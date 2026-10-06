package com.ownervortex.nyxrecorder.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.runtime.mutableIntStateOf
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
    var permTick by remember { mutableIntStateOf(0) }

    val permLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { permTick++ }

    fun granted(permission: String): Boolean =
        androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                allFilesGranted = Build.VERSION.SDK_INT >= 30 &&
                    android.os.Environment.isExternalStorageManager()
                crashReport =
                    com.ownervortex.nyxrecorder.core.util.CrashLog.read(context)
                permTick++
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

        Spacer(Modifier.height(12.dp))
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text(
                    "Floating bubble",
                    color = NixText, fontSize = 15.sp, fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Size and on-screen opacity — applies to the next recording",
                    color = NixTextDim, fontSize = 12.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Size", color = NixText, fontSize = 14.sp)
                    Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                        listOf("S", "M", "L").forEachIndexed { index, label ->
                            NixChip(
                                label = label,
                                selected = state.bubbleSize == index,
                                onClick = { viewModel.setBubbleSize(index) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Opacity", color = NixText, fontSize = 14.sp)
                    Text(
                        "${(state.bubbleOpacity * 100).toInt()}%",
                        color = NixAccent, fontSize = 13.sp, fontWeight = FontWeight.Bold
                    )
                }
                androidx.compose.material3.Slider(
                    value = state.bubbleOpacity,
                    onValueChange = { viewModel.setBubbleOpacity(it) },
                    valueRange = 0.10f..1f,
                    colors = androidx.compose.material3.SliderDefaults.colors(
                        thumbColor = NixPrimary,
                        activeTrackColor = NixPrimary,
                        inactiveTrackColor = NixSurfaceHigh
                    )
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle("PERMISSIONS")
        val permissionRows = remember(permTick) {
            listOf(
                PermissionRow(
                    "Notifications",
                    "Recording & export alerts",
                    Build.VERSION.SDK_INT < 33 ||
                        granted(android.Manifest.permission.POST_NOTIFICATIONS),
                    { permLauncher.launch(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS)) }
                ),
                PermissionRow(
                    "Display over apps",
                    "Required for the control bubble",
                    android.provider.Settings.canDrawOverlays(context),
                    {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                        }
                    },
                    grantLabel = "Open"
                ),
                PermissionRow(
                    "Music & audio",
                    "Pick background music",
                    if (Build.VERSION.SDK_INT >= 33) granted(android.Manifest.permission.READ_MEDIA_AUDIO)
                    else granted(android.Manifest.permission.READ_EXTERNAL_STORAGE),
                    { onRequestMusicPermission() }
                ),
                PermissionRow(
                    "Microphone",
                    "Record your voice",
                    granted(android.Manifest.permission.RECORD_AUDIO),
                    { permLauncher.launch(arrayOf(android.Manifest.permission.RECORD_AUDIO)) }
                ),
                PermissionRow(
                    "Camera",
                    "Face cam window",
                    granted(android.Manifest.permission.CAMERA),
                    { permLauncher.launch(arrayOf(android.Manifest.permission.CAMERA)) }
                ),
                PermissionRow(
                    "Modify system settings",
                    "For the touch indicator",
                    Build.VERSION.SDK_INT < 23 ||
                        android.provider.Settings.System.canWrite(context),
                    {
                        try {
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_MANAGE_WRITE_SETTINGS,
                                    Uri.parse("package:${context.packageName}")
                                )
                            )
                        } catch (_: Exception) {
                        }
                    },
                    grantLabel = "Open"
                )
            )
        }
        val allPermsGranted = permissionRows.all { it.granted }
        NixCard(Modifier.fillMaxWidth()) {
            Column {
                if (allPermsGranted) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "✓",
                            color = NixAccent, fontSize = 16.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "All permissions granted",
                            color = NixAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                permissionRows.forEachIndexed { index, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                row.title,
                                color = NixText, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                row.subtitle,
                                color = NixTextDim, fontSize = 11.sp,
                                modifier = Modifier.padding(top = 1.dp)
                            )
                        }
                        if (row.granted) {
                            Text(
                                "✓ Granted",
                                color = NixAccent, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        } else {
                            TextButton(onClick = row.action) {
                                Text(
                                    row.grantLabel,
                                    color = NixPrimary, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    if (index != permissionRows.lastIndex) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height(1.dp)
                                .background(NixStroke)
                        )
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

private data class PermissionRow(
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val action: () -> Unit,
    val grantLabel: String = "Grant"
)
