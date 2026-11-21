package dev.mediamanager.mobile.ui.storage

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.storage.SizeRow
import dev.mediamanager.mobile.storage.StorageSnapshot
import dev.mediamanager.mobile.storage.share
import dev.mediamanager.mobile.storage.usedFraction
import dev.mediamanager.mobile.ui.components.ConfirmDialog
import dev.mediamanager.mobile.ui.components.MmSurface
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatBytes
import dev.mediamanager.mobile.ui.format.formatShare
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@Composable
fun StorageScreen(container: AppContainer, player: PlayerRepository, onClose: () -> Unit = {}) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val temp = remember { MutableStateFlow(0L) }
    val covers = remember { MutableStateFlow(0L) }
    val snapshot by container.storage.observe(temp, covers)
        .collectAsStateWithLifecycle(initialValue = StorageSnapshot())
    val largest by container.storage.largestItems(50)
        .collectAsStateWithLifecycle(initialValue = emptyList<SizeRow>())
    var pendingDelete by remember { mutableStateOf<Item?>(null) }
    var recheckMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(snapshot.tempBytes, snapshot.coversBytes) {
        temp.value = container.storage.measureTemp()
        covers.value = container.storage.measureCovers()
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MmTextButton("Back", Icons.Default.ArrowBack, color = colors.muted, onClick = onClose)
                Spacer(Modifier.weight(1f))
                Text(
                    "Storage",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(64.dp))
            }
        }

        item {
            LibraryCard(snapshot)
        }

        item {
            SectionTitle("What is using it")
            MmSurface(Modifier.fillMaxWidth()) {
                StackedBar(snapshot)
                Spacer(Modifier.height(12.dp))
                LegendRow("Audio", snapshot.audioBytes, snapshot.libraryBytes, colors.accent)
                LegendRow("Video", snapshot.videoBytes, snapshot.libraryBytes, colors.accent.copy(alpha = 0.75f))
                LegendRow("Thumbnails", snapshot.thumbBytes, snapshot.libraryBytes, colors.accent.copy(alpha = 0.5f))
                LegendRow("Covers", snapshot.coversBytes, snapshot.libraryBytes, colors.accent.copy(alpha = 0.32f))
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
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
                Text(
                    it,
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (largest.isEmpty()) {
            item { Text("No media yet.", color = colors.muted, modifier = Modifier.padding(vertical = 8.dp)) }
        } else {
            item {
                MmSurface(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                    largest.forEachIndexed { index, row ->
                        LargestRow(
                            row = row,
                            filesDir = container.appContext.filesDir,
                            onPlay = { player.playItems(largest.map { it.item }, index) },
                            onDelete = { pendingDelete = row.item },
                        )
                        if (index != largest.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                color = colors.line,
                                modifier = Modifier.padding(start = 64.dp),
                            )
                        }
                    }
                }
            }
        }
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
private fun LibraryCard(snapshot: StorageSnapshot) {
    val colors = LocalAppColors.current
    val fraction = usedFraction(snapshot.deviceTotal, snapshot.deviceFree)
    MmSurface(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text(
            "LIBRARY",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Text(
            formatBytes(snapshot.libraryBytes),
            color = colors.ink,
            style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
        )
        Text(
            "${snapshot.itemCount} item${if (snapshot.itemCount == 1) "" else "s"}",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
        Spacer(Modifier.height(16.dp))
        dev.mediamanager.mobile.ui.components.MmProgressBar(
            progress = fraction,
            modifier = Modifier.fillMaxWidth(),
            height = 8.dp,
            trackColor = colors.panel2,
            fillColor = colors.accent,
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

/** One bar, proportional segments: the breakdown at a glance before the numbers. */
@Composable
private fun StackedBar(snapshot: StorageSnapshot) {
    val colors = LocalAppColors.current
    val total = snapshot.libraryBytes.coerceAtLeast(1)
    val parts = listOf(
        snapshot.audioBytes to colors.accent,
        snapshot.videoBytes to colors.accent.copy(alpha = 0.75f),
        snapshot.thumbBytes to colors.accent.copy(alpha = 0.5f),
        snapshot.coversBytes to colors.accent.copy(alpha = 0.32f),
    ).filter { it.first > 0 }

    Row(
        Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(colors.panel2),
    ) {
        if (parts.isEmpty()) {
            Box(Modifier.fillMaxWidth().height(10.dp).background(colors.panel2))
        } else {
            parts.forEach { (bytes, color) ->
                Box(Modifier.weight(bytes.toFloat() / total).height(10.dp).background(color))
            }
        }
    }
}

@Composable
private fun LegendRow(label: String, bytes: Long, whole: Long, dot: Color) {
    val colors = LocalAppColors.current
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
        Text(
            label,
            color = colors.ink,
            modifier = Modifier.weight(1f).padding(start = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Text(
            formatBytes(bytes),
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
        )
        Text(
            formatShare(share(bytes, whole)),
            color = colors.muted,
            modifier = Modifier.width(64.dp).padding(start = 8.dp),
            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun LargestRow(row: SizeRow, filesDir: java.io.File, onPlay: () -> Unit, onDelete: () -> Unit) {
    val colors = LocalAppColors.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(row.item, filesDir, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
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
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Item actions", tint = colors.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(
                    text = { Text("Play") },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                    onClick = {
                        menu = false
                        onPlay()
                    },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = colors.danger) },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger) },
                    onClick = {
                        menu = false
                        onDelete()
                    },
                )
            }
        }
    }
}
