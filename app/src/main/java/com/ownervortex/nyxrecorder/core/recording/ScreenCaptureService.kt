package com.ownervortex.nyxrecorder.core.recording

import android.app.Activity
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.ownervortex.nyxrecorder.MainActivity
import com.ownervortex.nyxrecorder.R
import com.ownervortex.nyxrecorder.core.overlay.BubbleController
import com.ownervortex.nyxrecorder.core.util.Constants
import com.ownervortex.nyxrecorder.core.util.DeviceHealth
import com.ownervortex.nyxrecorder.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.CountDownLatch

class ScreenCaptureService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Default + Job())
    private val mainHandler = Handler(Looper.getMainLooper())
    private val muxerLock = Any()

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var videoEncoder: MediaCodec? = null
    private var muxer: MediaMuxer? = null

    private var outputUri: Uri? = null
    private var outputFile: File? = null
    private var outputDescriptor: ParcelFileDescriptor? = null
    private var pendingFilePath: String? = null

    private var videoTrack = -1
    private var audioTrackIndex = -1
    @Volatile private var muxerStarted = false
    @Volatile private var recording = false
    @Volatile private var paused = false
    @Volatile private var stopping = false
    @Volatile private var cleanedUp = false

    private var audioEncoder: AudioMixerEncoder? = null
    private var touchIndicatorApplied = false
    private var bubble: BubbleController? = null
    private var config: RecordingConfig? = null
    private var fileName: String = ""

    private var elapsedMs = 0L
    private var lastTickMs = 0L
    private var eosLatch: CountDownLatch? = null

    private var videoShiftUs = 0L
    private var videoLastWrittenRaw = Long.MIN_VALUE
    private var videoDiscarding = false
    private val pendingVideo = ArrayList<PendingSample>()

    private var fallbackPosted = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            Constants.ACTION_START -> {
                @Suppress("DEPRECATION")
                val data = intent.getParcelableExtra<Intent>(EXTRA_PROJECTION_DATA)
                @Suppress("DEPRECATION")
                val cfg = intent.getParcelableExtra<RecordingConfig>(EXTRA_CONFIG)
                if (data != null && cfg != null && !recording) {
                    startRecording(data, cfg)
                } else if (data == null || cfg == null) {
                    stopSelf()
                }
            }
            Constants.ACTION_PAUSE -> doPause()
            Constants.ACTION_RESUME -> doResume()
            Constants.ACTION_STOP -> serviceScope.launch { doStop() }
            Constants.ACTION_MUSIC_TOGGLE -> {
                audioEncoder?.toggleMusicPause()
                pushNotification()
            }
            Constants.ACTION_MUSIC_STOP -> {
                audioEncoder?.stopMusic()
                pushNotification()
            }
            Constants.ACTION_MUSIC_FORWARD -> {
                audioEncoder?.forwardMusic()
            }
        }
        return START_NOT_STICKY
    }

    // ------------------------------------------------------------------ start

    private fun applyTouchIndicator() {
        if (!SettingsStore.touchIndicator) return
        if (Build.VERSION.SDK_INT >= 23 && !Settings.canWrite(this)) return
        touchIndicatorApplied = try {
            Settings.System.putInt(contentResolver, Settings.System.SHOW_TOUCHES, 1)
        } catch (_: Exception) {
            false
        }
    }

    private fun clearTouchIndicator() {
        if (!touchIndicatorApplied) return
        touchIndicatorApplied = false
        try {
            Settings.System.putInt(contentResolver, Settings.System.SHOW_TOUCHES, 0)
        } catch (_: Exception) {
        }
    }

    private fun startRecording(projectionData: Intent, cfg: RecordingConfig) {
        recording = true
        stopping = false
        cleanedUp = false
        paused = false
        elapsedMs = 0L
        lastTickMs = System.currentTimeMillis()
        videoTrack = -1
        audioTrackIndex = -1
        muxerStarted = false
        videoShiftUs = 0L
        videoLastWrittenRaw = Long.MIN_VALUE
        videoDiscarding = false
        pendingVideo.clear()
        config = cfg
        applyTouchIndicator()

        fileName = "NYX_" +
            SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date()) + ".mp4"
        RecordingStatus.onStart(fileName, cfg.qualityLabel)

        startForegroundCompat(cfg)

        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val proj = manager.getMediaProjection(Activity.RESULT_OK, projectionData)
        if (proj == null) {
            failStart()
            return
        }
        projection = proj
        proj.registerCallback(object : MediaProjection.Callback() {
            override fun onStop() {
                serviceScope.launch { doStop() }
            }
        }, null)

        if (!createOutputAndEncoder(cfg)) {
            failStart()
            return
        }

        if (cfg.audioNeeded) {
            val audio = AudioMixerEncoder(
                context = this,
                recordMic = cfg.recordMic,
                recordDeviceAudio = cfg.recordDeviceAudio,
                musicUri = cfg.musicUri,
                musicVolume = cfg.musicVolume,
                projection = proj,
                lock = muxerLock,
                getMuxer = { muxer },
                muxerStarted = { muxerStarted },
                onTrackReady = { index ->
                    audioTrackIndex = index
                    maybeStartMuxer()
                }
            )
            if (audio.prepare()) {
                audioEncoder = audio
            } else {
                audio.setPaused(false)
            }
        }

        val surface = videoEncoder?.createInputSurface()
        if (surface == null) {
            failStart()
            return
        }
        videoEncoder?.start()

        virtualDisplay = proj.createVirtualDisplay(
            "NYX-Recorder",
            cfg.width, cfg.height, cfg.densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            surface, null, null
        )
        if (virtualDisplay == null) {
            failStart()
            return
        }

        audioEncoder?.setPaused(false)
        audioEncoder?.start()
        startVideoDrain()
        startTicker()
        scheduleMuxerFallback()

        if (cfg.showBubble) {
            try {
                val controller = BubbleController(
                    this,
                    cfg.faceCam,
                    cfg.musicUri != null,
                    onMusicToggle = {
                        audioEncoder?.toggleMusicPause()
                        bubble?.setMusicPaused(audioEncoder?.isMusicPaused() ?: false)
                        pushNotification()
                    },
                    onMusicStop = {
                        audioEncoder?.stopMusic()
                        bubble?.hideMusicControls()
                        pushNotification()
                    },
                    onMusicForward = { audioEncoder?.forwardMusic() }
                )
                val ok = controller.show(
                    onPauseToggle = {
                        if (paused) RecordingController.resume(this)
                        else RecordingController.pause(this)
                    },
                    onStop = { RecordingController.stop(this) },
                    onHide = { hideBubble() }
                )
                if (ok) {
                    bubble = controller
                } else {
                    notifyBubbleBlocked()
                }
            } catch (_: Exception) {
            }
        }
    }

    /** Overlay permission missing: tell the user instead of failing silently. */
    private fun notifyBubbleBlocked() {
        try {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            val pending = PendingIntent.getActivity(
                this, 3, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val notification = NotificationCompat.Builder(this, Constants.RECORDING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_record)
                .setContentTitle("Floating bubble blocked")
                .setContentText("Tap to allow “Display over apps” so controls can appear")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setColor(0xFF6C63FF.toInt())
                .build()
            getSystemService(NotificationManager::class.java)?.notify(4201, notification)
        } catch (_: Exception) {
        }
    }

    private fun failStart() {
        // Roll back a partially started session; keeps everything idempotent.
        serviceScope.launch { doStop() }
    }

    private fun createOutputAndEncoder(cfg: RecordingConfig): Boolean {
        return try {
            if (com.ownervortex.nyxrecorder.core.util.StoragePaths.hasAllFilesAccess(this)) {
                val file = com.ownervortex.nyxrecorder.core.util.StoragePaths.newCaptureFile()
                outputFile = file
                pendingFilePath = file.absolutePath
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            } else if (Build.VERSION.SDK_INT >= 29) {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(
                        MediaStore.Video.Media.RELATIVE_PATH,
                        com.ownervortex.nyxrecorder.core.util.StoragePaths.relativePath()
                    )
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                outputUri = contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: return false
                outputDescriptor = contentResolver.openFileDescriptor(outputUri!!, "rw")
                    ?: return false
                muxer = MediaMuxer(
                    outputDescriptor!!.fileDescriptor,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )
            } else {
                val file = com.ownervortex.nyxrecorder.core.util.StoragePaths.newCaptureFile()
                outputFile = file
                pendingFilePath = file.absolutePath
                muxer = MediaMuxer(file.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            }

            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, cfg.width, cfg.height).apply {
                setInteger(
                    MediaFormat.KEY_COLOR_FORMAT,
                    MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface
                )
                setInteger(MediaFormat.KEY_BIT_RATE, cfg.bitRate)
                setInteger(MediaFormat.KEY_FRAME_RATE, cfg.frameRate)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                if (Build.VERSION.SDK_INT >= 29) {
                    setInteger(
                        MediaFormat.KEY_PROFILE,
                        MediaCodecInfo.CodecProfileLevel.AVCProfileMain
                    )
                }
            }
            videoEncoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            }
            true
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------ video drain

    private fun startVideoDrain() {
        serviceScope.launch {
            val info = MediaCodec.BufferInfo()
            try {
                while (!cleanedUp) {
                    val codec = videoEncoder ?: break
                    val outIdx = codec.dequeueOutputBuffer(info, 10_000)
                    when {
                        outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                            synchronized(muxerLock) {
                                val m = muxer
                                if (m != null && videoTrack < 0) {
                                    try {
                                        videoTrack = m.addTrack(codec.outputFormat)
                                    } catch (_: Exception) {
                                    }
                                }
                            }
                            maybeStartMuxer()
                        }
                        outIdx >= 0 -> {
                            try {
                                val buf = codec.getOutputBuffer(outIdx)
                                if (buf != null && info.size > 0) {
                                    handleVideoSample(buf, info)
                                }
                            } catch (_: Exception) {
                            }
                            try {
                                codec.releaseOutputBuffer(outIdx, false)
                            } catch (_: Exception) {
                            }
                            if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                                eosLatch?.countDown()
                                break
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Codec was released underneath us while stopping — nothing left to drain.
                eosLatch?.countDown()
            }
        }
    }

    private fun handleVideoSample(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        val rawPts = info.presentationTimeUs
        synchronized(muxerLock) {
            if (!muxerStarted || videoTrack < 0) {
                val data = ByteArray(info.size)
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                buf.get(data)
                pendingVideo.add(PendingSample(data, 0, info.size, rawPts, info.flags))
                if (pendingVideo.size > 240) pendingVideo.removeAt(0)
                return
            }
            flushVideoPendingLocked()
            if (paused) {
                videoDiscarding = true
                return
            }
            if (videoDiscarding) {
                videoDiscarding = false
                if (videoLastWrittenRaw != Long.MIN_VALUE) {
                    videoShiftUs += rawPts - videoLastWrittenRaw - 1_000
                }
            }
            val writePts = rawPts - videoShiftUs
            info.presentationTimeUs = writePts
            muxer?.writeSampleData(videoTrack, buf, info)
            videoLastWrittenRaw = rawPts
        }
    }

    private fun flushVideoPendingLocked() {
        if (pendingVideo.isEmpty()) return
        val m = muxer ?: return
        for (sample in pendingVideo) {
            try {
                val info = MediaCodec.BufferInfo().apply {
                    set(sample.offset, sample.size, sample.ptsUs - videoShiftUs, sample.flags)
                }
                val buf = ByteBuffer.wrap(sample.data, sample.offset, sample.size)
                m.writeSampleData(videoTrack, buf, info)
                videoLastWrittenRaw = sample.ptsUs
            } catch (_: Exception) {
            }
        }
        pendingVideo.clear()
    }

    private fun maybeStartMuxer() {
        synchronized(muxerLock) {
            if (muxerStarted || muxer == null || videoTrack < 0) return
            val audioReady = audioTrackIndex >= 0 || audioEncoder == null
            if (audioReady) {
                startMuxerLocked()
            }
        }
    }

    private fun startMuxerLocked() {
        try {
            muxer?.start()
            muxerStarted = true
        } catch (_: Exception) {
            muxerStarted = false
        }
    }

    private fun scheduleMuxerFallback() {
        if (fallbackPosted) return
        fallbackPosted = true
        mainHandler.postDelayed({
            synchronized(muxerLock) {
                if (!muxerStarted && muxer != null && videoTrack >= 0) {
                    startMuxerLocked()
                }
            }
        }, 1500)
    }

    // ------------------------------------------------------------------ pause

    private fun doPause() {
        if (!recording || paused || stopping) return
        paused = true
        lastTickMs = System.currentTimeMillis()
        audioEncoder?.setPaused(true)
        RecordingStatus.onPaused(true)
        bubble?.setPaused(true)
        pushNotification()
    }

    private fun doResume() {
        if (!recording || !paused || stopping) return
        paused = false
        lastTickMs = System.currentTimeMillis()
        audioEncoder?.setPaused(false)
        RecordingStatus.onPaused(false)
        bubble?.setPaused(false)
        pushNotification()
    }

    // ------------------------------------------------------------------- stop

    @Synchronized
    private fun doStop() {
        if (stopping || cleanedUp) return
        stopping = true
        recording = false
        mainHandler.removeCallbacksAndMessages(null)

        // 1. Finalize audio first so its last samples land before the muxer stops.
        try {
            audioEncoder?.stop()
        } catch (_: Exception) {
        }
        audioEncoder = null

        // 2. Drain the remaining video (EOS) with a bounded wait.
        val codec = videoEncoder
        if (codec != null && !cleanedUp) {
            eosLatch = CountDownLatch(1)
            try {
                codec.signalEndOfInputStream()
            } catch (_: Exception) {
                eosLatch?.countDown()
            }
            try {
                eosLatch?.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
            }
        }

        finalizeEverything()
        clearTouchIndicator()
        mainHandler.post { hideBubble() }
        RecordingStatus.onStop()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun finalizeEverything() {
        if (cleanedUp) return
        cleanedUp = true

        try {
            virtualDisplay?.release()
        } catch (_: Exception) {
        }
        virtualDisplay = null

        try {
            projection?.stop()
        } catch (_: Exception) {
        }
        projection = null

        try {
            videoEncoder?.stop()
        } catch (_: Exception) {
        }
        try {
            videoEncoder?.release()
        } catch (_: Exception) {
        }
        videoEncoder = null

        synchronized(muxerLock) {
            try {
                if (muxerStarted) muxer?.stop()
            } catch (_: Exception) {
            }
            try {
                muxer?.release()
            } catch (_: Exception) {
            }
            muxer = null
            muxerStarted = false
            pendingVideo.clear()
        }

        try {
            outputDescriptor?.close()
        } catch (_: Exception) {
        }
        outputDescriptor = null

        val uri = outputUri
        if (uri != null) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(uri, values, null, null)
            } catch (_: Exception) {
            }
            outputUri = null
        }

        val path = pendingFilePath
        if (path != null) {
            pendingFilePath = null
            outputFile = null
            try {
                com.ownervortex.nyxrecorder.data.RecordingRepository(this).scanFile(path)
            } catch (_: Exception) {
            }
        }
    }

    private fun hideBubble() {
        try {
            bubble?.hide()
        } catch (_: Exception) {
        }
        bubble = null
    }

    // ----------------------------------------------------------- notifications

    private fun startTicker() {
        mainHandler.post { runTicker() }
    }

    private fun runTicker() {
        if (!recording || stopping || cleanedUp) return
        val now = System.currentTimeMillis()
        if (!paused) elapsedMs += now - lastTickMs
        lastTickMs = now
        RecordingStatus.onTick(elapsedMs, paused)
        bubble?.updateTime(DeviceHealth.formatDuration(elapsedMs))
        pushNotification()
        mainHandler.postDelayed({ runTicker() }, 1000)
    }

    private fun pushNotification() {
        try {
            getSystemService(NotificationManager::class.java)
                ?.notify(Constants.RECORDING_NOTIFICATION_ID, buildNotification())
        } catch (_: Exception) {
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 1,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val actionIntent = { action: String ->
            PendingIntent.getService(
                this, action.hashCode() and 0x7FFFFFFF,
                Intent(this, ScreenCaptureService::class.java).setAction(action),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
        val time = DeviceHealth.formatDuration(elapsedMs)
        val quality = config?.qualityLabel ?: ""
        val builder = NotificationCompat.Builder(this, Constants.RECORDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_record)
            .setContentTitle(
                if (paused) "Recording paused" else "Recording screen"
            )
            .setContentText(if (quality.isEmpty()) time else "$time • $quality")
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setColor(0xFF6C63FF.toInt())
        if (paused) {
            builder.addAction(
                R.drawable.ic_stat_play,
                "Resume",
                actionIntent(Constants.ACTION_RESUME)
            )
        } else {
            builder.addAction(
                R.drawable.ic_stat_pause,
                "Pause",
                actionIntent(Constants.ACTION_PAUSE)
            )
            builder.addAction(
                R.drawable.ic_stat_record,
                "Stop",
                actionIntent(Constants.ACTION_STOP)
            )
        }
        val musicUri = config?.musicUri
        if (musicUri != null && audioEncoder?.isMusicActive() == true) {
            val musicPaused = audioEncoder?.isMusicPaused() ?: false
            builder.addAction(
                if (musicPaused) R.drawable.ic_stat_play else R.drawable.ic_stat_pause,
                if (musicPaused) "Music play" else "Music pause",
                actionIntent(Constants.ACTION_MUSIC_TOGGLE)
            )
            builder.addAction(
                R.drawable.ic_stat_music_stop,
                "Music stop",
                actionIntent(Constants.ACTION_MUSIC_STOP)
            )
            builder.addAction(
                R.drawable.ic_stat_forward,
                "Music +10s",
                actionIntent(Constants.ACTION_MUSIC_FORWARD)
            )
        }
        return builder.build()
    }

    private fun startForegroundCompat(cfg: RecordingConfig) {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= 29) {
            var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
            val needsMic = cfg.recordMic || cfg.recordDeviceAudio
            val micGranted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= 30 && needsMic && micGranted) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            val camGranted = ContextCompat.checkSelfPermission(
                this, android.Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= 30 && cfg.faceCam && camGranted) {
                type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
            try {
                ServiceCompat.startForeground(
                    this, Constants.RECORDING_NOTIFICATION_ID, notification, type
                )
            } catch (_: Exception) {
                startForeground(Constants.RECORDING_NOTIFICATION_ID, notification)
            }
        } else {
            startForeground(Constants.RECORDING_NOTIFICATION_ID, notification)
        }
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        if (!cleanedUp) {
            stopping = true
            recording = false
            try {
                audioEncoder?.stop()
            } catch (_: Exception) {
            }
            audioEncoder = null
            finalizeEverything()
            RecordingStatus.onStop()
        }
        clearTouchIndicator()
        hideBubble()
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_PROJECTION_DATA = "projection_data"
        const val EXTRA_CONFIG = "recording_config"
    }
}
