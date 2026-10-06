package com.ownervortex.nyxrecorder.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.core.content.ContextCompat
import com.ownervortex.nyxrecorder.core.recording.RecordingConfig
import com.ownervortex.nyxrecorder.core.recording.RecordingController
import com.ownervortex.nyxrecorder.core.recording.RecordingStatus
import com.ownervortex.nyxrecorder.core.util.Constants
import com.ownervortex.nyxrecorder.core.util.DeviceHealth
import com.ownervortex.nyxrecorder.data.Recording
import com.ownervortex.nyxrecorder.data.SettingsStore
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.screens.EditorScreen
import com.ownervortex.nyxrecorder.ui.screens.HomeScreen
import com.ownervortex.nyxrecorder.ui.screens.LibraryScreen
import com.ownervortex.nyxrecorder.ui.screens.MusicScreen
import com.ownervortex.nyxrecorder.ui.screens.PlayerScreen
import com.ownervortex.nyxrecorder.ui.screens.SettingsScreen
import com.ownervortex.nyxrecorder.ui.theme.NixBackground
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixSurface
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim
import com.ownervortex.nyxrecorder.ui.viewmodel.EditorViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.LibraryState
import com.ownervortex.nyxrecorder.ui.viewmodel.LibraryViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.MusicViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.SettingsViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private data class Tab(val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Library", Icons.Filled.Movie),
    Tab("Music", Icons.Filled.LibraryMusic),
    Tab("Settings", Icons.Filled.Settings)
)

@Composable
fun AppRoot() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val settingsViewModel: SettingsViewModel = viewModel()
    val libraryViewModel: LibraryViewModel = viewModel()
    val musicViewModel: MusicViewModel = viewModel()
    val editorViewModel: EditorViewModel = viewModel()

    val settings by settingsViewModel.state.collectAsState()
    val libraryState by libraryViewModel.state.collectAsState()
    val libraryMessage by libraryViewModel.message.collectAsState()
    val status by RecordingStatus.state.collectAsState()

    var tab by remember { mutableIntStateOf(0) }
    var playerTarget by remember { mutableStateOf<Recording?>(null) }
    var editorTarget by remember { mutableStateOf<Recording?>(null) }
    var countdown by remember { mutableIntStateOf(0) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var pendingOverlayCheck by remember { mutableStateOf(false) }
    var hintedAllFiles by remember { mutableStateOf(false) }

    // --------------------------------------------------------- launchers

    var startConsent: () -> Unit = {}

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val allGranted = results.values.all { it }
        if (allGranted) {
            startConsent()
        } else {
            // Mic/camera may still be optional — only block if a needed one is denied.
            val micNeeded = settings.recordMic || settings.recordDeviceAudio
            val micMissing = micNeeded &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
                PackageManager.PERMISSION_GRANTED
            val camMissing = settings.faceCam &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) !=
                PackageManager.PERMISSION_GRANTED
            if (micMissing || camMissing) {
                scope.launch {
                    snackbarHostState.showSnackbar("Needed permission denied — recording not started")
                }
            } else {
                startConsent()
            }
        }
    }

    val projectionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val data = result.data!!
            val config = buildConfig(context, settings)
            if (settings.countdown && countdown <= 0) {
                scope.launch {
                    for (i in 3 downTo 1) {
                        countdown = i
                        delay(1000)
                    }
                    countdown = 0
                    RecordingController.start(context, data, config)
                    libraryViewModel.refresh()
                }
            } else {
                RecordingController.start(context, data, config)
                libraryViewModel.refresh()
            }
        }
    }

    val musicPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        musicViewModel.load()
    }

    fun launchProjectionConsent() {
        val manager =
            context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        projectionLauncher.launch(manager.createScreenCaptureIntent())
    }

    startConsent = { launchProjectionConsent() }

    fun requestStart() {
        if (status.isRecording) return
        musicViewModel.stopPreview()
        if (DeviceHealth.lowStorage(context)) {
            scope.launch { snackbarHostState.showSnackbar("Not enough free storage to record") }
            return
        }
        if (settings.showBubble && !Settings.canDrawOverlays(context)) {
            pendingOverlayCheck = true
            try {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            } catch (_: Exception) {
                pendingOverlayCheck = false
            }
            scope.launch {
                snackbarHostState.showSnackbar("Grant “Display over apps”, then tap record again")
            }
            return
        }

        if (Build.VERSION.SDK_INT >= 30 &&
            !com.ownervortex.nyxrecorder.core.util.StoragePaths.hasAllFilesAccess(context) &&
            !hintedAllFiles
        ) {
            hintedAllFiles = true
            scope.launch {
                snackbarHostState.showSnackbar(
                    "Saving to Movies/NYX Recorder — grant All files access in Settings to save to /NYX Recorder"
                )
            }
        }

        val needed = mutableListOf<String>()
        if (settings.recordMic || settings.recordDeviceAudio) needed += Manifest.permission.RECORD_AUDIO
        if (settings.faceCam) needed += Manifest.permission.CAMERA
        if (Build.VERSION.SDK_INT >= 33) needed += Manifest.permission.POST_NOTIFICATIONS
        if (Build.VERSION.SDK_INT < 29) needed += Manifest.permission.WRITE_EXTERNAL_STORAGE

        val missing = needed.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
        } else {
            launchProjectionConsent()
        }
    }

    // Resume handler: continue once overlay permission granted.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && pendingOverlayCheck) {
                pendingOverlayCheck = false
                if (Settings.canDrawOverlays(context)) {
                    scope.launch {
                        snackbarHostState.showSnackbar("Bubble enabled — tap record")
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Snackbar for library actions.
    LaunchedEffect(libraryMessage) {
        libraryMessage?.let {
            snackbarHostState.showSnackbar(it)
            libraryViewModel.consumeMessage()
        }
    }

    // Refresh library when a recording stops.
    var wasRecording by remember { mutableStateOf(status.isRecording) }
    LaunchedEffect(status.isRecording) {
        if (wasRecording && !status.isRecording) libraryViewModel.refresh()
        wasRecording = status.isRecording
    }

    // ------------------------------------------------------ back handling

    val activeOverlay = editorTarget ?: playerTarget
    BackHandler(enabled = activeOverlay != null) {
        if (editorTarget != null) {
            editorTarget = null
            editorViewModel.reset()
        } else {
            playerTarget = null
        }
    }

    // ------------------------------------------------------------ content

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            containerColor = NixBackground,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (activeOverlay == null) {
                    NavigationBar(containerColor = NixSurface) {
                        tabs.forEachIndexed { index, item ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = {
                                    tab = index
                                    if (index == 1) libraryViewModel.refresh()
                                    if (index == 2 && musicViewModel.hasPermission()) {
                                        musicViewModel.load()
                                    }
                                },
                                icon = { Icon(item.icon, contentDescription = item.label) },
                                label = { Text(item.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = NixPrimary,
                                    selectedTextColor = NixPrimary,
                                    unselectedIconColor = NixTextDim,
                                    unselectedTextColor = NixTextDim,
                                    indicatorColor = NixPrimary.copy(alpha = 0.16f)
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Box(Modifier.padding(padding)) {
                val editor = editorTarget
                val player = playerTarget
                when {
                    editor != null -> {
                        EditorScreen(
                            recording = editor,
                            viewModel = editorViewModel,
                            onBack = {
                                editorTarget = null
                                editorViewModel.reset()
                            },
                            onExported = {
                                snackbarHostState.let { host ->
                                    scope.launch { host.showSnackbar("Export saved to NYX Recorder") }
                                }
                                editorViewModel.consumeDone()
                                editorTarget = null
                                libraryViewModel.refresh()
                            }
                        )
                    }
                    player != null -> {
                        PlayerScreen(
                            recording = player,
                            onBack = { playerTarget = null },
                            onEdit = {
                                editorTarget = player
                                playerTarget = null
                            },
                            onShare = {
                                try {
                                    context.startActivity(
                                        Intent.createChooser(
                                            libraryViewModel.share(player), "Share recording"
                                        )
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            onDelete = {
                                libraryViewModel.delete(player)
                                playerTarget = null
                            },
                            onFavorite = {
                                libraryViewModel.toggleFavorite(player)
                                playerTarget = null
                            }
                        )
                    }
                    tab == 0 -> {
                        HomeScreen(
                            status = status,
                            qualityLabel = qualityLabelFor(settings.qualityPreset),
                            micOn = settings.recordMic,
                            deviceAudioOn = settings.recordDeviceAudio,
                            bubbleOn = settings.showBubble,
                            freeSpaceLabel = freeSpaceLabel(context),
                            onRecord = { requestStart() },
                            onPauseToggle = {
                                if (status.isPaused) RecordingController.resume(context)
                                else RecordingController.pause(context)
                            },
                            onStop = { RecordingController.stop(context) },
                            countdownValue = countdown
                        )
                    }
                    tab == 1 -> {
                        val items = (libraryState as? LibraryState.Loaded)?.items ?: emptyList()
                        LibraryScreen(
                            items = items,
                            loading = libraryState is LibraryState.Loading,
                            onPlay = { playerTarget = it },
                            onEdit = { editorTarget = it },
                            onShare = {
                                try {
                                    context.startActivity(
                                        Intent.createChooser(libraryViewModel.share(it), "Share recording")
                                    )
                                } catch (_: Exception) {
                                }
                            },
                            onDelete = { libraryViewModel.delete(it) },
                            onRename = { rec, name -> libraryViewModel.rename(rec, name) },
                            onFavorite = { libraryViewModel.toggleFavorite(it) }
                        )
                    }
                    tab == 2 -> {
                        MusicScreen(
                            musicViewModel = musicViewModel,
                            settingsViewModel = settingsViewModel
                        )
                    }
                    else -> {
                        SettingsScreen(
                            viewModel = settingsViewModel,
                            onRequestMusicPermission = {
                                if (musicViewModel.hasPermission()) {
                                    musicViewModel.load()
                                } else {
                                    musicPermissionLauncher.launch(
                                        if (Build.VERSION.SDK_INT >= 33) {
                                            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
                                        } else {
                                            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                                        }
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun qualityLabelFor(preset: Int): String = when (preset) {
    0 -> "Auto"
    1 -> "1080p"
    2 -> "720p"
    else -> "480p"
}

private fun freeSpaceLabel(context: Context): String {
    val bytes = DeviceHealth.freeSpaceBytes()
    return if (bytes in 1 until Constants.MIN_FREE_BYTES) {
        "Low — ${DeviceHealth.formatBytes(bytes)} free"
    } else {
        DeviceHealth.formatBytes(bytes)
    }
}

private fun buildConfig(
    context: Context,
    settings: com.ownervortex.nyxrecorder.ui.viewmodel.SettingsUi
): RecordingConfig {
    val dm = context.resources.displayMetrics
    val width = dm.widthPixels
    val height = dm.heightPixels
    val long = maxOf(width, height)
    val short = minOf(width, height)

    val targetShort = when (settings.qualityPreset) {
        1 -> 1080
        2 -> 720
        3 -> 480
        else -> short
    }
    val scale = if (settings.qualityPreset == 0) 1f
    else minOf(1f, targetShort.toFloat() / short)

    val w = ((width * scale).toInt() / 2 * 2).coerceAtLeast(2)
    val h = ((height * scale).toInt() / 2 * 2).coerceAtLeast(2)
    val fps = 30
    val bitRate = (w.toLong() * h * fps / 14).coerceIn(2_000_000L, 12_000_000L).toInt()

    return RecordingConfig(
        width = w,
        height = h,
        densityDpi = dm.densityDpi,
        frameRate = fps,
        bitRate = bitRate,
        recordMic = settings.recordMic,
        recordDeviceAudio = settings.recordDeviceAudio && Build.VERSION.SDK_INT >= 29,
        musicUri = settings.musicUri,
        musicVolume = settings.musicVolume,
        showBubble = settings.showBubble,
        faceCam = settings.faceCam
    )
}
