package com.ownervortex.nyxrecorder.core.util

import android.os.Build
import android.os.Environment
import java.io.File

/**
 * Output folder strategy:
 *  - If the user granted "All files access" → /storage/emulated/0/NYX Recorder
 *  - Else (API 29+) → MediaStore RELATIVE_PATH "Movies/NYX Recorder"
 *  - Else (API 26-28) → File write to /sdcard/NYX Recorder (needs storage permission)
 *
 * Everything resolves to a folder called `NYX Recorder` at the root of internal
 * storage, not inside Movies.
 */
object StoragePaths {
    const val ROOT_NAME = "NYX Recorder"

    fun hasAllFilesAccess(context: android.content.Context): Boolean =
        Build.VERSION.SDK_INT >= 30 &&
            Environment.isExternalStorageManager()

    /** Directory for direct File writes (used with All-files-access or API < 29). */
    fun directDir(): File {
        val base = Environment.getExternalStorageDirectory()
        return File(base, ROOT_NAME).apply { mkdirs() }
    }

    /** RELATIVE_PATH value used when writing via MediaStore (no all-files-access). */
    fun relativePath(): String = "Movies/$ROOT_NAME"

    fun newCaptureFile(): File {
        val dir = directDir()
        val name = "NYX_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date()) + ".mp4"
        return File(dir, name)
    }

    fun exportFile(): File {
        val dir = directDir()
        val name = "NYX_EDIT_" +
            java.text.SimpleDateFormat("yyyyMMdd_HHmmss", java.util.Locale.US)
                .format(java.util.Date()) + ".mp4"
        return File(dir, name)
    }
}
