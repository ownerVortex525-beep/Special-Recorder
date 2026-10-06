package com.ownervortex.nyxrecorder.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class MusicTrack(
    val id: String,
    val uri: String,
    val title: String,
    val artist: String,
    val durationMs: Long
)

class MusicRepository(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_MEDIA_AUDIO) ==
            PackageManager.PERMISSION_GRANTED ||
            (android.os.Build.VERSION.SDK_INT < 33 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED)

    suspend fun loadMusic(): List<MusicTrack> = withContext(Dispatchers.IO) {
        if (!hasPermission()) return@withContext emptyList()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.DURATION
        )
        val result = mutableListOf<MusicTrack>()
        try {
            context.contentResolver.query(
                collection, projection,
                "${MediaStore.Audio.Media.IS_MUSIC} = 1",
                null,
                "${MediaStore.Audio.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += MusicTrack(
                        id = id.toString(),
                        uri = uri.toString(),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown",
                        durationMs = cursor.getLong(durCol)
                    )
                    if (result.size >= 300) break
                }
            }
        } catch (_: Exception) {
        }
        result
    }
}
