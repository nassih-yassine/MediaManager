package dev.mediamanager.mobile.ui.playlists

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.PlaylistEntity
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.components.loadThumb
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

@Composable
fun PlaylistsScreen(container: AppContainer, player: PlayerRepository) {
    var selectedId by remember { mutableStateOf<String?>(null) }
    val id = selectedId
    if (id == null) {
        PlaylistList(container, onOpen = { selectedId = it })
    } else {
        PlaylistDetail(container, player, playlistId = id, onBack = { selectedId = null })
    }
}

@Composable
private fun PlaylistList(container: AppContainer, onOpen: (String) -> Unit) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.db.playlistDao().observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val itemsByPlaylist = remember(playlists) { mutableMapOf<String, List<Item>>() }
    var newName by remember { mutableStateOf("") }

    // Previews are cheap: the first four entries drive the mosaic.
    val previews by produceState(emptyMap<String, List<Item>>(), playlists) {
        val map = mutableMapOf<String, List<Item>>()
        playlists.forEach { playlist ->
            val ids = container.db.playlistDao().items(playlist.id).take(4).map { it.itemId }
            map[playlist.id] = ids.mapNotNull { container.db.itemDao().get(it)?.toItem() }
        }
        value = map
    }
    itemsByPlaylist.putAll(previews)

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New playlist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isEmpty()) return@Button
                    scope.launch {
                        val now = System.currentTimeMillis()
                        container.db.playlistDao().upsert(
                            PlaylistEntity(
                                id = UUID.randomUUID().toString(),
                                name = name,
                                createdAt = now,
                                updatedAt = now,
                            ),
                        )
                        newName = ""
                    }
                },
                enabled = newName.isNotBlank(),
                modifier = Modifier.padding(start = 8.dp),
            ) { Text("Create") }
        }
        Spacer(Modifier.height(12.dp))
        if (playlists.isEmpty()) {
            Text(
                "Playlists hold any mix of audio and video. Create one, then add items from the library.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
        }
        playlists.forEach { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PlaylistCover(
                    coverPath = playlist.coverPath,
                    items = itemsByPlaylist[playlist.id].orEmpty(),
                    filesDir = container.appContext.filesDir,
                    size = 56.dp,
                )
                Spacer(Modifier.size(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        color = colors.ink,
                        style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        "${itemsByPlaylist[playlist.id]?.size ?: 0} shown · tap to open",
                        color = colors.muted,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
                Text("Open", color = colors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaylistDetail(
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

    var coverSheet by remember { mutableStateOf(false) }
    var itemPicker by remember { mutableStateOf(false) }
    var renameOpen by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            container.playlistCovers.fromUri(playlistId, uri)
            playlist = dao.get(playlistId)
        }
    }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = colors.muted) }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { if (items.isNotEmpty()) player.playItems(items, 0) }) {
                Text("Play all", color = colors.accent)
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.clickable { coverSheet = true }) {
                PlaylistCover(
                    coverPath = playlist?.coverPath,
                    items = items.take(4),
                    filesDir = container.appContext.filesDir,
                    size = 96.dp,
                )
            }
            Spacer(Modifier.size(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    playlist?.name ?: "Playlist",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(
                    "${items.size} items",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                Row {
                    TextButton(onClick = {
                        renameValue = playlist?.name.orEmpty()
                        renameOpen = true
                    }) { Text("Rename", color = colors.accent) }
                    TextButton(onClick = { coverSheet = true }) {
                        Text("Change cover", color = colors.accent)
                    }
                }
            }
        }

        if (items.isEmpty()) {
            Text("Empty. Add items from the library.", color = colors.muted)
        }
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Thumb(item, container.appContext.filesDir, Modifier.size(40.dp))
                Text(
                    item.title,
                    color = colors.ink,
                    modifier = Modifier.weight(1f).padding(start = 8.dp).clickable {
                        player.playItems(items, index)
                    },
                    maxLines = 1,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = {
                    if (index == 0) return@TextButton
                    val ids = items.map { it.id }.toMutableList()
                    ids.add(index - 1, ids.removeAt(index))
                    scope.launch { dao.reorder(playlistId, ids) }
                }) { Text("↑", color = colors.muted) }
                TextButton(onClick = {
                    if (index == items.lastIndex) return@TextButton
                    val ids = items.map { it.id }.toMutableList()
                    ids.add(index + 1, ids.removeAt(index))
                    scope.launch { dao.reorder(playlistId, ids) }
                }) { Text("↓", color = colors.muted) }
                TextButton(onClick = { scope.launch { dao.removeItem(playlistId, item.id) } }) {
                    Text("✕", color = colors.danger)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = {
            scope.launch { dao.delete(playlistId) }
            onBack()
        }) { Text("Delete playlist", color = colors.danger) }
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
        ModalBottomSheet(
            onDismissRequest = { coverSheet = false },
            containerColor = colors.panel,
        ) {
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
                SheetAction("From this playlist", enabled = items.isNotEmpty()) {
                    coverSheet = false
                    itemPicker = true
                }
                SheetAction("Choose a photo") {
                    coverSheet = false
                    pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (playlist?.coverPath != null) {
                    SheetAction("Remove cover", danger = true) {
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
        ModalBottomSheet(
            onDismissRequest = { itemPicker = false },
            containerColor = colors.panel,
        ) {
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
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    TextButton(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = when {
                !enabled -> colors.muted
                danger -> colors.danger
                else -> colors.accent
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Custom cover first; otherwise the Spotify-style 2×2 mosaic from the first four
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
    val custom by produceState<Bitmap?>(null, coverPath) {
        value = coverPath?.let { loadThumb(File(filesDir, it)) }
    }
    val mosaic by produceState<List<Bitmap>>(emptyList(), items.map { it.thumbPath }) {
        value = items.mapNotNull { item -> item.thumbPath?.let { loadThumb(File(filesDir, it)) } }
    }

    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(if (size > 64.dp) 16.dp else 12.dp))
            .background(colors.panel2),
        contentAlignment = Alignment.Center,
    ) {
        val cover = custom
        when {
            cover != null -> Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = "Playlist cover",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )

            mosaic.isNotEmpty() -> Mosaic(mosaic, size)

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
private fun Mosaic(bitmaps: List<Bitmap>, size: Dp) {
    val colors = LocalAppColors.current
    val cell = size / 2
    Column(Modifier.size(size)) {
        Row {
            MosaicCell(bitmaps.getOrNull(0), cell, colors.panel2)
            MosaicCell(bitmaps.getOrNull(1), cell, colors.panel2)
        }
        Row {
            MosaicCell(bitmaps.getOrNull(2), cell, colors.panel2)
            MosaicCell(bitmaps.getOrNull(3), cell, colors.panel2)
        }
    }
}

@Composable
private fun MosaicCell(bitmap: Bitmap?, size: Dp, placeholder: androidx.compose.ui.graphics.Color) {
    Box(Modifier.size(size).background(placeholder)) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(size),
            )
        }
    }
}
