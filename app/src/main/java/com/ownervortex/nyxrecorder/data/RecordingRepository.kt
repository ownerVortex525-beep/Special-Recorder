package com.ownervortex.nyxrecorder.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.ownervortex.nyxrecorder.core.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class RecordingRepository(private val context: Context) {

    suspend fun loadRecordings(): List<Recording> = withContext(Dispatchers.IO) {
        val favorites = SettingsStore.favorites()
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.WIDTH,
            MediaStore.Video.Media.HEIGHT
        )
        val selection: String?
        val args: Array<String>?
        if (Build.VERSION.SDK_INT >= 29) {
            selection = "${MediaStore.Video.Media.RELATIVE_PATH} LIKE ?"
            args = arrayOf("%${Constants.RECORD_DIR}/%")
        } else {
            selection = "${MediaStore.Video.Media.DATA} LIKE ?"
            args = arrayOf("%/${Constants.RECORD_DIR}/%")
        }

        val result = mutableListOf<Recording>()
        try {
            context.contentResolver.query(
                collection, projection, selection, args,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val durIdx = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val wIdx = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val hIdx = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val uri = ContentUris.withAppendedId(collection, id)
                    result += Recording(
                        id = id.toString(),
                        uri = uri.toString(),
                        displayName = cursor.getString(nameCol) ?: "Video",
                        createdAt = cursor.getLong(dateCol) * 1000L,
                        sizeBytes = cursor.getLong(sizeCol),
                        durationMs = if (durIdx >= 0) cursor.getLong(durIdx) else 0L,
                        width = if (wIdx >= 0) cursor.getInt(wIdx) else 0,
                        height = if (hIdx >= 0) cursor.getInt(hIdx) else 0,
                        isFavorite = favorites.contains(id.toString())
                    )
                }
            }
        } catch (_: Exception) {
        }
        result
    }

    fun rename(recording: Recording, newName: String): Boolean {
        return try {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, newName)
            }
            val updated = context.contentResolver.update(
                Uri.parse(recording.uri), values, null, null
            )
            updated > 0
        } catch (_: Exception) {
            false
        }
    }

    fun delete(recording: Recording): Boolean {
        return try {
            context.contentResolver.delete(Uri.parse(recording.uri), null, null) > 0
        } catch (_: Exception) {
            false
        }
    }

    fun share(recording: Recording): Intent {
        val uri = Uri.parse(recording.uri)
        return Intent(Intent.ACTION_SEND).apply {
            type = "video/mp4"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    fun bytesUsed(recordings: List<Recording>): Long = recordings.sumOf { it.sizeBytes }

    fun scanFile(path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("video/mp4"), null)
    }

    fun newFile(): File {
        val dir = File(
            context.getExternalFilesDir(null)?.parentFile
                ?: File(android.os.Environment.getExternalStorageDirectory(), "Movies"),
            Constants.RECORD_DIR
        )
        dir.mkdirs()
        return dir
    }
}
