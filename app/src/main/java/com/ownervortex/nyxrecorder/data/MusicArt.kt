package com.ownervortex.nyxrecorder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.LruCache

/**
 * Loads and caches embedded album art for music tracks.
 */
object MusicArt {
    private val cache = object : LruCache<String, Bitmap>(16) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(context: Context, uriStr: String): Bitmap? {
        synchronized(cache) {
            cache.get(uriStr)?.let { return it }
        }
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(uriStr))
            val pic = retriever.embeddedPicture ?: return null
            val bmp = BitmapFactory.decodeByteArray(pic, 0, pic.size) ?: return null
            synchronized(cache) { cache.put(uriStr, bmp) }
            bmp
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    fun clear() {
        synchronized(cache) { cache.evictAll() }
    }
}
