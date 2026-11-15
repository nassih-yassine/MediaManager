package dev.mediamanager.mobile.ui.playlists

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import kotlin.math.roundToInt
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.PlaylistEntity
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.components.EmptyState
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun PlaylistsTab(container: AppContainer, player: PlayerRepository) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val id = selectedId
    if (id == null) {
        PlaylistListContent(container, onOpen = { selectedId = it })
    } else {
        PlaylistDetailScreen(container, player, playlistId = id, onBack = { selectedId = null })
    }
}

@Composable
fun PlaylistListContent(container: AppContainer, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.db.playlistDao().observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var creating by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    val previews by produceState(emptyMap<String, List<Item>>(), playlists) {
        val map = mutableMapOf<String, List<Item>>()
        playlists.forEach { playlist ->
            val ids = container.db.playlistDao().items(playlist.id).take(4).map { it.itemId }
            map[playlist.id] = ids.mapNotNull { container.db.itemDao().get(it)?.toItem() }
        }
        value = map
    }

    if (playlists.isEmpty()) {
        EmptyState(
            title = "No playlists yet",
            body = "A playlist holds any mix of audio and video. Create one, then add items from the library.",
            actionLabel = "New playlist",
            onAction = { creating = true },
        )
    } else {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "${playlists.size} playlist${if (playlists.size == 1) "" else "s"}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                MmTextButton("New", Icons.Default.Add, onClick = { creating = true })
            }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(playlists, key = { it.id }) { playlist ->
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.panel)
                            .border(1.dp, colors.line, RoundedCornerShape(16.dp))
                            .clickable { onOpen(playlist.id) }
                            .padding(10.dp),
                    ) {
                        PlaylistCover(
                            coverPath = playlist.coverPath,
                            items = previews[playlist.id].orEmpty(),
                            filesDir = container.appContext.filesDir,
                            size = 150.dp,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            playlist.name,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            "${previews[playlist.id]?.size ?: 0} shown",
                            color = colors.muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }

    if (creating) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { creating = false },
            containerColor = colors.panel,
            title = { Text("New playlist", color = colors.ink) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = newName.trim()
                        if (name.isEmpty()) return@TextButton
                        scope.launch {
                            val now = System.currentTimeMillis()
                            val playlist = PlaylistEntity(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                createdAt = now,
                                updatedAt = now,
                            )
                            container.db.playlistDao().upsert(playlist)
                            newName = ""
                            creating = false
                            onOpen(playlist.id)
                        }
                    },
                    enabled = newName.isNotBlank(),
                ) { Text("Create", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { creating = false }) { Text("Cancel", color = colors.muted) }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    container: AppContainer,
    player: PlayerRepository,
    playlistId: String,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    BackHandler(onBack = onBack)
    val dao = container.db.playlistDao()
    val entries by dao.observeItems(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val items by produceState(emptyList<Item>(), entries) {
        value = entries.mapNotNull { entry -> container.db.itemDao().get(entry.itemId)?.toItem() }
    }
    var playlist by remember { mutableStateOf<PlaylistEntity?>(null) }
    LaunchedEffect(playlistId) { playlist = dao.get(playlistId) }

    var menu by remember { mutableStateOf(false) }
    var coverSheet by remember { mutableStateOf(false) }
    var itemPicker by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var draggingIndex by remember { mutableIntStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val rowHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 64.dp.toPx() }
    var renameValue by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            container.playlistCovers.fromUri(playlistId, uri)
            playlist = dao.get(playlistId)
        }
    }

    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MmTextButton("Back", Icons.Default.ArrowBack, color = colors.muted, onClick = onBack)
                        Spacer(Modifier.weight(1f))
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Playlist actions", tint = colors.muted)
                            }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        renameValue = playlist?.name.orEmpty()
                                        renameOpen = true
                                        menu = false
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Change cover") },
                                    leadingIcon = { Icon(Icons.Default.AddPhotoAlternate, contentDescription = null) },
                                    onClick = { coverSheet = true; menu = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete", color = colors.danger) },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger) },
                                    onClick = {
                                        menu = false
                                        scope.launch { dao.delete(playlistId) }
                                        onBack()
                                    },
                                )
                            }
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlaylistCover(
                            coverPath = playlist?.coverPath,
                            items = items.take(4),
                            filesDir = container.appContext.filesDir,
                            size = 128.dp,
                        )
                        Spacer(Modifier.size(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                playlist?.name ?: "Playlist",
                                color = colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                            )
                            Text(
                                "${items.size} item${if (items.size == 1) "" else "s"}",
                                color = colors.muted,
                                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                            )
                            Spacer(Modifier.height(8.dp))
                            androidx.compose.material3.Button(
                                onClick = { if (items.isNotEmpty()) player.playItems(items, 0) },
                                enabled = items.isNotEmpty(),
                                colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                    containerColor = colors.accent,
                                    contentColor = colors.accentInk,
                                ),
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.size(8.dp))
                                Text("Play all")
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }

            if (items.isEmpty()) {
                item {
                    Text(
                        "Empty. Add items from the library with \"Add to playlist\".",
                        color = colors.muted,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                val dragging = draggingIndex == index
                Row(
                    Modifier
                        .fillMaxWidth()
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (dragging) dragOffset else 0f
                            alpha = if (dragging) 0.92f else 1f
                        }
                        .background(if (dragging) colors.panel2 else colors.canvas)
                        .clickable { player.playItems(items, index) }
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Thumb(item, container.appContext.filesDir, Modifier.size(44.dp))
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text(
                            item.title,
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "${if (item.isAudio) "Audio" else "Video"} · ${formatDuration(item.durationMs)}",
                            color = colors.muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                    var rowMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { rowMenu = true }) {
                            Icon(
                                Icons.Default.MoreVert,
                                contentDescription = "Item actions",
                                tint = colors.muted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        DropdownMenu(expanded = rowMenu, onDismissRequest = { rowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Play") },
                                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                                onClick = {
                                    rowMenu = false
                                    player.playItems(items, index)
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Remove from playlist", color = colors.danger) },
                                leadingIcon = {
                                    Icon(Icons.Default.Delete, contentDescription = null, tint = colors.danger)
                                },
                                onClick = {
                                    rowMenu = false
                                    scope.launch { dao.removeItem(playlistId, item.id) }
                                },
                            )
                        }
                    }
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Drag to reorder",
                        tint = colors.muted,
                        modifier = Modifier
                            .size(32.dp)
                            .pointerInput(item.id, items.size) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffset = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex
                                        draggingIndex = -1
                                        val steps = (dragOffset / rowHeightPx).roundToInt()
                                        dragOffset = 0f
                                        if (from >= 0) {
                                            val to = (from + steps).coerceIn(0, items.lastIndex)
                                            if (to != from) {
                                                val ids = items.map { it.id }.toMutableList()
                                                ids.add(to, ids.removeAt(from))
                                                scope.launch { dao.reorder(playlistId, ids) }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        draggingIndex = -1
                                        dragOffset = 0f
                                    },
                                )
                            },
                    )
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }

    if (renameOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { renameOpen = false },
            containerColor = colors.panel,
            title = { Text("Rename playlist", color = colors.ink) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it },
                    singleLine = true,
                    label = { Text("Name") },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val name = renameValue.trim()
                        if (name.isNotEmpty()) {
                            scope.launch {
                                dao.rename(playlistId, name, System.currentTimeMillis())
                                playlist = dao.get(playlistId)
                            }
                        }
                        renameOpen = false
                    },
                ) { Text("Save", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text("Cancel", color = colors.muted) }
            },
        )
    }

    if (coverSheet) {
        ModalBottomSheet(onDismissRequest = { coverSheet = false }, containerColor = colors.panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Playlist cover",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(
                    "A mosaic is built from the first four items until you pick your own.",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(8.dp))
                SheetAction("From this playlist", Icons.Default.PlaylistPlay, enabled = items.isNotEmpty()) {
                    coverSheet = false
                    itemPicker = true
                }
                SheetAction("Choose a photo", Icons.Default.PhotoLibrary) {
                    coverSheet = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (playlist?.coverPath != null) {
                    SheetAction("Remove cover", Icons.Default.Delete, danger = true) {
                        coverSheet = false
                        scope.launch {
                            container.playlistCovers.clear(playlistId)
                            playlist = dao.get(playlistId)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (itemPicker) {
        ModalBottomSheet(onDismissRequest = { itemPicker = false }, containerColor = colors.panel) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Choose from this playlist",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.height(8.dp))
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.height(320.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items, key = { it.id }) { item ->
                        Thumb(
                            item = item,
                            filesDir = container.appContext.filesDir,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    scope.launch {
                                        container.playlistCovers.fromItemThumb(playlistId, item.thumbPath)
                                        playlist = dao.get(playlistId)
                                        itemPicker = false
                                    }
                                },
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun SheetAction(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Icon(
            icon,
            contentDescription = null,
            tint = when {
                !enabled -> colors.muted
                danger -> colors.danger
                else -> colors.accent
            },
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            label,
            color = when {
                !enabled -> colors.muted
                danger -> colors.danger
                else -> colors.accent
            },
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Custom cover first; otherwise the Spotify-style 2x2 mosaic from the first four
 * items; otherwise a glyph. The mosaic is rendered from item thumbs, never stored.
 */
@Composable
private fun PlaylistCover(
    coverPath: String?,
    items: List<Item>,
    filesDir: File,
    size: Dp,
) {
    val colors = LocalAppColors.current
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 64.dp) 16.dp else 12.dp))
            .background(colors.panel2),
        contentAlignment = Alignment.Center,
    ) {
        val thumbFiles = items.mapNotNull { it.thumbPath }.map { File(filesDir, it) }.filter { it.exists() }
        when {
            coverPath != null && File(filesDir, coverPath).exists() -> coil.compose.AsyncImage(
                model = File(filesDir, coverPath),
                contentDescription = "Playlist cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )

            thumbFiles.isNotEmpty() -> Mosaic(thumbFiles, size)

            else -> Icon(
                Icons.Default.PlaylistPlay,
                contentDescription = null,
                tint = colors.muted,
                modifier = Modifier.size(size / 2),
            )
        }
    }
}

@Composable
private fun Mosaic(files: List<File>, size: Dp) {
    val colors = LocalAppColors.current
    val cell = size / 2
    Column(Modifier.size(size)) {
        Row {
            MosaicCell(files.getOrNull(0), cell, colors.panel2)
            MosaicCell(files.getOrNull(1), cell, colors.panel2)
        }
        Row {
            MosaicCell(files.getOrNull(2), cell, colors.panel2)
            MosaicCell(files.getOrNull(3), cell, colors.panel2)
        }
    }
}

@Composable
private fun MosaicCell(file: File?, size: Dp, placeholder: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(size).background(placeholder)) {
        if (file != null) {
            coil.compose.AsyncImage(
                model = file,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
