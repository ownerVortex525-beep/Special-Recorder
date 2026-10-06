package com.ownervortex.nyxrecorder.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.ownervortex.nyxrecorder.data.Recording
import com.ownervortex.nyxrecorder.data.ThumbnailCache
import com.ownervortex.nyxrecorder.ui.components.EmptyState
import com.ownervortex.nyxrecorder.ui.components.SectionTitle
import com.ownervortex.nyxrecorder.ui.theme.NixPrimary
import com.ownervortex.nyxrecorder.ui.theme.NixRed
import com.ownervortex.nyxrecorder.ui.theme.NixStroke
import com.ownervortex.nyxrecorder.ui.theme.NixSurface
import com.ownervortex.nyxrecorder.ui.theme.NixSurfaceHigh
import com.ownervortex.nyxrecorder.ui.theme.NixText
import com.ownervortex.nyxrecorder.ui.theme.NixTextDim

@Composable
fun LibraryScreen(
    items: List<Recording>,
    loading: Boolean,
    onPlay: (Recording) -> Unit,
    onEdit: (Recording) -> Unit,
    onShare: (Recording) -> Unit,
    onDelete: (Recording) -> Unit,
    onRename: (Recording, String) -> Unit,
    onFavorite: (Recording) -> Unit
) {
    var renameTarget by remember { mutableStateOf<Recording?>(null) }

    Column(Modifier.fillMaxSize()) {
        SectionTitle("LIBRARY")

        if (items.isEmpty() && !loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(
                    Icons.Filled.Movie,
                    "No recordings yet",
                    "Start your first recording from the Home tab — it will appear here instantly."
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 20.dp, end = 20.dp, bottom = 24.dp
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items, key = { it.id }) { recording ->
                    RecordingCard(
                        recording = recording,
                        onPlay = { onPlay(recording) },
                        onEdit = { onEdit(recording) },
                        onShare = { onShare(recording) },
                        onDelete = { onDelete(recording) },
                        onRename = { renameTarget = recording },
                        onFavorite = { onFavorite(recording) }
                    )
                }
            }
        }
    }

    renameTarget?.let { target ->
        RenameDialog(
            initial = target.displayName,
            onDismiss = { renameTarget = null },
            onConfirm = { name ->
                renameTarget = null
                if (name.isNotBlank()) onRename(target, name)
            }
        )
    }
}

@Composable
private fun RecordingCard(
    recording: Recording,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    onFavorite: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NixSurface)
            .border(1.dp, NixStroke, RoundedCornerShape(16.dp))
            .clickable(onClick = onPlay)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Thumbnail(recording)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                recording.displayName,
                color = NixText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    append(recording.resolutionLabel)
                    if (recording.durationMs > 0) {
                        if (isNotEmpty()) append(" • ")
                        append(com.ownervortex.nyxrecorder.core.util.DeviceHealth.formatDuration(recording.durationMs))
                    }
                    append(" • ")
                    append(
                        com.ownervortex.nyxrecorder.core.util.DeviceHealth.formatBytes(recording.sizeBytes)
                    )
                },
                color = NixTextDim,
                fontSize = 12.sp,
                maxLines = 1
            )
            if (recording.isFavorite) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "♥ Favorite",
                    color = NixRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Actions", tint = NixTextDim)
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Filled.PlayArrow, null) },
                    onClick = { menuOpen = false; onPlay() }
                )
                DropdownMenuItem(
                    text = { Text("Edit") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menuOpen = false; onEdit() }
                )
                DropdownMenuItem(
                    text = { Text(if (recording.isFavorite) "Unfavorite" else "Favorite") },
                    leadingIcon = {
                        Icon(
                            if (recording.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            null,
                            tint = NixRed
                        )
                    },
                    onClick = { menuOpen = false; onFavorite() }
                )
                DropdownMenuItem(
                    text = { Text("Rename") },
                    leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { menuOpen = false; onRename() }
                )
                DropdownMenuItem(
                    text = { Text("Share") },
                    leadingIcon = { Icon(Icons.Filled.Share, null) },
                    onClick = { menuOpen = false; onShare() }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = NixRed) },
                    leadingIcon = { Icon(Icons.Filled.Delete, null, tint = NixRed) },
                    onClick = { menuOpen = false; onDelete() }
                )
            }
        }
    }
}

@Composable
private fun Thumbnail(recording: Recording) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bitmap by produceState<Bitmap?>(initialValue = null, key1 = recording.id) {
        value = ThumbnailCache.get(context, recording.uri, recording.id)
    }
    Box(
        modifier = Modifier
            .size(width = 104.dp, height = 62.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(NixSurfaceHigh),
        contentAlignment = Alignment.Center
    ) {
        val bmp = bitmap
        if (bmp != null) {
            Image(
                bmp.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Icon(Icons.Filled.Movie, null, tint = NixTextDim, modifier = Modifier.size(26.dp))
        }
        Icon(
            Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = NixPrimary,
            modifier = Modifier
                .size(24.dp)
                .align(Alignment.Center)
                .padding(2.dp)
        )
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial.substringBeforeLast('.')) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(NixSurface)
                .border(1.dp, NixStroke, RoundedCornerShape(18.dp))
                .padding(20.dp)
        ) {
            Text("Rename recording", color = NixText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            androidx.compose.material3.OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NixPrimary,
                    unfocusedBorderColor = NixStroke,
                    focusedTextColor = NixText,
                    unfocusedTextColor = NixText,
                    cursorColor = NixPrimary
                )
            )
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = NixTextDim) }
                TextButton(onClick = { onConfirm(text.trim()) }) {
                    Text("Save", color = NixPrimary, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
