package com.ownervortex.nyxrecorder.core.util

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Writes the last uncaught exception to filesDir so crashes (e.g. GPU-specific
 * editor issues) can be diagnosed from Settings instead of being invisible.
 */
object CrashLog {

    private const val DIR = "crash"
    private const val FILE = "last.log"

    fun file(context: Context): File =
        File(File(context.filesDir, DIR), FILE)

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val target = file(app)
                target.parentFile?.mkdirs()
                target.writeText(
                    buildString {
                        appendLine(
                            "Time: " + SimpleDateFormat(
                                "yyyy-MM-dd HH:mm:ss", Locale.US
                            ).format(Date())
                        )
                        appendLine(
                            "Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} " +
                                "(Android ${android.os.Build.VERSION.RELEASE}, SDK ${android.os.Build.VERSION.SDK_INT})"
                        )
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    }
                )
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    /** Last crash report text, or null when nothing was recorded. */
    fun read(context: Context): String? = try {
        val f = file(context)
        if (f.exists() && f.length() > 0) f.readText() else null
    } catch (_: Exception) {
        null
    }

    fun clear(context: Context) {
        try {
            file(context).delete()
        } catch (_: Exception) {
        }
    }
}
