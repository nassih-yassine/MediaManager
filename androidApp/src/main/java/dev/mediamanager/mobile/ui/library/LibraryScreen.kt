package dev.mediamanager.mobile.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.components.ConfirmDialog
import dev.mediamanager.mobile.ui.components.EmptyState
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatBytes
import dev.mediamanager.mobile.download.watchedFraction
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.playlists.AddToPlaylistDialog
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun LibraryScreen(
    container: AppContainer,
    player: PlayerRepository,
    onAdd: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val filesDir = container.appContext.filesDir

    var filter by rememberSaveable { mutableStateOf(LibraryFilter.AUDIO.name) }

    val all by container.db.itemDao().observeAll(null)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var sort by rememberSaveable { mutableStateOf(LibrarySort.NEWEST.name) }
    var query by rememberSaveable { mutableStateOf("") }
    var listMode by rememberSaveable { mutableStateOf(true) }
    var menuSort by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDelete by remember { mutableStateOf<List<Item>>(emptyList()) }
    var playlistFor by remember { mutableStateOf<List<Item>?>(null) }

    BackHandler(enabled = selected.isNotEmpty()) { selected = emptySet() }

    val currentFilter = LibraryFilter.from(filter)
    val currentSort = LibrarySort.valueOf(sort)
    // Filtering and sorting run per keystroke otherwise; cache until an input moves.
    val visible by remember(all, currentFilter, currentSort, query) {
        androidx.compose.runtime.derivedStateOf {
            sortItems(
                all.map { it.toItem() }
                    .filter { kindFor(currentFilter)?.let { kind -> it.kind == kind } ?: true }
                    .filter { query.isBlank() || it.title.contains(query, ignoreCase = true) },
                currentSort,
            )
        }
    }

    fun playFrom(item: Item) {
        val index = visible.indexOfFirst { it.id == item.id }.coerceAtLeast(0)
        player.playItems(visible, index)
    }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search titles") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    keyboardOptions = KeyboardOptions.Default,
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                )
                IconButton(onClick = { listMode = !listMode }) {
                    Icon(
                        if (listMode) Icons.Default.GridView else Icons.Default.ViewList,
                        contentDescription = if (listMode) "Grid" else "List",
                        tint = colors.muted,
                    )
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LibraryFilter.shown.forEach { option ->
                    androidx.compose.material3.FilterChip(
                        selected = filter == option.name,
                        onClick = { filter = option.name },
                        label = { Text(option.label) },
                        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
                            selectedContainerColor = colors.accent.copy(alpha = 0.16f),
                            selectedLabelColor = colors.accent,
                            labelColor = colors.muted,
                        ),
                        border = androidx.compose.material3.FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = filter == option.name,
                            borderColor = colors.line,
                            selectedBorderColor = colors.accent,
                        ),
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                Spacer(Modifier.weight(1f))
                MmTextButton(
                    text = LibrarySort.entries.first { it.name == sort }.label,
                    icon = Icons.Default.Sort,
                    color = colors.muted,
                    onClick = { menuSort = true },
                )
                Box {
                    DropdownMenu(expanded = menuSort, onDismissRequest = { menuSort = false }) {
                        LibrarySort.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = { sort = option.name; menuSort = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "${visible.size} item${if (visible.size == 1) "" else "s"}",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }

        if (visible.isEmpty()) {
            EmptyState(
                title = if (all.isEmpty()) "Nothing here yet" else "No matches",
                body = if (all.isEmpty()) {
                    "Paste a link and it lands here, with cover art."
                } else {
                    "Try another search or filter."
                },
                actionLabel = if (all.isEmpty()) "Paste a link" else null,
                onAction = if (all.isEmpty()) onAdd else null,
            )
        } else if (listMode) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(visible, key = { it.id }) { item ->
                    ListRow(
                        item = item,
                        filesDir = filesDir,
                        onClick = { if (selected.isEmpty()) playFrom(item) else selected = selected.toggle(item.id) },
                        onLongClick = { selected = selected.toggle(item.id) },
                        onPlayNext = { player.playNext(item) },
                        onAddToQueue = { player.addToQueue(item) },
                        onAddToPlaylist = { playlistFor = listOf(item) },
                        onDelete = { pendingDelete = listOf(item) },
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                itemsIndexed(visible, key = { _, item -> item.id }) { _, item ->
                    LibraryCard(
                        item = item,
                        filesDir = filesDir,
                        selected = item.id in selected,
                        sizeLabel = if (currentSort == LibrarySort.LARGEST) formatBytes(item.sizeBytes) else null,
                        onClick = {
                            if (selected.isEmpty()) playFrom(item) else selected = selected.toggle(item.id)
                        },
                        onLongClick = { selected = selected.toggle(item.id) },
                        onPlayNext = { player.playNext(item) },
                        onAddToQueue = { player.addToQueue(item) },
                        onAddToPlaylist = { playlistFor = listOf(item) },
                        onDelete = { pendingDelete = listOf(item) },
                    )
                }
            }
        }

        if (selected.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(colors.panel2)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("${selected.size} selected", color = colors.ink)
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { playlistFor = visible.filter { it.id in selected } }) {
                    Icon(Icons.Default.PlaylistAdd, contentDescription = "Add to playlist", tint = colors.ink)
                }
                IconButton(onClick = { pendingDelete = visible.filter { it.id in selected } }) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.danger)
                }
                IconButton(onClick = { selected = emptySet() }) {
                    Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.muted)
                }
            }
        }
    }

    if (pendingDelete.isNotEmpty()) {
        val freed = pendingDelete.sumOf { it.sizeBytes }
        ConfirmDialog(
            title = if (pendingDelete.size == 1) "Delete this item?" else "Delete ${pendingDelete.size} items?",
            body = "The file and its playlist entries go. This frees ${formatBytes(freed)}.",
            confirmLabel = "Delete",
            onConfirm = {
                val ids = pendingDelete.map { it.id }
                scope.launch {
                    ids.forEach { id ->
                        player.onItemDeleted(id)
                        container.itemRepository.delete(id)
                    }
                    selected = emptySet()
                }
            },
            onDismiss = { pendingDelete = emptyList() },
        )
    }

    playlistFor?.let { items ->
        AddToPlaylistDialog(
            container = container,
            itemIds = items.map { it.id },
            onClose = { playlistFor = null },
        )
    }
}

private fun Set<String>.toggle(id: String): Set<String> =
    if (id in this) this - id else this + id

@Composable
private fun ListRow(
    item: Item,
    filesDir: java.io.File,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item, filesDir, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(
                item.title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append(if (item.isAudio) "Audio" else "Video")
                    formatDuration(item.durationMs).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
                    if (!item.available) append(" · file missing")
                },
                color = if (item.available) colors.muted else colors.danger,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = colors.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onAddToQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onAddToPlaylist() })
                DropdownMenuItem(
                    text = { Text("Delete", color = colors.danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
    }
}

@Composable
private fun LibraryCard(
    item: Item,
    filesDir: java.io.File,
    selected: Boolean,
    sizeLabel: String?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = LocalAppColors.current
    var menu by remember { mutableStateOf(false) }
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
            .padding(12.dp),
    ) {
        Box {
            Thumb(
                item = item,
                filesDir = filesDir,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .combinedClickable(onClick = onClick, onLongClick = onLongClick),
            )
            if (selected) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp),
                )
            }
            val duration = formatDuration(item.durationMs)
            if (duration.isNotEmpty()) {
                Text(
                    duration,
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(colors.panel.copy(alpha = 0.85f))
                        .padding(horizontal = 5.dp, vertical = 2.dp),
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = colors.accentInk,
                    modifier = Modifier.size(20.dp),
                )
            }
            IconButton(onClick = { menu = true }, modifier = Modifier.align(Alignment.TopStart)) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = colors.ink)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; onPlayNext() })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; onAddToQueue() })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; onAddToPlaylist() })
                DropdownMenuItem(
                    text = { Text("Delete", color = colors.danger) },
                    onClick = { menu = false; onDelete() },
                )
            }
        }
        val watched = watchedFraction(item.positionMs, item.durationMs)
        if (watched > 0f) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.panel2),
            ) {
                Box(Modifier.fillMaxWidth(watched).height(3.dp).background(colors.accent))
            }
            Spacer(Modifier.height(5.dp))
        } else {
            Spacer(Modifier.height(8.dp))
        }
        Text(
            item.title,
            color = colors.ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
        )
        Text(
            buildString {
                append(if (item.isAudio) "Audio" else "Video")
                if (!item.available) append(" · file missing")
                sizeLabel?.let { append(" · ").append(it) }
            },
            color = if (item.available) colors.muted else colors.danger,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
