package com.ownervortex.nyxrecorder.core.recording

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.ownervortex.nyxrecorder.core.util.Constants

object RecordingController {

    fun start(context: Context, projectionData: Intent, config: RecordingConfig) {
        val intent = Intent(context, ScreenCaptureService::class.java).apply {
            action = Constants.ACTION_START
            putExtra(ScreenCaptureService.EXTRA_PROJECTION_DATA, projectionData)
            putExtra(ScreenCaptureService.EXTRA_CONFIG, config)
        }
        ContextCompat.startForegroundService(context, intent)
    }

    fun pause(context: Context) {
        send(context, Constants.ACTION_PAUSE)
    }

    fun resume(context: Context) {
        send(context, Constants.ACTION_RESUME)
    }

    fun stop(context: Context) {
        send(context, Constants.ACTION_STOP)
    }

    private fun send(context: Context, action: String) {
        try {
            context.startService(
                Intent(context, ScreenCaptureService::class.java).setAction(action)
            )
        } catch (_: Exception) {
        }
    }
}
