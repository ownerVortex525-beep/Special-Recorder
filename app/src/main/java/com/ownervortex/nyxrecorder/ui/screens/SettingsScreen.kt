package com.ownervortex.nyxrecorder.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownervortex.nyxrecorder.core.util.DeviceHealth
import com.ownervortex.nyxrecorder.data.ThumbnailCache
import com.ownervortex.nyxrecorder.ui.components.AccentBar
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.components.NixChip
import com.ownervortex.nyxrecorder.ui.components.SectionTitle
import com.ownervortex.nyxrecorder.ui.components.SettingRow
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim
import com.ownervortex.nyxrecorder.ui.viewmodel.SettingsViewModel
import java.util.Locale

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onRequestMusicPermission: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current

    var allFilesGranted by remember {
        mutableStateOf(
            Build.VERSION.SDK_INT >= 30 &&
                android.os.Environment.isExternalStorageManager()
        )
    }
    var crashReport by remember {
        mutableStateOf(
            com.ownervortex.nyxrecorder.core.util.CrashLog.read(context)
        )
    }
    var showCrashLog by remember { mutableStateOf(false) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                allFilesGranted = Build.VERSION.SDK_INT >= 30 &&
                    android.os.Environment.isExternalStorageManager()
                crashReport =
                    com.ownervortex.nyxrecorder.core.util.CrashLog.read(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        SectionTitle("RECORDING")

        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Quality", color = NixText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Cap resolution; frame rate stays at 30 fps",
                    color = NixTextDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                    listOf("Auto", "1080p", "720p", "480p").forEachIndexed { index, label ->
                        NixChip(
                            label = label,
                            selected = state.qualityPreset == index,
                            onClick = { viewModel.setQuality(index) }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        NixCard(Modifier.fillMaxWidth()) {
            Column {
                ToggleSetting(
                    title = "Microphone",
                    subtitle = "Record your voice while recording",
                    checked = state.recordMic,
                    onChange = { viewModel.setMic(it) }
                )
                ToggleSetting(
                    title = "Internal audio",
                    subtitle = "Record app & game sound (Android 10+)",
                    checked = state.recordDeviceAudio,
                    onChange = { viewModel.setDeviceAudio(it) }
                )
                ToggleSetting(
                    title = "Countdown",
                    subtitle = "3… 2… 1… before capture starts",
                    checked = state.countdown,
                    onChange = { viewModel.setCountdown(it) }
                )
                ToggleSetting(
                    title = "Control bubble",
                    subtitle = "Floating pause / stop while recording",
                    checked = state.showBubble,
                    onChange = { viewModel.setBubble(it) }
                )
                ToggleSetting(
                    title = "Face cam",
                    subtitle = "Front camera window, captured in video",
                    checked = state.faceCam,
                    onChange = { viewModel.setFaceCam(it) }
                )
                ToggleSetting(
                    title = "Touch indicator",
                    subtitle = "Show taps while recording",
                    checked = state.touchIndicator,
                    onChange = { enabled ->
                        viewModel.setTouchIndicator(enabled)
                        if (enabled &&
                            Build.VERSION.SDK_INT >= 23 &&
                            !Settings.canWrite(context)
                        ) {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (_: Exception) {
                            }
                            android.widget.Toast.makeText(
                                context,
                                "Grant “Modify system settings”, then the taps will show while recording",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("PERMISSIONS")
        NixCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow("Music access", subtitle = "Pick background music") {
                    TextButton(onClick = onRequestMusicPermission) {
                        Text("Grant", color = NixPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                SettingRow(
                    "Display over apps",
                    subtitle = "Required for the control bubble"
                ) {
                    TextButton(onClick = {
                        try {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:${context.packageName}")
                            )
                            context.startActivity(intent)
                        } catch (_: Exception) {
                        }
                    }) {
                        Text("Open", color = NixPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                if (Build.VERSION.SDK_INT >= 23) {
                    SettingRow("Modify system settings", subtitle = "For the touch indicator") {
                        TextButton(onClick = {
                            try {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                                context.startActivity(intent)
                            } catch (_: Exception) {
                            }
                        }) {
                            Text("Open", color = NixPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("STORAGE")
        val thumbBytes = ThumbnailCache.cacheBytes(context)
        val freeBytes = DeviceHealth.freeSpaceBytes()
        NixCard(Modifier.fillMaxWidth()) {
            Column {
                SettingRow(
                    "Free space",
                    subtitle = DeviceHealth.formatBytes(freeBytes)
                ) {}
                SettingRow(
                    "Thumbnail cache",
                    subtitle = DeviceHealth.formatBytes(thumbBytes)
                ) {
                    TextButton(onClick = { ThumbnailCache.clear(context) }) {
                        Text("Clear", color = NixPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                SettingRow(
                    "Output folder",
                    subtitle = if (allFilesGranted)
                        "/storage/emulated/0/NYX Recorder"
                    else
                        "Movies/NYX Recorder (fallback)"
                ) {
                    if (Build.VERSION.SDK_INT >= 30 && !allFilesGranted) {
                        TextButton(onClick = {
                            try {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                                        Uri.parse("package:${context.packageName}")
                                    )
                                )
                            } catch (_: Exception) {
                                context.startActivity(
                                    Intent(
                                        Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION
                                    )
                                )
                            }
                        }) {
                            Text("Grant", color = NixPrimary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("ABOUT")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "NYX-RECORDER",
                    color = NixText, fontSize = 16.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Version 2.1.0 • Screen recorder & editor",
                    color = NixTextDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Recordings are saved only on this device, in NYX Recorder. " +
                        "Nothing is uploaded anywhere.",
                    color = NixTextDim, fontSize = 12.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        NixCard(Modifier.fillMaxWidth()) {
            SettingRow(
                "Crash log",
                subtitle = if (crashReport != null)
                    "Last crash was recorded"
                else
                    "No crashes recorded"
            ) {
                if (crashReport != null) {
                    TextButton(onClick = { showCrashLog = true }) {
                        Text("View", color = NixPrimary, fontWeight = FontWeight.Bold)
                    }
                    TextButton(onClick = {
                        com.ownervortex.nyxrecorder.core.util.CrashLog.clear(context)
                        crashReport = null
                        showCrashLog = false
                    }) {
                        Text("Clear", color = NixTextDim, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }

    if (showCrashLog && crashReport != null) {
        androidx.compose.ui.window.Dialog(onDismissRequest = { showCrashLog = false }) {
            NixCard(
                Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        "Crash report",
                        color = NixText, fontSize = 15.sp, fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        crashReport.orEmpty().take(6000),
                        color = NixTextDim,
                        fontSize = 11.sp,
                        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp)
                            .verticalScroll(rememberScrollState())
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = { showCrashLog = false }) {
                        Text("Close", color = NixPrimary, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun ToggleSetting(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    SettingRow(title, subtitle = subtitle) {
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NixText,
                checkedTrackColor = NixPrimary,
                uncheckedThumbColor = NixTextDim,
                uncheckedTrackColor = NixSurfaceHigh,
                uncheckedBorderColor = NixStroke,
                checkedBorderColor = NixPrimary
            )
        )
    }
}
