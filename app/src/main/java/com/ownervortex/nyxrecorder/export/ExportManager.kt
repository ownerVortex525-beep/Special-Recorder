package com.ownervortex.nyxrecorder.export

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.audio.SpeedChangingAudioProcessor
import androidx.media3.common.audio.SpeedProvider
import androidx.media3.effect.Brightness
import androidx.media3.effect.Contrast
import androidx.media3.effect.Crop
import androidx.media3.effect.HslAdjustment
import androidx.media3.effect.Presentation
import androidx.media3.effect.RgbFilter
import androidx.media3.effect.ScaleAndRotateTransformation
import androidx.media3.effect.TextOverlay
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.ownervortex.nyxrecorder.core.util.Constants
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

enum class EditFilter(val label: String) {
    NONE("None"),
    GRAYSCALE("B&W"),
    INVERT("Invert"),
    WARM("Warm"),
    COOL("Cool"),
    VIVID("Vivid"),
    DARK("Dark")
}

data class ExportRequest(
    val inputUri: String,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val speed: Float = 1f,
    val filter: EditFilter = EditFilter.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val cropLeft: Float = 0f,
    val cropTop: Float = 0f,
    val cropRight: Float = 0f,
    val cropBottom: Float = 0f,
    val output720: Boolean = false,
    val watermark: String? = null,
    val watermarkCorner: Int = 3,
    val portrait: Boolean = false
) {
    val hasCrop: Boolean
        get() = cropLeft > 0f || cropTop > 0f || cropRight > 0f || cropBottom > 0f
}

/**
 * Builds the video effect chain. Shared between the export pipeline and the
 * editor's live preview — [includeSpeed] is false for preview because
 * SpeedChangeEffect is not supported by ExoPlayer.setVideoEffects (the preview
 * applies speed through playbackParameters instead).
 */
fun buildVideoEffects(request: ExportRequest, includeSpeed: Boolean): List<Effect> {
    val effects = ArrayList<Effect>()

    if (request.hasCrop) {
        // Crop takes NDC coordinates: -1..1 with origin at center.
        effects += Crop(
            -1f + 2f * request.cropLeft,
            -1f + 2f * request.cropTop,
            1f - 2f * request.cropRight,
            1f - 2f * request.cropBottom
        )
    }

    if (request.output720) {
        // Portrait sources get a portrait 720p output, not a landscape one.
        effects += Presentation.createForWidthAndHeight(
            if (request.portrait) 720 else 1280,
            if (request.portrait) 1280 else 720,
            Presentation.LAYOUT_SCALE_TO_FIT
        )
    }

    when (request.filter) {
        EditFilter.GRAYSCALE -> effects += RgbFilter.createGrayscaleFilter()
        EditFilter.INVERT -> effects += RgbFilter.createInvertedFilter()
        EditFilter.WARM -> effects += HslAdjustment.Builder()
            .adjustHue(12f).adjustSaturation(0.15f).build()
        EditFilter.COOL -> effects += HslAdjustment.Builder()
            .adjustHue(-14f).adjustSaturation(0.1f).build()
        EditFilter.VIVID -> effects += HslAdjustment.Builder()
            .adjustSaturation(0.35f).build()
        EditFilter.DARK -> effects += HslAdjustment.Builder()
            .adjustLightness(-0.2f).build()
        EditFilter.NONE -> Unit
    }

    if (request.brightness != 0f) effects += Brightness(request.brightness)
    if (request.contrast != 1f) effects += Contrast(request.contrast)

    if (includeSpeed && request.speed != 1f) {
        effects += androidx.media3.effect.SpeedChangeEffect(request.speed)
    }

    val watermark = request.watermark
    if (!watermark.isNullOrBlank()) {
        try {
            val (anchorX, anchorY) = when (request.watermarkCorner) {
                0 -> -0.75f to 0.75f   // top-left
                1 -> 0.75f to 0.75f    // top-right
                2 -> -0.75f to -0.75f  // bottom-left
                else -> 0.75f to -0.75f // bottom-right
            }
            val settings = androidx.media3.effect.OverlaySettings.Builder()
                .setBackgroundFrameAnchor(anchorX, anchorY)
                .setScale(1f, 1f)
                .build()
            val textOverlay = TextOverlay.createStaticTextOverlay(
                android.text.SpannableString(watermark),
                settings
            )
            effects += androidx.media3.effect.OverlayEffect(listOf(textOverlay))
        } catch (_: Exception) {
        }
    }

    // Always ensure even output dimensions.
    effects += ScaleAndRotateTransformation.Builder()
        .setScale(1f, 1f)
        .setRotationDegrees(0f)
        .build()

    return effects
}

class ExportManager(private val context: Context) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val cancelled = AtomicBoolean(false)

    private var transformer: Transformer? = null
    private var progressJob: Runnable? = null
    private var tempFile: File? = null
    private var finished = false

    fun start(
        request: ExportRequest,
        onProgress: (Int) -> Unit,
        onDone: (Uri) -> Unit,
        onExportError: (String) -> Unit
    ) {
        cancelled.set(false)
        finished = false

        val temp = File(context.cacheDir, "nyx_export_${System.currentTimeMillis()}.mp4")
        tempFile = temp

        val mediaItem = MediaItem.Builder()
            .setUri(request.inputUri)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(request.trimStartMs)
                    .apply {
                        if (request.trimEndMs > request.trimStartMs) {
                            setEndPositionMs(request.trimEndMs)
                        }
                    }
                    .build()
            )
            .build()

        val videoEffects = buildEffects(request)
        val audioProcessors = buildAudioProcessors(request)

        val listener = object : Transformer.Listener {
            override fun onCompleted(composition: androidx.media3.transformer.Composition, result: ExportResult) {
                if (finished) return
                finished = true
                stopProgress()
                if (cancelled.get()) {
                    temp.delete()
                    return
                }
                onProgress(100)
                Thread {
                    publish(
                        temp,
                        onDone = { uri -> mainHandler.post { onDone(uri) } },
                        onError = { message -> mainHandler.post { onExportError(message) } }
                    )
                }.start()
            }

            override fun onError(
                composition: androidx.media3.transformer.Composition,
                result: ExportResult,
                exception: ExportException
            ) {
                if (finished) return
                finished = true
                stopProgress()
                temp.delete()
                if (!cancelled.get()) {
                    val reason = exception.errorCodeName
                    mainHandler.post { onExportError(reason ?: "Export failed") }
                }
            }
        }

        try {
            val t = Transformer.Builder(context)
                .setVideoMimeType("video/avc")
                .setAudioMimeType("audio/mp4a-latm")
                .addListener(listener)
                .build()
            transformer = t

            val edited = EditedMediaItem.Builder(mediaItem)
                .setEffects(Effects(audioProcessors, videoEffects))
                .build()

            t.start(edited, temp.absolutePath)
            startProgress(onProgress)
        } catch (t: Throwable) {
            finished = true
            stopProgress()
            temp.delete()
            onExportError(t.message ?: "Could not start export")
        }
    }

    fun cancel() {
        cancelled.set(true)
        stopProgress()
        try {
            transformer?.cancel()
        } catch (_: Exception) {
        }
        tempFile?.delete()
        tempFile = null
    }

    private fun buildEffects(request: ExportRequest): List<Effect> =
        buildVideoEffects(request, includeSpeed = true)

    private fun buildAudioProcessors(request: ExportRequest): List<androidx.media3.common.audio.AudioProcessor> {
        if (request.speed == 1f) return emptyList()
        val speed = request.speed
        val provider = object : SpeedProvider {
            override fun getSpeed(positionUs: Long): Float = speed
            override fun getNextSpeedChangeTimeUs(positionUs: Long): Long =
                androidx.media3.common.C.TIME_UNSET
        }
        return listOf(SpeedChangingAudioProcessor(provider))
    }

    private fun startProgress(onProgress: (Int) -> Unit) {
        val holder = ProgressHolder()
        val runnable = object : Runnable {
            override fun run() {
                if (finished) return
                try {
                    val state = transformer?.getProgress(holder)
                        ?: androidx.media3.transformer.Transformer.PROGRESS_STATE_NO_TRANSFORMATION
                    when (state) {
                        androidx.media3.transformer.Transformer.PROGRESS_STATE_AVAILABLE -> {
                            onProgress(holder.progress.coerceIn(0, 99))
                        }
                        androidx.media3.transformer.Transformer.PROGRESS_STATE_NO_TRANSFORMATION -> {
                            onProgress(100)
                        }
                    }
                } catch (_: Exception) {
                }
                mainHandler.postDelayed(this, 350)
            }
        }
        progressJob = runnable
        mainHandler.post(runnable)
    }

    private fun stopProgress() {
        progressJob?.let { mainHandler.removeCallbacks(it) }
        progressJob = null
    }

    private fun publish(temp: File, onDone: (Uri) -> Unit, onError: (String) -> Unit) {
        try {
            val sp = com.ownervortex.nyxrecorder.core.util.StoragePaths
            val name = sp.exportFile().name
            if (sp.hasAllFilesAccess(context) || Build.VERSION.SDK_INT < 29) {
                val dest = sp.exportFile()
                temp.copyTo(dest, overwrite = true)
                temp.delete()
                MediaScannerConnection.scanFile(
                    context, arrayOf(dest.absolutePath), arrayOf("video/mp4"), null
                )
                onDone(Uri.fromFile(dest))
            } else {
                val values = ContentValues().apply {
                    put(MediaStore.Video.Media.DISPLAY_NAME, name)
                    put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                    put(MediaStore.Video.Media.RELATIVE_PATH, sp.relativePath())
                    put(MediaStore.Video.Media.IS_PENDING, 1)
                }
                val uri = context.contentResolver.insert(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values
                ) ?: run { onError("Could not create output"); temp.delete(); return }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    temp.inputStream().use { it.copyTo(out) }
                } ?: run { onError("Could not write output"); temp.delete(); return }
                val done = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
                context.contentResolver.update(uri, done, null, null)
                temp.delete()
                onDone(uri)
            }
        } catch (t: Throwable) {
            temp.delete()
            onError(t.message ?: "Could not save export")
        }
    }
}
