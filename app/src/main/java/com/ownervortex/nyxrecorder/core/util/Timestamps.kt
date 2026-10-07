package com.ownervortex.nyxrecorder.core.util

import com.ownervortex.nyxrecorder.data.SettingsStore
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Timestamps {

    /**
     * Formats [epochMs] using the timezone the user picked in Settings: the
     * device's local timezone, or a fixed Asia/Kolkata (India) timezone. File
     * names intentionally keep their own independent format.
     */
    fun format(epochMs: Long, pattern: String = "dd MMM yyyy, HH:mm"): String {
        val sdf = SimpleDateFormat(pattern, Locale.getDefault())
        sdf.timeZone =
            if (SettingsStore.timestampIndia) TimeZone.getTimeZone("Asia/Kolkata")
            else TimeZone.getDefault()
        return sdf.format(Date(epochMs))
    }
}
