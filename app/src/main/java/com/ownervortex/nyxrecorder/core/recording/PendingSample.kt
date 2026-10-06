package com.ownervortex.nyxrecorder.core.recording

/** Holds an encoded sample until the muxer has started (both tracks must exist first). */
internal class PendingSample(
    val data: ByteArray,
    val offset: Int,
    val size: Int,
    val ptsUs: Long,
    val flags: Int
)
