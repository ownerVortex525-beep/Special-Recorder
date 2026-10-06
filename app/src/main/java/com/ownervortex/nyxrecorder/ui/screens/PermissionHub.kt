package com.ownervortex.nyxrecorder.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.ownervortex.nyxrecorder.ui.components.AccentBar
import com.ownervortex.nyxrecorder.ui.components.NixButton
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixAmber
import com.ownervortex.nyxrecorder.ui.theme.NixBackground
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim

private data class HubRow(
    val key: String,
    val title: String,
    val subtitle: String,
    val granted: Boolean,
    val action: () -> Unit
)

/**
 * First-launch permission setup: one screen with live green checks, a batch
 * "grant" button and per-row actions. Settings-intent rows (overlay / all-files)
 * re-check themselves whenever the app resumes.
 */
@Composable
fun PermissionHub(onDone: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var refreshTick by remember { mutableIntStateOf(0) }

    fun check(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun musicGranted(): Boolean =
        if (Build.VERSION.SDK_INT >= 33) check(Manifest.permission.READ_MEDIA_AUDIO)
        else check(Manifest.permission.READ_EXTERNAL_STORAGE)

    fun notificationsGranted(): Boolean =
        Build.VERSION.SDK_INT < 33 || check(Manifest.permission.POST_NOTIFICATIONS)

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(context)

    fun allFilesGranted(): Boolean =
        Build.VERSION.SDK_INT < 30 || android.os.Environment.isExternalStorageManager()

    fun openOverlay() = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (_: Exception) {
    }

    fun openAllFiles() = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
        } catch (_: Exception) {
        }
    }

    val runtimeMissing = buildList {
        if (!notificationsGranted()) add(Manifest.permission.POST_NOTIFICATIONS)
        if (!musicGranted()) {
            add(
                if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                else Manifest.permission.READ_EXTERNAL_STORAGE
            )
        }
        if (!check(Manifest.permission.RECORD_AUDIO)) add(Manifest.permission.RECORD_AUDIO)
        if (!check(Manifest.permission.CAMERA)) add(Manifest.permission.CAMERA)
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { refreshTick++ }

    // Re-check after returning from system settings screens. Deliberately keyed
    // only on the lifecycle owner — keying on refreshTick would re-add the
    // observer each time and re-dispatch ON_RESUME in an infinite loop.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshTick++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val rows = remember(refreshTick) {
        HubRow(
            "notifications", "Notifications",
            "Recording & export alerts",
            notificationsGranted()
        ) { runtimeLauncher.launch(arrayOf(Manifest.permission.POST_NOTIFICATIONS)) },
        HubRow(
            "overlay", "Display over apps",
            "Floating control bubble",
            overlayGranted(),
            ::openOverlay
        ),
        HubRow(
            "music", "Music & audio",
            "Pick background music",
            musicGranted()
        ) {
            runtimeLauncher.launch(
                arrayOf(
                    if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO
                    else Manifest.permission.READ_EXTERNAL_STORAGE
                )
            )
        },
        HubRow(
            "mic", "Microphone",
            "Record your voice",
            check(Manifest.permission.RECORD_AUDIO)
        ) { runtimeLauncher.launch(arrayOf(Manifest.permission.RECORD_AUDIO)) },
        HubRow(
            "camera", "Camera",
            "Face cam overlay",
            check(Manifest.permission.CAMERA)
        ) { runtimeLauncher.launch(arrayOf(Manifest.permission.CAMERA)) },
        HubRow(
            "allfiles", "All files access",
            "Save straight to /NYX Recorder (optional)",
            allFilesGranted(),
            ::openAllFiles
        )
    ) }
    val allGranted = rows.all { it.granted }

    Column(
        Modifier
            .fillMaxSize()
            .background(NixBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(28.dp))
        AccentBar()
        Spacer(Modifier.height(20.dp))
        Text(
            "Welcome to NYX-RECORDER",
            color = NixText, fontSize = 22.sp, fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Grant these once so recording, bubble controls and music work " +
                "without interruptions. You can change them anytime in Settings.",
            color = NixTextDim, fontSize = 13.sp
        )
        Spacer(Modifier.height(18.dp))

        NixCard(Modifier.fillMaxWidth()) {
            Column {
                rows.forEachIndexed { index, row ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        StatusDot(row.granted)
                        Spacer(Modifier.size(12.dp))
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
                            androidx.compose.material3.TextButton(onClick = row.action) {
                                Text(
                                    if (row.key == "overlay" || row.key == "allfiles") "Open"
                                    else "Grant",
                                    color = NixPrimary,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                    if (index != rows.lastIndex) {
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

        Spacer(Modifier.height(18.dp))
        NixButton(
            text = when {
                runtimeMissing.isNotEmpty() -> "Grant permissions"
                !overlayGranted() -> "Enable floating bubble"
                !allFilesGranted() -> "Enable all files access"
                else -> "All permissions granted"
            },
            color = when {
                allGranted -> NixAccent
                else -> NixPrimary
            },
            enabled = !(allGranted && runtimeMissing.isEmpty()),
            onClick = {
                when {
                    runtimeMissing.isNotEmpty() ->
                        runtimeLauncher.launch(runtimeMissing.toTypedArray())
                    !overlayGranted() -> openOverlay()
                    !allFilesGranted() -> openAllFiles()
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        NixButton(
            text = if (allGranted) "Continue" else "Continue anyway",
            color = NixSurfaceHigh,
            onClick = onDone,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusDot(granted: Boolean) {
    Box(
        Modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(if (granted) NixAccent.copy(alpha = 0.18f) else NixSurfaceHigh)
            .border(1.dp, if (granted) NixAccent else NixStroke, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        if (granted) {
            Icon(
                Icons.Filled.Check,
                contentDescription = "Granted",
                tint = NixAccent,
                modifier = Modifier.size(14.dp)
            )
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(NixAmber)
            )
        }
    }
}
