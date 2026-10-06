package com.ownervortex.nyxrecorder.data

data class Recording(
    val id: String,
    val uri: String,
    val displayName: String,
    val createdAt: Long,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val isFavorite: Boolean = false
) {
    val resolutionLabel: String
        get() = if (width > 0 && height > 0) "${width}x$height" else ""
}
