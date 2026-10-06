package com.ownervortex.nyxrecorder.ui.screens

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.ownervortex.nyxrecorder.data.Recording
import com.ownervortex.nyxrecorder.export.EditFilter
import com.ownervortex.nyxrecorder.export.buildVideoEffects
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
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim
import com.ownervortex.nyxrecorder.ui.viewmodel.EditParams
import com.ownervortex.nyxrecorder.ui.viewmodel.EditorViewModel
import com.ownervortex.nyxrecorder.ui.viewmodel.ExportState
import com.ownervortex.nyxrecorder.ui.viewmodel.toExportRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val TOOL_TABS = listOf("Trim", "Speed", "Filter", "Adjust", "Crop", "Watermark")

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
        viewModel.setSourceSize(recording.width, recording.height)
        try {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(context, Uri.parse(recording.uri))
            val duration = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val w = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: 0
            val h = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: 0
            retriever.release()
            viewModel.setDuration(duration)
            if (w > 0 && h > 0) viewModel.setSourceSize(w, h)
        } catch (_: Exception) {
        }
    }

    androidx.compose.runtime.DisposableEffect(player) {
        onDispose { player.release() }
    }

    // ------------------------------------------------------- live preview
    val previewRequest = remember(params, viewModel.sourcePortrait) {
        params.toExportRequest(recording.uri, viewModel.sourcePortrait)
    }
    val previewEffects = remember(previewRequest) {
        buildVideoEffects(previewRequest, includeSpeed = false)
    }
    LaunchedEffect(previewEffects) {
        try {
            player.setVideoEffects(previewEffects)
        } catch (_: Exception) {
        }
    }
    LaunchedEffect(params.speed) {
        try {
            player.playbackParameters = PlaybackParameters(params.speed)
        } catch (_: Exception) {
        }
    }

    var startFraction by remember { mutableFloatStateOf(0f) }
    var endFraction by remember { mutableFloatStateOf(1f) }

    // --------------------------------------------------- trim thumbnails
    var thumbnails by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    LaunchedEffect(recording.id, viewModel.durationMs) {
        val dur = viewModel.durationMs
        if (dur <= 0) return@LaunchedEffect
        thumbnails = withContext(Dispatchers.IO) {
            extractThumbnails(context, recording.uri, dur)
        }
    }

    var tool by rememberSaveable { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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

        // ---------------------------------------------------------- preview
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    this.player = player
                    useController = true
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(230.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(androidx.compose.ui.graphics.Color.Black)
                .border(1.dp, NixStroke, RoundedCornerShape(16.dp))
        )
        Text(
            "Preview updates live — speed changes apply on export.",
            color = NixTextDim,
            fontSize = 11.sp,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
        )

        // -------------------------------------------------------- tool tabs
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            TOOL_TABS.forEachIndexed { index, label ->
                NixChip(
                    label = label,
                    selected = tool == index,
                    onClick = { tool = index }
                )
            }
        }

        // ------------------------------------------------------- tool panel
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Spacer(Modifier.height(10.dp))
            when (tool) {
                0 -> TrimPanel(viewModel, startFraction, endFraction,
                    { startFraction = it }, { endFraction = it }, thumbnails)
                1 -> SpeedPanel(viewModel, params)
                2 -> FilterPanel(viewModel, params)
                3 -> AdjustPanel(viewModel, params)
                4 -> CropPanel(viewModel, params)
                5 -> WatermarkPanel(viewModel, params)
            }
            Spacer(Modifier.height(16.dp))
        }

        // ----------------------------------------------------------- export
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            AccentBar(Modifier.padding(bottom = 8.dp))
            NixCard(Modifier.fillMaxWidth()) {
                ToggleRow(
                    title = "Force 720p",
                    subtitle = "Smaller file, faster export",
                    checked = params.output720,
                    onChange = { viewModel.update { p -> p.copy(output720 = it) } }
                )
            }
            Spacer(Modifier.height(8.dp))
            when (val state = export) {
                is ExportState.Idle, is ExportState.Failed -> {
                    val failed = state as? ExportState.Failed
                    if (failed != null) {
                        Text(
                            "Export failed: ${failed.reason}",
                            color = NixRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                    }
                    NixButton(
                        text = if (failed != null) "Retry export" else "Export video",
                        icon = Icons.Filled.Save,
                        onClick = { viewModel.export(recording.uri) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                is ExportState.Running -> {
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
                    Spacer(Modifier.height(8.dp))
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
                    Spacer(Modifier.height(8.dp))
                }

                is ExportState.Done -> {
                    Text(
                        "Export complete — saved to NYX Recorder",
                        color = NixAccent,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    NixButton(
                        text = "Done",
                        onClick = { onExported() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

// ------------------------------------------------------------------ panels

@Composable
private fun TrimPanel(
    viewModel: EditorViewModel,
    startFraction: Float,
    endFraction: Float,
    onStartChange: (Float) -> Unit,
    onEndChange: (Float) -> Unit,
    thumbnails: List<Bitmap>
) {
    val duration = viewModel.durationMs.toFloat()
    if (duration <= 0f) {
        Text("Reading duration…", color = NixTextDim, fontSize = 13.sp)
        return
    }
    if (thumbnails.isNotEmpty()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NixSurfaceHigh),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            thumbnails.forEach { bmp ->
                Image(
                    bitmap = bmp.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .height(52.dp)
                )
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    RangeSliderSafe(
        start = startFraction,
        end = endFraction,
        onStartChange = onStartChange,
        onEndChange = onEndChange
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
}

@Composable
private fun SpeedPanel(viewModel: EditorViewModel, params: EditParams) {
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
}

@Composable
private fun FilterPanel(viewModel: EditorViewModel, params: EditParams) {
    SectionTitle("FILTER")
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
    ) {
        EditFilter.entries.forEach { filter ->
            NixChip(
                label = filter.label,
                selected = params.filter == filter,
                onClick = { viewModel.update { it.copy(filter = filter) } }
            )
        }
    }
}

@Composable
private fun AdjustPanel(viewModel: EditorViewModel, params: EditParams) {
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
}

@Composable
private fun CropPanel(viewModel: EditorViewModel, params: EditParams) {
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
    Spacer(Modifier.height(6.dp))
    Text(
        "Crop applies to the exported video (and the preview above).",
        color = NixTextDim,
        fontSize = 11.sp
    )
}

@Composable
private fun WatermarkPanel(viewModel: EditorViewModel, params: EditParams) {
    SectionTitle("WATERMARK")
    OutlinedTextField(
        value = params.watermarkText,
        onValueChange = { value ->
            viewModel.update { it.copy(watermarkText = value.take(40)) }
        },
        label = { Text("Watermark text", color = NixTextDim) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        textStyle = LocalTextStyle.current.copy(color = NixText, fontSize = 14.sp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = NixPrimary,
            unfocusedBorderColor = NixStroke,
            focusedTextColor = NixText,
            unfocusedTextColor = NixText,
            focusedLabelColor = NixPrimary,
            unfocusedLabelColor = NixTextDim
        )
    )
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf("Top-L", "Top-R", "Bot-L", "Bot-R").forEachIndexed { index, label ->
            NixChip(
                label = label,
                selected = params.watermarkCorner == index,
                onClick = { viewModel.update { it.copy(watermarkCorner = index) } }
            )
        }
    }
    Spacer(Modifier.height(6.dp))
    Text(
        if (params.watermarkText.isBlank())
            "Type text to stamp your video in the chosen corner."
        else
            "Watermark appears in the preview and in the export.",
        color = NixTextDim,
        fontSize = 11.sp
    )
}

// ----------------------------------------------------------------- helpers

private fun extractThumbnails(
    context: Context,
    uri: String,
    durationMs: Long,
    count: Int = 8
): List<Bitmap> {
    val out = mutableListOf<Bitmap>()
    if (durationMs <= 0 || count < 2) return out
    val retriever = MediaMetadataRetriever()
    return try {
        retriever.setDataSource(context, Uri.parse(uri))
        for (i in 0 until count) {
            val timeUs = (durationMs * i / (count - 1)) * 1000L
            val frame = retriever.getFrameAtTime(
                timeUs, MediaMetadataRetriever.OPTION_CLOSEST
            ) ?: continue
            val targetH = 72
            val targetW = (frame.width.toFloat() / frame.height * targetH)
                .toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(frame, targetW, targetH, true)
            out += scaled
            if (scaled !== frame) frame.recycle()
        }
        out
    } catch (_: Exception) {
        out
    } finally {
        try {
            retriever.release()
        } catch (_: Exception) {
        }
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
