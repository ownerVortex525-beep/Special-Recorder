package com.ownervortex.nyxrecorder.core.recording

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPaused: Boolean = false,
    val elapsedMs: Long = 0L,
    val fileName: String = "",
    val qualityLabel: String = ""
)

object RecordingStatus {
    private val _state = MutableStateFlow(RecordingUiState())
    val state: StateFlow<RecordingUiState> = _state.asStateFlow()

    fun onStart(fileName: String, qualityLabel: String) {
        _state.value = RecordingUiState(
            isRecording = true,
            isPaused = false,
            elapsedMs = 0L,
            fileName = fileName,
            qualityLabel = qualityLabel
        )
    }

    fun onTick(elapsedMs: Long, paused: Boolean) {
        val cur = _state.value
        if (!cur.isRecording) return
        _state.value = cur.copy(elapsedMs = elapsedMs, isPaused = paused)
    }

    fun onPaused(paused: Boolean) {
        val cur = _state.value
        if (!cur.isRecording) return
        _state.value = cur.copy(isPaused = paused)
    }

    fun onStop() {
        _state.value = RecordingUiState()
    }
}
