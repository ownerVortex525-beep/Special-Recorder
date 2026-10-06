package com.ownervortex.nyxrecorder.ui.screens

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.ownervortex.nyxrecorder.ui.components.NixButton
import com.ownervortex.nyxrecorder.ui.components.NixChip
import com.ownervortex.nyxrecorder.ui.components.SectionTitle
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixAmber
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurface
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim
import com.ownervortex.nyxrecorder.ui.viewmodel.SettingsUi
import com.ownervortex.nyxrecorder.ui.viewmodel.SettingsViewModel

/**
 * Shown every time Record is tapped: pick quality, toggle mic/audio/cam/bubble
 * and see live permission status before starting. "Start" hands off to the
 * normal consent + countdown flow.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordOptionsSheet(
    settings: SettingsUi,
    viewModel: SettingsViewModel,
    onDismiss: () -> Unit,
    onStart: () -> Unit,
    onOpenMusic: () -> Unit
) {
    val context = LocalContext.current

    fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun overlayGranted(): Boolean = Settings.canDrawOverlays(context)

    fun openOverlay() = try {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:${context.packageName}")
            )
        )
    } catch (_: Exception) {
    }

    val micGranted = granted(Manifest.permission.RECORD_AUDIO)
    val camGranted = granted(Manifest.permission.CAMERA)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = NixSurface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = NixStroke) }
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                "Start recording",
                color = NixText, fontSize = 18.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "Check your options — everything stays on this device.",
                color = NixTextDim, fontSize = 12.sp
            )

            Spacer(Modifier.height(14.dp))
            SectionTitle("QUALITY")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Auto", "1080p", "720p", "480p").forEachIndexed { index, label ->
                    NixChip(
                        label = label,
                        selected = settings.qualityPreset == index,
                        onClick = { viewModel.setQuality(index) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            SectionTitle("AUDIO & VIDEO")
            OptionToggle(
                title = "Microphone",
                subtitle = "Record your voice",
                checked = settings.recordMic,
                statusGranted = micGranted,
                statusText = if (micGranted) "✓ Granted" else "Will ask to record",
                onChange = { viewModel.setMic(it) }
            )
            if (Build.VERSION.SDK_INT >= 29) {
                OptionToggle(
                    title = "Internal audio",
                    subtitle = "App & game sound",
                    checked = settings.recordDeviceAudio,
                    statusGranted = micGranted,
                    statusText = if (micGranted) "✓ Granted" else "Will ask to record",
                    onChange = { viewModel.setDeviceAudio(it) }
                )
            }
            OptionToggle(
                title = "Face cam",
                subtitle = "Front camera window in the corner",
                checked = settings.faceCam,
                statusGranted = camGranted,
                statusText = if (camGranted) "✓ Granted" else "Will ask to record",
                onChange = { viewModel.setFaceCam(it) }
            )
            OptionToggle(
                title = "Control bubble",
                subtitle = "Floating pause / stop while recording",
                checked = settings.showBubble,
                statusGranted = overlayGranted(),
                statusText = if (overlayGranted()) "✓ Granted" else "Needs permission",
                statusAction = if (overlayGranted()) null else ::openOverlay,
                onChange = { viewModel.setBubble(it) }
            )
            OptionToggle(
                title = "Countdown",
                subtitle = "3… 2… 1… before capture starts",
                checked = settings.countdown,
                statusGranted = true,
                statusText = "",
                onChange = { viewModel.setCountdown(it) }
            )

            Spacer(Modifier.height(6.dp))
            SectionTitle("MUSIC")
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(NixSurfaceHigh)
                    .clickable { onOpenMusic() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        settings.musicTitle ?: "No background music",
                        color = NixText, fontSize = 14.sp, fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (settings.musicUri != null) "Plays on loop while recording"
                        else "Tap to pick a track",
                        color = NixTextDim, fontSize = 11.sp
                    )
                }
                Text(
                    if (settings.musicUri != null) "Change" else "Pick",
                    color = NixPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(18.dp))
            NixButton(
                text = "Start recording",
                onClick = onStart,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(18.dp))
        }
    }
}

@Composable
private fun OptionToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    statusGranted: Boolean,
    statusText: String,
    statusAction: (() -> Unit)? = null,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = NixText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                if (statusText.isNotEmpty()) {
                    Spacer(Modifier.size(8.dp))
                    if (statusGranted) {
                        StatusPill(statusText, NixAccent)
                    } else if (statusAction != null) {
                        Text(
                            statusText,
                            color = NixAmber, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
                                .background(NixAmber.copy(alpha = 0.14f))
                                .clickable { statusAction() }
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    } else {
                        StatusPill(statusText, NixAmber)
                    }
                }
            }
            Text(subtitle, color = NixTextDim, fontSize = 11.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = NixText,
                checkedTrackColor = NixPrimary,
                uncheckedThumbColor = NixTextDim,
                uncheckedTrackColor = NixSurfaceHigh,
                uncheckedBorderColor = NixStroke
            )
        )
    }
}

@Composable
private fun StatusPill(text: String, color: androidx.compose.ui.graphics.Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.size(5.dp))
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}
