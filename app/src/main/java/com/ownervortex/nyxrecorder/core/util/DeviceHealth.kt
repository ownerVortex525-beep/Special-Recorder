package com.ownervortex.nyxrecorder.core.util

import android.app.ActivityManager
import android.app.ActivityManager
import android.content.Context
import android.os.PowerManager
import java.io.File
import java.util.Locale

object DeviceHealth {

     fun freeSpaceBytes(): Long {
        return try {
            val dir = com.ownervortex.nyxrecorder.core.util.StoragePaths.directDir()
            dir.usableSpace
        } catch (_: Exception) {
            0L
        }
    }

    fun lowStorage(context: Context): Boolean {
        val low = freeSpaceBytes() in 1 until Constants.MIN_FREE_BYTES
        if (low) return true
        return try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            info.lowMemory || info.availMem < 250L * 1024 * 1024
        } catch (_: Exception) {
            false
        }
    }

    fun thermalSevere(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT < 29) return false
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
        } catch (_: Exception) {
            false
        }
    }

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.size - 1) {
            value /= 1024
            unit++
        }
        return String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun formatDuration(ms: Long): String {
        val totalSec = ms / 1000
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%02d:%02d", m, s)
    }
}
