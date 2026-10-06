package com.ownervortex.nyxrecorder.ui.viewmodel

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ownervortex.nyxrecorder.data.MusicRepository
import com.ownervortex.nyxrecorder.data.MusicTrack
import com.ownervortex.nyxrecorder.data.Recording
import com.ownervortex.nyxrecorder.data.RecordingRepository
import com.ownervortex.nyxrecorder.data.SettingsStore
import com.ownervortex.nyxrecorder.export.EditFilter
import com.ownervortex.nyxrecorder.export.ExportManager
import com.ownervortex.nyxrecorder.export.ExportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// -------------------------------------------------------------- settings

data class SettingsUi(
    val qualityPreset: Int = 1,
    val recordMic: Boolean = false,
    val recordDeviceAudio: Boolean = false,
    val showBubble: Boolean = true,
    val bubbleSize: Int = 1,
    val bubbleOpacity: Float = 0.5f,
    val countdown: Boolean = true,
    val faceCam: Boolean = false,
    val touchIndicator: Boolean = false,
    val musicVolume: Float = 0.7f,
    val musicUri: String? = null,
    val musicTitle: String? = null,
    val themeIndex: Int = 0
)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {

    private val _state = MutableStateFlow(readStore())
    val state: StateFlow<SettingsUi> = _state.asStateFlow()

    private fun readStore() = SettingsUi(
        qualityPreset = SettingsStore.qualityPreset,
        recordMic = SettingsStore.recordMic,
        recordDeviceAudio = SettingsStore.recordDeviceAudio,
        showBubble = SettingsStore.showBubble,
        bubbleSize = SettingsStore.bubbleSize,
        bubbleOpacity = SettingsStore.bubbleOpacity,
        countdown = SettingsStore.countdown,
        faceCam = SettingsStore.faceCam,
        touchIndicator = SettingsStore.touchIndicator,
        musicVolume = SettingsStore.musicVolume,
        musicUri = SettingsStore.musicUri,
        musicTitle = SettingsStore.musicTitle,
        themeIndex = SettingsStore.themeIndex
    )

    fun setQuality(index: Int) {
        SettingsStore.qualityPreset = index
        refresh()
    }

    fun setMic(enabled: Boolean) {
        SettingsStore.recordMic = enabled
        refresh()
    }

    fun setDeviceAudio(enabled: Boolean) {
        SettingsStore.recordDeviceAudio = enabled
        refresh()
    }

    fun setBubble(enabled: Boolean) {
        SettingsStore.showBubble = enabled
        refresh()
    }

    fun setBubbleSize(index: Int) {
        SettingsStore.bubbleSize = index
        refresh()
    }

    fun setBubbleOpacity(value: Float) {
        SettingsStore.bubbleOpacity = value
        refresh()
    }

    fun setCountdown(enabled: Boolean) {
        SettingsStore.countdown = enabled
        refresh()
    }

    fun setFaceCam(enabled: Boolean) {
        SettingsStore.faceCam = enabled
        refresh()
    }

    fun setTouchIndicator(enabled: Boolean) {
        SettingsStore.touchIndicator = enabled
        refresh()
    }

    fun setTheme(index: Int) {
        SettingsStore.themeIndex = index
        com.ownervortex.nyxrecorder.ui.theme.setNixPalette(index)
        refresh()
    }

    fun setMusicVolume(value: Float) {
        SettingsStore.musicVolume = value
        refresh()
    }

    fun clearMusic() {
        SettingsStore.musicUri = null
        SettingsStore.musicTitle = null
        refresh()
    }

    fun setMusic(uri: String?, title: String?) {
        SettingsStore.musicUri = uri
        SettingsStore.musicTitle = title
        refresh()
    }

    fun refresh() {
        _state.value = readStore()
    }
}

// -------------------------------------------------------------- library

sealed interface LibraryState {
    data object Loading : LibraryState
    data class Loaded(val items: List<Recording>) : LibraryState
}

class LibraryViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = RecordingRepository(app)
    private val _state = MutableStateFlow<LibraryState>(LibraryState.Loading)
    val state: StateFlow<LibraryState> = _state.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val items = repository.loadRecordings()
            _state.value = LibraryState.Loaded(items)
        }
    }

    /** Refresh now and again after the media scanner has had time to index. */
    fun refreshWithRetry() {
        refresh()
        viewModelScope.launch {
            delay(1200)
            refresh()
        }
    }

    fun delete(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.delete(recording)
            withContext(Dispatchers.Main) {
                _message.value = "Deleted ${recording.displayName}"
                refresh()
            }
        }
    }

    fun rename(recording: Recording, newName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val ok = repository.rename(recording, newName)
            withContext(Dispatchers.Main) {
                if (ok) _message.value = "Renamed"
                else _message.value = "Rename failed"
                refresh()
            }
        }
    }

    fun toggleFavorite(recording: Recording) {
        viewModelScope.launch(Dispatchers.IO) {
            SettingsStore.setFavorite(recording.id, !recording.isFavorite)
            withContext(Dispatchers.Main) { refresh() }
        }
    }

    fun share(recording: Recording) = repository.share(recording)

    fun totalBytes(items: List<Recording>): Long = repository.bytesUsed(items)

    fun consumeMessage() {
        _message.value = null
    }
}

// -------------------------------------------------------------- music

class MusicViewModel(app: Application) : AndroidViewModel(app) {

    private val repository = MusicRepository(app)
    private val _tracks = MutableStateFlow<List<MusicTrack>>(emptyList())
    val tracks: StateFlow<List<MusicTrack>> = _tracks.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _selected = MutableStateFlow(SettingsStore.musicUri)
    val selected: StateFlow<String?> = _selected.asStateFlow()

    // ---------------------------------------------------------- preview playback
    private var previewPlayer: androidx.media3.exoplayer.ExoPlayer? = null

    private val _previewUri = MutableStateFlow<String?>(null)
    val previewUri: StateFlow<String?> = _previewUri.asStateFlow()

    private val _previewPlaying = MutableStateFlow(false)
    val previewPlaying: StateFlow<Boolean> = _previewPlaying.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _loading.value = true
            _tracks.value = repository.loadMusic()
            _loading.value = false
        }
    }

    fun select(track: MusicTrack?) {
        SettingsStore.musicUri = track?.uri
        SettingsStore.musicTitle = track?.title
        _selected.value = track?.uri
    }

    /**
     * Tap preview: a new track plays from 0:00, tapping the playing track
     * toggles pause/resume.
     */
    fun togglePreview(track: MusicTrack) {
        val player = previewPlayer ?: androidx.media3.exoplayer.ExoPlayer
            .Builder(getApplication())
            .build()
            .also { previewPlayer = it }
        if (_previewUri.value == track.uri) {
            if (_previewPlaying.value) {
                player.pause()
                _previewPlaying.value = false
            } else {
                player.play()
                _previewPlaying.value = true
            }
        } else {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(track.uri))
            player.prepare()
            player.play()
            _previewUri.value = track.uri
            _previewPlaying.value = true
        }
    }

    /** Stops the preview (used when leaving the screen or starting a recording). */
    fun stopPreview() {
        try {
            previewPlayer?.stop()
        } catch (_: Exception) {
        }
        _previewUri.value = null
        _previewPlaying.value = false
    }

    override fun onCleared() {
        try {
            previewPlayer?.release()
        } catch (_: Exception) {
        }
        previewPlayer = null
        super.onCleared()
    }

    fun hasPermission(): Boolean = repository.hasPermission()
}

// -------------------------------------------------------------- editor

sealed interface ExportState {
    data object Idle : ExportState
    data class Running(val progress: Int) : ExportState
    data class Done(val uri: Uri) : ExportState
    data class Failed(val reason: String) : ExportState
}

data class EditParams(
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = -1L,
    val speed: Float = 1f,
    val filter: EditFilter = EditFilter.NONE,
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val cropPreset: Int = 0,
    val output720: Boolean = false,
    val watermarkText: String = "",
    val watermarkCorner: Int = 3
)

/** Maps editor params onto the export request (also used for live preview). */
fun EditParams.toExportRequest(inputUri: String, portrait: Boolean): ExportRequest {
    val crop = when (cropPreset) {
        1 -> Triple(0.125f, 0f, 0.125f)      // 4:3-ish from 16:9
        2 -> Triple(0.25f, 0f, 0.25f)        // 1:1
        3 -> Triple(0.3f, 0f, 0.3f)          // 9:16 vertical
        else -> Triple(0f, 0f, 0f)
    }
    return ExportRequest(
        inputUri = inputUri,
        trimStartMs = trimStartMs,
        trimEndMs = if (trimEndMs > trimStartMs) trimEndMs else -1L,
        speed = speed,
        filter = filter,
        brightness = brightness,
        contrast = contrast,
        cropLeft = crop.first,
        cropTop = crop.second,
        cropRight = crop.first,
        cropBottom = crop.third,
        output720 = output720,
        watermark = watermarkText.ifBlank { null },
        watermarkCorner = watermarkCorner,
        portrait = portrait
    )
}

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    private val _params = MutableStateFlow(EditParams())
    val params: StateFlow<EditParams> = _params.asStateFlow()

    private val _export = MutableStateFlow<ExportState>(ExportState.Idle)
    val export: StateFlow<ExportState> = _export.asStateFlow()

    private var manager: ExportManager? = null

    var durationMs: Long = 0L
        private set

    var sourcePortrait: Boolean = false
        private set

    fun setDuration(ms: Long) {
        durationMs = ms
        if (_params.value.trimEndMs < 0) {
            _params.value = _params.value.copy(trimEndMs = ms)
        }
    }

    fun setSourceSize(width: Int, height: Int) {
        sourcePortrait = width > 0 && height > width
    }

    fun update(transform: (EditParams) -> EditParams) {
        _params.value = transform(_params.value)
    }

    fun reset() {
        _params.value = EditParams()
        _export.value = ExportState.Idle
        durationMs = 0L
        sourcePortrait = false
    }

    fun export(inputUri: String) {
        if (_export.value is ExportState.Running) return
        val request = _params.value.toExportRequest(inputUri, sourcePortrait)
        _export.value = ExportState.Running(0)
        val exportManager = ExportManager(getApplication())
        manager = exportManager
        exportManager.start(
            request,
            onProgress = { value -> _export.value = ExportState.Running(value) },
            onDone = { uri -> _export.value = ExportState.Done(uri) },
            onExportError = { reason -> _export.value = ExportState.Failed(reason) }
        )
    }

    fun cancelExport() {
        manager?.cancel()
        manager = null
        _export.value = ExportState.Idle
    }

    fun consumeDone() {
        if (_export.value is ExportState.Done) _export.value = ExportState.Idle
    }
}
