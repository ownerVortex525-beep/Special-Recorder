package com.ownervortex.nyxrecorder.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ownervortex.nyxrecorder.data.Recording
import com.ownervortex.nyxrecorder.export.EditFilter
import com.ownervortex.nyxrecorder.ui.components.AccentBar
import com.ownervortex.nyxrecorder.ui.components.NixButton
import com.ownervortex.nyxrecorder.ui.components.NixCard
import com.ownervortex.nyxrecorder.ui.components.NixChip
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
import com.ownervortex.nyxrecorder.ui.viewmodel.EditParams
import com.ownervortex.nyxrecorder.ui.viewmodel.EditorViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.ExportState

@Composable
fun EditorScreen(
    recording: Recording,
    viewModel: EditorViewModel,
    onBack: () -> Unit,
    onExported: () -> Unit
) {
    val context = LocalContext.current
    val params by viewModel.params.collectAsState()
    val export by viewModel.export.collectAsState()

    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(recording.uri)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(recording.id) {
        viewModel.reset()
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(recording.uri))
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            retriever.release()
            viewModel.setDuration(duration)
        } catch (_: Exception) {
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    var startFraction by remember { mutableFloatStateOf(0f) }
    var endFraction by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(export) {
        if (export is ExportState.Done) onExported()
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = NixText)
            }
            Text(
                "Editor",
                color = NixText,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (export is ExportState.Running) {
                IconButton(onClick = { viewModel.cancelExport() }) {
                    Icon(Icons.Filled.Close, contentDescription = "Cancel export", tint = NixRed)
                }
            }
        }
        AccentBar()
        Spacer(Modifier.height(12.dp))

        // ---------------------------------------------------------- preview
        AndroidView(
            factory = { ctx ->
                val exo = player
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
                .border(1.dp, NixStroke, RoundedCornerShape(16.dp))
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Preview shows the original clip; filters, speed and crop are applied on export.",
            color = NixTextDim,
            fontSize = 11.sp
        )

        // ------------------------------------------------------------- trim
        Spacer(Modifier.height(14.dp))
        SectionTitle("TRIM")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                if (viewModel.durationMs > 0) {
                    val duration = viewModel.durationMs.toFloat()
                    RangeSliderSafe(
                        start = startFraction,
                        end = endFraction,
                        onStartChange = { startFraction = it },
                        onEndChange = { endFraction = it }
                    )
                    Row(Modifier.fillMaxWidth()) {
                        Text(
                            com.ownervortex.nyxrecorder.core.util.DeviceHealth
                                .formatDuration((startFraction * duration).toLong()),
                            color = NixAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            com.ownervortex.nyxrecorder.core.util.DeviceHealth
                                .formatDuration((endFraction * duration).toLong()),
                            color = NixAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold
                        )
                    }
                    LaunchedEffect(startFraction, endFraction) {
                        viewModel.update {
                            it.copy(
                                trimStartMs = (startFraction * duration).toLong(),
                                trimEndMs = (endFraction * duration).toLong()
                            )
                        }
                    }
                } else {
                    Text("Reading duration…", color = NixTextDim, fontSize = 13.sp)
                }
            }
        }

        // ----------------------------------------------------------- speed
        Spacer(Modifier.height(14.dp))
        SectionTitle("SPEED")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(0.5f, 0.75f, 1f, 1.5f, 2f).forEach { speed ->
                NixChip(
                    label = "${speed}x",
                    selected = params.speed == speed,
                    onClick = { viewModel.update { it.copy(speed = speed) } }
                )
            }
        }

        // ---------------------------------------------------------- filter
        Spacer(Modifier.height(14.dp))
        SectionTitle("FILTER")
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            EditFilter.entries.forEachIndexed { index, filter ->
                if (index in 0..3) {
                    NixChip(
                        label = filter.label,
                        selected = params.filter == filter,
                        onClick = { viewModel.update { it.copy(filter = filter) } }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            EditFilter.entries.forEachIndexed { index, filter ->
                if (index > 3) {
                    NixChip(
                        label = filter.label,
                        selected = params.filter == filter,
                        onClick = { viewModel.update { it.copy(filter = filter) } }
                    )
                }
            }
        }

        // ------------------------------------------------------- adjustment
        Spacer(Modifier.height(14.dp))
        SectionTitle("ADJUST")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                SliderRow(
                    label = "Brightness",
                    value = params.brightness,
                    valueRange = -0.4f..0.4f,
                    onChange = { viewModel.update { p -> p.copy(brightness = it) } }
                )
                SliderRow(
                    label = "Contrast",
                    value = params.contrast,
                    valueRange = 0.6f..1.6f,
                    onChange = { viewModel.update { p -> p.copy(contrast = it) } }
                )
            }
        }

        // ------------------------------------------------------------- crop
        Spacer(Modifier.height(14.dp))
        SectionTitle("CROP")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("None", "4:3", "1:1", "9:16").forEachIndexed { index, label ->
                NixChip(
                    label = label,
                    selected = params.cropPreset == index,
                    onClick = { viewModel.update { it.copy(cropPreset = index) } }
                )
            }
        }

        // ----------------------------------------------------------- output
        Spacer(Modifier.height(14.dp))
        SectionTitle("OUTPUT")
        NixCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(vertical = 2.dp)) {
                ToggleRow(
                    title = "Force 720p",
                    subtitle = "Smaller file, faster export",
                    checked = params.output720,
                    onChange = { viewModel.update { p -> p.copy(output720 = it) } }
                )
                ToggleRow(
                    title = "Watermark",
                    subtitle = "Adds “NYX” in the corner",
                    checked = params.watermark,
                    onChange = { viewModel.update { p -> p.copy(watermark = it) } }
                )
            }
        }

        // ----------------------------------------------------------- export
        Spacer(Modifier.height(20.dp))
        when (val state = export) {
            is ExportState.Idle -> {
                NixButton(
                    text = "Export video",
                    icon = Icons.Filled.Save,
                    onClick = { viewModel.export(recording.uri) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            is ExportState.Running -> {
                NixCard(Modifier.fillMaxWidth(), accent = NixPrimary) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "Exporting…",
                                color = NixText,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                "${state.progress}%",
                                color = NixPrimary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                        }
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(NixSurfaceHigh)
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((state.progress / 100f).coerceIn(0f, 1f))
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(NixPrimary)
                            )
                        }
                    }
                }
            }
            is ExportState.Done -> {
                NixCard(Modifier.fillMaxWidth(), accent = NixAccent) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Export complete", color = NixAccent, fontWeight = FontWeight.Bold)
                        Text(
                            "Saved to Movies/NYXRecorder",
                            color = NixTextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
            is ExportState.Failed -> {
                NixCard(Modifier.fillMaxWidth(), accent = NixRed) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Export failed", color = NixRed, fontWeight = FontWeight.Bold)
                        Text(
                            state.reason,
                            color = NixTextDim,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        Spacer(Modifier.height(10.dp))
                        NixButton(
                            text = "Retry",
                            onClick = { viewModel.export(recording.uri) },
                            color = NixRed
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(28.dp))
    }
}

@Composable
private fun RangeSliderSafe(
    start: Float,
    end: Float,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit
) {
    androidx.compose.material3.RangeSlider(
        value = start..end,
        onValueChange = { range ->
            onStartChange(range.start)
            onEndChange(range.endInclusive)
        },
        valueRange = 0f..1f,
        colors = SliderDefaults.colors(
            thumbColor = NixPrimary,
            activeTrackColor = NixPrimary,
            inactiveTrackColor = NixSurfaceHigh
        )
    )
}

@Composable
private fun SliderRow(
    label: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = NixTextDim, fontSize = 13.sp, modifier = Modifier.width(84.dp))
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = valueRange,
            modifier = Modifier.weight(1f),
            colors = SliderDefaults.colors(
                thumbColor = NixAmber,
                activeTrackColor = NixAmber,
                inactiveTrackColor = NixSurfaceHigh
            )
        )
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = NixText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(subtitle, color = NixTextDim, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
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
