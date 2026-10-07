package com.ownervortex.nyxrecorder.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownervortex.nyxrecorder.core.recording.RecordingUiState
import com.ownervortex.nyxrecorder.core.util.DeviceHealth
import com.ownervortex.nyxrecorder.ui.components.AccentBar
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.components.PulseDot
import com.ownervortex.nyxrecorder.ui.components.SectionTitle
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixAmber
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixRed
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurface
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim

@Composable
fun HomeScreen(
    status: RecordingUiState,
    qualityLabel: String,
    micOn: Boolean,
    deviceAudioOn: Boolean,
    bubbleOn: Boolean,
    freeSpaceLabel: String,
    onRecord: () -> Unit,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit,
    countdownValue: Int
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        val homeWatermark = remember {
            com.ownervortex.nyxrecorder.data.SettingsStore.homeLabel
                ?.takeIf { it.isNotBlank() } ?: "CYBER-FORCE"
        }
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            Text(
                homeWatermark,
                color = NixAccent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 2.sp
            )
        }
        Spacer(Modifier.height(4.dp))
        AccentBar()
        Spacer(Modifier.height(20.dp))

        if (status.isRecording) {
            ActiveRecordingCard(status, onPauseToggle, onStop)
        } else {
            RecordButton(onRecord, countdownValue)
        }

        Spacer(Modifier.height(24.dp))
        SectionTitle("QUICK SETTINGS")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                QuickRow("Quality", qualityLabel)
                QuickRow("Microphone", if (micOn) "On" else "Off", if (micOn) NixAccent else NixTextDim)
                QuickRow("Internal audio", if (deviceAudioOn) "On" else "Off", if (deviceAudioOn) NixAccent else NixTextDim)
                QuickRow("Control bubble", if (bubbleOn) "On" else "Off", if (bubbleOn) NixAccent else NixTextDim)
            }
        }

        Spacer(Modifier.height(16.dp))
        SectionTitle("DEVICE")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 4.dp)) {
                QuickRow("Free storage", freeSpaceLabel, if (freeSpaceLabel.startsWith("Low")) NixRed else NixText)
                QuickRow(
                    "Output folder",
                    if (android.os.Build.VERSION.SDK_INT >= 30 &&
                        android.os.Environment.isExternalStorageManager()
                    ) {
                        "NYX Recorder"
                    } else {
                        "Movies/NYX Recorder"
                    }
                )
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Tip: after tapping record, Android asks for screen-capture consent. " +
                "Your audio, pause and stop controls stay in the floating bubble and notification.",
            color = NixTextDim,
            fontSize = 12.sp,
            lineHeight = 17.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 28.dp)
        )
    }
}

@Composable
private fun RecordButton(onRecord: () -> Unit, countdownValue: Int) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        val visible = countdownValue <= 0
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn() + scaleIn(initialScale = 0.6f),
            exit = fadeOut()
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(172.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(NixAccent.copy(alpha = 0.35f), NixAccent.copy(alpha = 0.05f))
                            )
                        )
                        .clickable(onClick = onRecord),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(122.dp)
                            .clip(CircleShape)
                            .background(
                                Brush.linearGradient(listOf(NixAccent, NixAccent.copy(alpha = 0.72f)))
                            )
                            .border(3.dp, NixAccent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "REC",
                            color = androidx.compose.ui.graphics.Color.White,
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 3.sp
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "Tap to start recording",
                    color = NixTextDim,
                    fontSize = 14.sp
                )
            }
        }
        if (countdownValue > 0) {
            Text(
                "$countdownValue",
                color = NixAmber,
                fontSize = 96.sp,
                fontWeight = FontWeight.Black
            )
        }
    }
}

@Composable
private fun ActiveRecordingCard(
    status: RecordingUiState,
    onPauseToggle: () -> Unit,
    onStop: () -> Unit
) {
    NixCard(Modifier.fillMaxWidth(), accent = NixRed.copy(alpha = 0.6f)) {
        Column(
            Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PulseDot(if (status.isPaused) NixAmber else NixRed)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (status.isPaused) "PAUSED" else "RECORDING",
                    color = if (status.isPaused) NixAmber else NixRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                DeviceHealth.formatDuration(status.elapsedMs),
                color = NixText,
                fontSize = 44.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "${status.qualityLabel} • ${status.fileName}",
                color = NixTextDim,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(18.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NixSurfaceHigh)
                        .border(1.dp, NixStroke, CircleShape)
                        .clickable(onClick = onPauseToggle),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (status.isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                        contentDescription = if (status.isPaused) "Resume" else "Pause",
                        tint = if (status.isPaused) NixAccent else NixAmber,
                        modifier = Modifier.size(28.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(NixRed.copy(alpha = 0.18f))
                        .border(1.dp, NixRed, CircleShape)
                        .clickable(onClick = onStop),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Stop,
                        contentDescription = "Stop",
                        tint = NixRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickRow(label: String, value: String, valueColor: androidx.compose.ui.graphics.Color = NixText) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NixTextDim, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}
