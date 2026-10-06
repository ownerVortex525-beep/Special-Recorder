package com.ownervortex.nyxrecorder.core.recording

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioPlaybackCaptureConfiguration
import android.media.AudioRecord
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.net.Uri
import android.os.Build
import androidx.core.content.ContextCompat
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.LinkedBlockingQueue

/**
 * Encodes one AAC audio track for the recording. Sources (microphone, internal
 * device audio and an optional background music track) are read on their own
 * threads into bounded queues and summed by a mixer thread, so memory stays
 * constant no matter how long the recording runs.
 *
 * While paused, samples keep flowing into the encoder (so the raw clock stays
 * continuous) but are discarded on output and the presentation times are
 * re-based on resume — the paused stretch is excised from the final file.
 */
class AudioMixerEncoder(
    private val context: Context,
    private val recordMic: Boolean,
    private val recordDeviceAudio: Boolean,
    private val musicUri: String?,
    private val musicVolume: Float,
    private val projection: MediaProjection?,
    private val lock: Any,
    private val getMuxer: () -> MediaMuxer?,
    private val muxerStarted: () -> Boolean,
    private val onTrackReady: (Int) -> Unit
) {
    private class Source(
        val queue: LinkedBlockingQueue<ByteArray>,
        val gain: Float,
        val pacing: Boolean
    )

    private var encoder: MediaCodec? = null
    private val sources = mutableListOf<Source>()
    private var micRecord: AudioRecord? = null
    private var deviceRecord: AudioRecord? = null

    private var audioTrack = -1
    private var pending = ArrayList<PendingSample>()

    @Volatile private var running = false
    @Volatile private var paused = false
    private var framesFed = 0L
    private var shiftUs = 0L
    private var lastWrittenRaw = Long.MIN_VALUE
    private var discarding = false

    val isPaused: Boolean
        get() = paused

    /** Creates the encoder and probes every requested source. Returns false if no audio is possible. */
    fun prepare(): Boolean {
        return try {
            val format = MediaFormat.createAudioFormat(
                MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, CHANNELS
            ).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 128_000)
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, FRAME_BYTES * 4)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }

            if (recordMic && hasMicPermission()) {
                val queue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)
                val record = createMicRecord()
                if (record != null) {
                    micRecord = record
                    sources.add(Source(queue, 1.0f, pacing = true))
                    startThread { micLoop(record, queue) }
                }
            }

            if (recordDeviceAudio && Build.VERSION.SDK_INT >= 29 && projection != null) {
                val queue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)
                val record = createPlaybackRecord(projection)
                if (record != null) {
                    deviceRecord = record
                    sources.add(Source(queue, 1.0f, pacing = true))
                    startThread { deviceLoop(record, queue) }
                }
            }

            val music = musicUri
            if (music != null) {
                val queue = LinkedBlockingQueue<ByteArray>(QUEUE_CAPACITY)
                sources.add(Source(queue, musicVolume.coerceIn(0f, 1.5f), pacing = false))
                startThread { musicLoop(music, queue) }
            }

            sources.isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    fun start() {
        if (encoder == null || sources.isEmpty()) return
        running = true
        startThread { mixerLoop() }
    }

    fun setPaused(value: Boolean) {
        paused = value
    }

    /** Stops threads, flushes the encoder and releases everything. Synchronous. */
    fun stop() {
        running = false
        val workers = synchronized(workerLock) { workerThreads.toList() }
        for (t in workers) t.interrupt()
        // Give reader threads a moment to exit their loops.
        val deadline = System.currentTimeMillis() + 800
        while (threadsAlive() && System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                break
            }
        }
        finishEncoder()
        releaseSources()
    }

    private var activeThreads = 0
    private val workerLock = Any()
    private val workerThreads = mutableListOf<Thread>()

    @Synchronized
    private fun threadStarted() {
        activeThreads++
    }

    @Synchronized
    private fun threadFinished() {
        activeThreads--
    }

    private fun threadsAlive(): Boolean = synchronized(this) { activeThreads > 0 }

    private fun startThread(body: () -> Unit) {
        threadStarted()
        val thread = Thread {
            try {
                body()
            } catch (_: Exception) {
            } finally {
                threadFinished()
            }
        }.apply {
            name = "nyx-audio"
            priority = Thread.NORM_PRIORITY - 1
            isDaemon = true
        }
        synchronized(workerLock) { workerThreads.add(thread) }
        thread.start()
    }

    // ---------------------------------------------------------------- sources

    /**
     * Source threads are spawned during [prepare] but [start] (which flips
     * [running]) happens a moment later — wait for it so sources are not
     * torn down before recording actually begins.
     */
    private fun waitUntilRunning(): Boolean {
        val deadline = System.currentTimeMillis() + 5_000
        while (!running) {
            if (System.currentTimeMillis() >= deadline) return false
            try {
                Thread.sleep(20)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return false
            }
        }
        return true
    }

    private fun hasMicPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun createMicRecord(): AudioRecord? {
        return try {
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK_IN, ENCODING)
            val record = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, CHANNEL_MASK_IN, ENCODING,
                maxOf(minBuf, FRAME_BYTES * 4)
            )
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record.startRecording()
                record
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun createPlaybackRecord(projection: MediaProjection): AudioRecord? {
        return try {
            val config = AudioPlaybackCaptureConfiguration.Builder(projection)
                .addMatchingUsage(AudioAttributes.USAGE_MEDIA)
                .addMatchingUsage(AudioAttributes.USAGE_GAME)
                .addMatchingUsage(AudioAttributes.USAGE_UNKNOWN)
                .build()
            val format = AudioFormat.Builder()
                .setEncoding(ENCODING)
                .setSampleRate(SAMPLE_RATE)
                .setChannelMask(CHANNEL_MASK_IN)
                .build()
            val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_MASK_IN, ENCODING)
            val record = AudioRecord.Builder()
                .setAudioPlaybackCaptureConfig(config)
                .setAudioFormat(format)
                .setBufferSizeInBytes(maxOf(minBuf, FRAME_BYTES * 4))
                .build()
            if (record.state != AudioRecord.STATE_INITIALIZED) {
                record.release()
                null
            } else {
                record.startRecording()
                record
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun micLoop(record: AudioRecord, queue: LinkedBlockingQueue<ByteArray>) {
        readLoop(record, queue)
    }

    private fun deviceLoop(record: AudioRecord, queue: LinkedBlockingQueue<ByteArray>) {
        readLoop(record, queue)
    }

    private fun readLoop(record: AudioRecord, queue: LinkedBlockingQueue<ByteArray>) {
        try {
            if (!waitUntilRunning()) return
            while (running) {
                val frame = ByteArray(FRAME_BYTES)
                var read = 0
                while (read < FRAME_BYTES && running) {
                    val n = record.read(frame, read, FRAME_BYTES - read)
                    if (n < 0) return
                    read += n
                }
                if (read == FRAME_BYTES) queue.put(frame)
            }
        } catch (_: Exception) {
        } finally {
            safeRelease(record)
        }
    }

    private fun safeRelease(record: AudioRecord) {
        try {
            if (record.state != AudioRecord.STATE_UNINITIALIZED) record.stop()
        } catch (_: Exception) {
        }
        try {
            if (record.state != AudioRecord.STATE_UNINITIALIZED) record.release()
        } catch (_: Exception) {
        }
    }

    private fun musicLoop(uriStr: String, queue: LinkedBlockingQueue<ByteArray>) {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        try {
            if (!waitUntilRunning()) return
            extractor = MediaExtractor()
            extractor.setDataSource(context, Uri.parse(uriStr), null)
            var trackIndex = -1
            var mime: String? = null
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val m = format.getString(MediaFormat.KEY_MIME)
                if (m != null && m.startsWith("audio/")) {
                    trackIndex = i
                    mime = m
                    break
                }
            }
            if (trackIndex < 0 || mime == null) return
            extractor.selectTrack(trackIndex)
            val trackFormat = extractor.getTrackFormat(trackIndex)
            val srcRate = trackFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val srcChannels = trackFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(trackFormat, null, null, 0)
                start()
            }

            val info = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false
            var acc = ByteArray(FRAME_BYTES * 8)
            var accLen = 0

            while (!outputDone && running) {
                if (!inputDone) {
                    val inIdx = decoder.dequeueInputBuffer(20_000)
                    if (inIdx >= 0) {
                        val inBuf = decoder.getInputBuffer(inIdx)
                        val size = if (inBuf != null) extractor.readSampleData(inBuf, 0) else -1
                        if (size < 0) {
                            decoder.queueInputBuffer(
                                inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputDone = true
                        } else {
                            decoder.queueInputBuffer(inIdx, 0, size, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = decoder.dequeueOutputBuffer(info, 20_000)
                if (outIdx >= 0) {
                    try {
                        val outBuf = decoder.getOutputBuffer(outIdx)
                        if (outBuf != null && info.size > 0) {
                            outBuf.position(info.offset)
                            outBuf.limit(info.offset + info.size)
                            val pcm = ByteArray(info.size)
                            outBuf.get(pcm)
                            val resampled = resample(pcm, srcRate, srcChannels)
                            if (accLen + resampled.size > acc.size) {
                                acc = acc.copyOf(maxOf(acc.size * 2, accLen + resampled.size))
                            }
                            System.arraycopy(resampled, 0, acc, accLen, resampled.size)
                            accLen += resampled.size
                            while (accLen >= FRAME_BYTES) {
                                queue.put(acc.copyOfRange(0, FRAME_BYTES))
                                System.arraycopy(acc, FRAME_BYTES, acc, 0, accLen - FRAME_BYTES)
                                accLen -= FRAME_BYTES
                            }
                        }
                    } catch (_: InterruptedException) {
                        Thread.currentThread().interrupt()
                        return
                    }
                    decoder.releaseOutputBuffer(outIdx, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                } else if (inputDone && outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    outputDone = true
                }
            }
        } catch (_: Exception) {
        } finally {
            try {
                decoder?.stop()
                decoder?.release()
            } catch (_: Exception) {
            }
            try {
                extractor?.release()
            } catch (_: Exception) {
            }
        }
    }

    /** Nearest-neighbour conversion to 44100 Hz stereo 16-bit. */
    private fun resample(pcm: ByteArray, srcRate: Int, srcChannels: Int): ByteArray {
        if (srcRate == SAMPLE_RATE && srcChannels == CHANNELS) return pcm
        if (pcm.size < 2 * srcChannels) return ByteArray(0)
        val samples = pcm.size / 2 / srcChannels
        if (samples <= 0) return ByteArray(0)
        val ratio = srcRate.toDouble() / SAMPLE_RATE
        val outSamples = (samples / ratio).toInt().coerceAtLeast(1)
        val out = ByteArray(outSamples * 2 * CHANNELS)
        for (i in 0 until outSamples) {
            val srcFrame = (i * ratio).toInt().coerceAtMost(samples - 1)
            var l: Int
            var r: Int
            if (srcChannels == 1) {
                l = (pcm[srcFrame * 2].toInt() and 0xFF) or (pcm[srcFrame * 2 + 1].toInt() shl 8)
                r = l
            } else {
                l = (pcm[srcFrame * srcChannels * 2].toInt() and 0xFF) or
                    (pcm[srcFrame * srcChannels * 2 + 1].toInt() shl 8)
                r = (pcm[srcFrame * srcChannels * 2 + 2].toInt() and 0xFF) or
                    (pcm[srcFrame * srcChannels * 2 + 3].toInt() shl 8)
            }
            val ls = l.toShort().toInt()
            val rs = r.toShort().toInt()
            out[i * 4] = (ls and 0xFF).toByte()
            out[i * 4 + 1] = ((ls shr 8) and 0xFF).toByte()
            out[i * 4 + 2] = (rs and 0xFF).toByte()
            out[i * 4 + 3] = ((rs shr 8) and 0xFF).toByte()
        }
        return out
    }

    // ----------------------------------------------------------------- mixer

    private fun mixerLoop() {
        val pacingSource = sources.firstOrNull { it.pacing }
        while (running) {
            val frame = ByteArray(FRAME_BYTES)
            var paced = false
            for (source in sources) {
                val data = if (source === pacingSource && !paced) {
                    paced = true
                    source.queue.poll(PACE_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS)
                } else {
                    source.queue.poll()
                }
                if (data != null) mixInto(frame, data, source.gain)
            }
            if (pacingSource == null) {
                // Music-only: pace the loop on wall clock.
                try {
                    Thread.sleep(FRAME_MS.toLong())
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    break
                }
            }
            if (!running) break
            feedEncoder(frame)
            drainEncoder()
        }
        finishEncoder()
    }

    private fun mixInto(dst: ByteArray, src: ByteArray, gain: Float) {
        val n = if (src.size < dst.size) src.size else dst.size
        var i = 0
        while (i + 1 < n) {
            var s = ((src[i].toInt() and 0xFF) or (src[i + 1].toInt() shl 8)).toShort().toInt()
            s = (s * gain).toInt()
            val d = ((dst[i].toInt() and 0xFF) or (dst[i + 1].toInt() shl 8)).toShort().toInt()
            var sum = d + s
            if (sum > 32767) sum = 32767 else if (sum < -32768) sum = -32768
            dst[i] = (sum and 0xFF).toByte()
            dst[i + 1] = ((sum shr 8) and 0xFF).toByte()
            i += 2
        }
    }

    private fun feedEncoder(frame: ByteArray) {
        val codec = encoder ?: return
        val inputIndex = codec.dequeueInputBuffer(5_000)
        if (inputIndex >= 0) {
            val buf = codec.getInputBuffer(inputIndex)
            if (buf != null) {
                buf.clear()
                buf.put(frame)
                val ptsUs = framesFed * 1_000_000L / SAMPLE_RATE
                codec.queueInputBuffer(inputIndex, 0, frame.size, ptsUs, 0)
            }
        }
        framesFed++
    }

    private fun drainEncoder() {
        val codec = encoder ?: return
        val info = MediaCodec.BufferInfo()
        while (true) {
            val outIdx = codec.dequeueOutputBuffer(info, 0)
            when {
                outIdx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    synchronized(lock) {
                        val muxer = getMuxer()
                        if (muxer != null && audioTrack < 0 && !muxerStarted()) {
                            try {
                                audioTrack = muxer.addTrack(codec.outputFormat)
                                onTrackReady(audioTrack)
                            } catch (_: Exception) {
                                audioTrack = -1
                            }
                        }
                    }
                }
                outIdx == MediaCodec.INFO_TRY_AGAIN_LATER -> break
                outIdx >= 0 -> {
                    val buf = codec.getOutputBuffer(outIdx)
                    if (buf != null && info.size > 0) {
                        handleSample(buf, info)
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                }
                else -> break
            }
        }
    }

    private fun handleSample(buf: ByteBuffer, info: MediaCodec.BufferInfo) {
        val rawPts = info.presentationTimeUs
        synchronized(lock) {
            val started = muxerStarted() && audioTrack >= 0
            if (!started) {
                val data = ByteArray(info.size)
                buf.position(info.offset)
                buf.limit(info.offset + info.size)
                buf.get(data)
                pending.add(PendingSample(data, 0, info.size, rawPts, info.flags))
                if (pending.size > 200) pending.removeAt(0)
                return
            }
            flushPendingLocked()
            if (paused) {
                discarding = true
                return
            }
            if (discarding) {
                discarding = false
                if (lastWrittenRaw != Long.MIN_VALUE) {
                    shiftUs += rawPts - lastWrittenRaw - 1_000
                }
            }
            val writePts = rawPts - shiftUs
            info.presentationTimeUs = writePts
            getMuxer()?.writeSampleData(audioTrack, buf, info)
            lastWrittenRaw = rawPts
        }
    }

    private fun flushPendingLocked() {
        if (pending.isEmpty()) return
        val muxer = getMuxer() ?: return
        for (sample in pending) {
            try {
                val info = MediaCodec.BufferInfo().apply {
                    set(sample.offset, sample.size, sample.ptsUs - shiftUs, sample.flags)
                }
                val buf = ByteBuffer.wrap(sample.data, sample.offset, sample.size)
                muxer.writeSampleData(audioTrack, buf, info)
                lastWrittenRaw = sample.ptsUs
            } catch (_: Exception) {
            }
        }
        pending.clear()
    }

    private val finishLock = Any()

    private fun finishEncoder() {
        val codec = synchronized(finishLock) {
            val c = encoder ?: return
            encoder = null
            c
        }
        try {
            val idx = codec.dequeueInputBuffer(100_000)
            if (idx >= 0) {
                codec.queueInputBuffer(
                    idx, 0, 0, framesFed * 1_000_000L / SAMPLE_RATE,
                    MediaCodec.BUFFER_FLAG_END_OF_STREAM
                )
            }
        } catch (_: Exception) {
        }
        // Drain whatever is left so the track ends cleanly.
        val info = MediaCodec.BufferInfo()
        val deadline = System.currentTimeMillis() + 400
        while (System.currentTimeMillis() < deadline) {
            val outIdx = codec.dequeueOutputBuffer(info, 20_000)
            if (outIdx >= 0) {
                val buf = codec.getOutputBuffer(outIdx)
                if (buf != null && info.size > 0 && !paused) {
                    synchronized(lock) {
                        if (muxerStarted() && audioTrack >= 0) {
                            try {
                                flushPendingLocked()
                                info.presentationTimeUs = info.presentationTimeUs - shiftUs
                                getMuxer()?.writeSampleData(audioTrack, buf, info)
                            } catch (_: Exception) {
                            }
                        }
                    }
                }
                codec.releaseOutputBuffer(outIdx, false)
                if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) break
            } else if (outIdx == MediaCodec.INFO_TRY_AGAIN_LATER) {
                break
            }
        }
        try {
            codec.stop()
        } catch (_: Exception) {
        }
        codec.release()
        synchronized(lock) {
            pending.clear()
        }
    }

    private fun releaseSources() {
        micRecord?.let { safeRelease(it) }
        micRecord = null
        deviceRecord?.let { safeRelease(it) }
        deviceRecord = null
        sources.clear()
        synchronized(workerLock) { workerThreads.clear() }
    }

    companion object {
        const val SAMPLE_RATE = 44100
        const val CHANNELS = 2
        const val FRAME_MS = 20
        const val FRAME_BYTES = SAMPLE_RATE * FRAME_MS / 1000 * 2 * CHANNELS
        private const val QUEUE_CAPACITY = 32
        private const val PACE_TIMEOUT_MS = 40L
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        private const val CHANNEL_MASK_IN = AudioFormat.CHANNEL_IN_STEREO
    }
}
