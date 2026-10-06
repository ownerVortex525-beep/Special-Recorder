package com.ownervortex.nyxrecorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.ownervortex.nyxrecorder.core.util.Constants
import com.ownervortex.nyxrecorder.core.util.CrashLog
import com.ownervortex.nyxrecorder.data.SettingsStore

class NixApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SettingsStore.init(this)
        CrashLog.install(this)
        createChannels()
    }

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java) ?: return

        val recording = NotificationChannel(
            Constants.RECORDING_CHANNEL_ID,
            "Screen recording",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while a screen recording is running"
            setShowBadge(false)
        }
        manager.createNotificationChannel(recording)

        val export = NotificationChannel(
            EXPORT_CHANNEL_ID,
            "Video export",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shown while videos are being exported or processed"
            setShowBadge(false)
        }
        manager.createNotificationChannel(export)
    }

    companion object {
        const val EXPORT_CHANNEL_ID = "nyx_export"
        const val EXPORT_NOTIFICATION_ID = 4200
    }
}
