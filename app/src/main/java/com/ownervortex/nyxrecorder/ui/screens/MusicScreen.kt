package com.ownervortex.nyxrecorder.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ownervortex.nyxrecorder.core.util.DeviceHealth
import com.ownervortex.nyxrecorder.ui.components.AccentBar
import com.ownervortex.nyxrecorder.ui.components.EmptyState
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.components.SectionTitle
import com.ownervortex.nyxrecorder.ui.theme.NixAccent
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim
import com.ownervortex.nyxrecorder.ui.viewmodel.MusicViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.SettingsViewModel

@Composable
fun MusicScreen(
    musicViewModel: MusicViewModel,
    settingsViewModel: SettingsViewModel
) {
    val tracks by musicViewModel.tracks.collectAsState()
    val loading by musicViewModel.loading.collectAsState()
    val selected by musicViewModel.selected.collectAsState()
    val settings by settingsViewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        if (musicViewModel.hasPermission()) musicViewModel.load()
    }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("BACKGROUND MUSIC")

        NixCard(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.MusicNote, null, tint = NixPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            settings.musicTitle ?: "No music selected",
                            color = NixText,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "Mixed into new recordings at the set volume",
                            color = NixTextDim,
                            fontSize = 11.sp
                        )
                    }
                    if (settings.musicUri != null) {
                        IconButton(onClick = { settingsViewModel.clearMusic() }) {
                            Icon(Icons.Filled.Clear, "Remove music", tint = NixTextDim)
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Volume", color = NixTextDim, fontSize = 13.sp, modifier = Modifier.width(70.dp))
                    Slider(
                        value = settings.musicVolume,
                        onValueChange = { settingsViewModel.setMusicVolume(it) },
                        valueRange = 0f..1f,
                        modifier = Modifier.weight(1f),
                        colors = SliderDefaults.colors(
                            thumbColor = NixAccent,
                            activeTrackColor = NixAccent,
                            inactiveTrackColor = NixSurfaceHigh
                        )
                    )
                    Text(
                        "${(settings.musicVolume * 100).toInt()}%",
                        color = NixText,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.width(44.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))
        AccentBar(Modifier.padding(horizontal = 20.dp))

        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Filled.Person,
                    if (loading) "Loading music…" else "No music found",
                    if (loading) "Scanning your library" else "Grant music access in Settings or pick tracks stored on this device."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, top = 8.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(tracks, key = { it.id }) { track ->
                    val isSelected = track.uri == selected
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isSelected) NixPrimary.copy(alpha = 0.15f) else NixSurfaceHigh)
                            .border(
                                1.dp,
                                if (isSelected) NixPrimary else NixStroke,
                                RoundedCornerShape(14.dp)
                            )
                            .clickable { musicViewModel.select(if (isSelected) null else track) }
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) NixPrimary else NixStroke),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.MusicNote,
                                contentDescription = null,
                                tint = NixText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                track.title,
                                color = NixText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1
                            )
                            Text(
                                "${track.artist} • ${DeviceHealth.formatDuration(track.durationMs)}",
                                color = NixTextDim,
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                        if (isSelected) {
                            Icon(Icons.Filled.CheckCircle, "Selected", tint = NixPrimary)
                        }
                    }
                }
            }
        }
    }
}
