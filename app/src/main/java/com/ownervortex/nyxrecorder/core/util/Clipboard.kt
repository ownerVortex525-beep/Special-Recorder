package com.ownervortex.nyxrecorder.core.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context

/** Copies the given text to the system clipboard and returns true on success. */
fun copyToClipboard(context: Context, label: CharSequence, text: CharSequence) {
    try {
        val manager = context.applicationContext
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        manager.setPrimaryClip(ClipData.newPlainText(label, text))
    } catch (_: Throwable) {
    }
}
