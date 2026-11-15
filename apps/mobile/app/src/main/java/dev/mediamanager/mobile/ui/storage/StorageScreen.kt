package dev.mediamanager.mobile.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.storage.SizeRow
import dev.mediamanager.mobile.storage.StorageSnapshot
import dev.mediamanager.mobile.storage.usedFraction
import dev.mediamanager.mobile.storage.share
import dev.mediamanager.mobile.ui.components.ConfirmDialog
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatBytes
import dev.mediamanager.mobile.ui.format.formatShare
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun StorageScreen(container: AppContainer, player: PlayerRepository) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val temp = remember { MutableStateFlow(0L) }
    val covers = remember { MutableStateFlow(0L) }
    val snapshot by container.storage.observe(temp, covers).collectAsStateWithLifecycle(initialValue = StorageSnapshot())
    val largest by container.storage.largestItems(50).collectAsStateWithLifecycle(initialValue = emptyList<SizeRow>())
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var recheckMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snapshot.tempBytes, snapshot.coversBytes) {
        temp.value = container.storage.measureTemp()
        covers.value = container.storage.measureCovers()
    }

    val deviceUsedFraction = usedFraction(snapshot.deviceTotal, snapshot.deviceFree)

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Library", color = colors.muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                Text(
                    formatBytes(snapshot.libraryBytes),
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { deviceUsedFraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = colors.accent,
                    trackColor = colors.panel2,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "${formatBytes(snapshot.deviceTotal - snapshot.deviceFree)} used of " +
                        "${formatBytes(snapshot.deviceTotal)} · ${formatBytes(snapshot.deviceFree)} free",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
        }

        item { SectionTitle("Breakdown") }
        item {
            BreakdownRow("Audio", snapshot.audioBytes, snapshot.audioCount, snapshot.libraryBytes, Icons.Default.MusicNote)
            BreakdownRow("Video", snapshot.videoBytes, snapshot.videoCount, snapshot.libraryBytes, Icons.Default.Videocam)
            BreakdownRow("Thumbnails", snapshot.thumbBytes, 0, snapshot.libraryBytes, Icons.Default.Image)
            BreakdownRow("Covers", snapshot.coversBytes, 0, snapshot.libraryBytes, Icons.Default.PhotoLibrary)
            BreakdownRow("Temp", snapshot.tempBytes, 0, snapshot.libraryBytes, Icons.Default.Schedule)
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Largest items")
                Spacer(Modifier.weight(1f))
                MmTextButton("Re-check", Icons.Default.Refresh, onClick = {
                    scope.launch {
                        val result = container.itemRepository.recheck()
                        temp.value = container.storage.measureTemp()
                        covers.value = container.storage.measureCovers()
                        recheckMessage = when {
                            result.missing.isEmpty() && result.resized.isEmpty() -> "Everything checks out"
                            result.missing.isEmpty() -> "${result.resized.size} file sizes corrected"
                            else -> "${result.missing.size} files missing on disk"
                        }
                    }
                })
            }
            recheckMessage?.let {
                Text(it, color = colors.muted, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }

        if (largest.isEmpty()) {
            item { Text("No media yet.", color = colors.muted) }
        }
        items(largest, key = { it.item.id }) { row ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .clickable { player.playItems(largest.map { it.item }, largest.indexOf(row)) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(row.item, container.appContext.filesDir, Modifier.height(48.dp).fillMaxWidth(0.18f))
                Spacer(Modifier.height(4.dp).fillMaxWidth(0.03f))
                Column(Modifier.weight(1f).padding(start = 12.dp)) {
                    Text(
                        row.item.title,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "${formatBytes(row.item.sizeBytes)} · ${formatShare(row.share)}" +
                            if (row.item.available) "" else " · missing",
                        color = if (row.item.available) colors.muted else colors.danger,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
                MmTextButton("Delete", Icons.Default.Delete, color = colors.danger, onClick = { pendingDelete = row.item })
            }
        }
        item { Spacer(Modifier.height(32.dp)) }
    }

    pendingDelete?.let { item ->
        ConfirmDialog(
            title = "Delete ${item.title}?",
            body = "Frees ${formatBytes(item.sizeBytes)}. The file and its playlist entries go.",
            confirmLabel = "Delete",
            onConfirm = {
                scope.launch {
                    player.onItemDeleted(item.id)
                    container.itemRepository.delete(item.id)
                    temp.value = container.storage.measureTemp()
                }
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

@Composable
private fun BreakdownRow(
    label: String,
    bytes: Long,
    count: Int,
    whole: Long,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = colors.muted, modifier = Modifier.size(16.dp).padding(end = 6.dp))
        Text(
            if (count > 0) "$label ($count)" else label,
            color = colors.ink,
            modifier = Modifier.width(104.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            Modifier
                .weight(1f)
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(colors.panel2),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(share(bytes, whole).coerceIn(0f, 1f))
                    .fillMaxHeight()
                    .background(colors.accent),
            )
        }
        Text(
            buildString {
                append(formatBytes(bytes))
                if (count > 0) append(" · ").append(count)
                append(" · ").append(formatShare(share(bytes, whole)))
            },
            color = colors.muted,
            modifier = Modifier.width(120.dp).padding(start = 12.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.End,
            maxLines = 1,
        )
    }
}
