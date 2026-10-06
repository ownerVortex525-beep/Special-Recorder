package com.ownervortex.nyxrecorder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

object ThumbnailCache {
    private const val MAX_WIDTH = 480

    suspend fun get(context: Context, uri: String, id: String): Bitmap? =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, "thumbs")
            dir.mkdirs()
            val file = File(dir, "$id.jpg")
            if (file.exists()) {
                try {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { return@withContext it }
                } catch (_: Exception) {
                }
                file.delete()
            }
            val bitmap = extract(context, uri) ?: return@withContext null
            try {
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
            } catch (_: Exception) {
            }
            bitmap
        }

    private fun extract(context: Context, uri: String): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uri))
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val atUs = (duration / 2).coerceIn(0L, 5_000_000L) * 1000
            var frame = retriever.getFrameAtTime(
                atUs,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC
            ) ?: retriever.frameAtTime
            if (frame != null && frame.width > MAX_WIDTH) {
                val scale = MAX_WIDTH.toFloat() / frame.width
                val scaled = Bitmap.createScaledBitmap(
                    frame,
                    MAX_WIDTH,
                    (frame.height * scale).toInt().coerceAtLeast(1),
                    true
                )
                if (scaled != frame) frame.recycle()
                frame = scaled
            }
            frame
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun clear(context: Context) {
        try {
            File(context.cacheDir, "thumbs").listFiles()?.forEach { it.delete() }
        } catch (_: Exception) {
        }
    }

    fun cacheBytes(context: Context): Long {
        return try {
            File(context.cacheDir, "thumbs").listFiles()?.sumOf { it.length() } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}
