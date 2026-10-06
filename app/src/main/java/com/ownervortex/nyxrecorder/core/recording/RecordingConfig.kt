package com.ownervortex.nyxrecorder.core.recording

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class RecordingConfig(
    val width: Int,
    val height: Int,
    val densityDpi: Int,
    val frameRate: Int = 30,
    val bitRate: Int = 6_000_000,
    val recordMic: Boolean = false,
    val recordDeviceAudio: Boolean = false,
    val musicUri: String? = null,
    val musicVolume: Float = 0.7f,
    val showBubble: Boolean = true,
    val faceCam: Boolean = false
) : Parcelable {
    val audioNeeded: Boolean
        get() = recordMic || recordDeviceAudio || musicUri != null

    val qualityLabel: String
        get() = "${if (width >= height) height else width}p $frameRate"
}
