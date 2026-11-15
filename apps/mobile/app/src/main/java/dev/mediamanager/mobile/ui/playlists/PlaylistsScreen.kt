package dev.mediamanager.mobile.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.PlaylistEntity
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun AddToPlaylistDialog(
    container: AppContainer,
    itemIds: List<String>,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val playlists by container.db.playlistDao().observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList())
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = colors.panel,
        title = { Text("Add to playlist", color = colors.ink) },
        text = {
            Column {
                playlists.forEach { playlist ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    container.db.playlistDao().addItems(playlist.id, itemIds, System.currentTimeMillis())
                                    onClose()
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        Text("add", color = colors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
                    }
                }
                if (playlists.isEmpty()) {
                    Text("No playlists yet.", color = colors.muted)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New playlist") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isEmpty()) return@Button
                    scope.launch {
                        val now = System.currentTimeMillis()
                        val playlist = PlaylistEntity(UUID.randomUUID().toString(), name, now, now)
                        container.db.playlistDao().upsert(playlist)
                        container.db.playlistDao().addItems(playlist.id, itemIds, now)
                        onClose()
                    }
                },
                enabled = newName.isNotBlank(),
            ) { Text("Create and add") }
        },
        dismissButton = {
            TextButton(onClick = onClose) { Text("Cancel", color = colors.muted) }
        },
    )
}

@Composable
fun PlaylistsScreen(container: AppContainer, player: dev.mediamanager.mobile.playback.PlayerRepository) {
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
    var newName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New playlist") },
                singleLine = true,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    val name = newName.trim()
                    if (name.isEmpty()) return@Button
                    scope.launch {
                        val now = System.currentTimeMillis()
                        container.db.playlistDao().upsert(
                            dev.mediamanager.mobile.data.PlaylistEntity(UUID.randomUUID().toString(), name, now, now),
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
            Text("Playlists hold any mix of audio and video.", color = colors.muted)
        }
        playlists.forEach { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onOpen(playlist.id) }
                    .padding(vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(playlist.name, color = colors.ink, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Text("Open", color = colors.accent, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun PlaylistDetail(
    container: AppContainer,
    player: dev.mediamanager.mobile.playback.PlayerRepository,
    playlistId: String,
    onBack: () -> Unit,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val dao = container.db.playlistDao()
    val entries by dao.observeItems(playlistId).collectAsStateWithLifecycle(initialValue = emptyList())
    val items by androidx.compose.runtime.produceState(emptyList<dev.mediamanager.mobile.data.Item>(), entries) {
        value = entries.mapNotNull { entry ->
            container.db.itemDao().get(entry.itemId)?.toItem()
        }
    }
    var playlist by remember { mutableStateOf<dev.mediamanager.mobile.data.PlaylistEntity?>(null) }
    androidx.compose.runtime.LaunchedEffect(playlistId) { playlist = dao.get(playlistId) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text("Back", color = colors.muted) }
            Spacer(Modifier.weight(1f))
            Text(playlist?.name ?: "Playlist", color = colors.ink)
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { if (items.isNotEmpty()) player.playItems(items, 0) },
            ) { Text("Play all", color = colors.accent) }
        }
        if (items.isEmpty()) {
            Text("Empty. Add items from the library.", color = colors.muted)
        }
        items.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    "${index + 1}.",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
                Text(
                    item.title,
                    color = colors.ink,
                    modifier = Modifier.weight(1f).clickable {
                        player.playItems(items, index)
                    },
                    maxLines = 1,
                )
                TextButton(onClick = {
                    if (index == 0) return@TextButton
                    val ids = items.map { it.id }.toMutableList()
                    val moved = ids.removeAt(index)
                    ids.add(index - 1, moved)
                    scope.launch { dao.reorder(playlistId, ids) }
                }) { Text("↑", color = colors.muted) }
                TextButton(onClick = {
                    if (index == items.lastIndex) return@TextButton
                    val ids = items.map { it.id }.toMutableList()
                    val moved = ids.removeAt(index)
                    ids.add(index + 1, moved)
                    scope.launch { dao.reorder(playlistId, ids) }
                }) { Text("↓", color = colors.muted) }
                TextButton(onClick = {
                    scope.launch { dao.removeItem(playlistId, item.id) }
                }) { Text("✕", color = colors.danger) }
            }
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = {
            scope.launch { dao.delete(playlistId) }
            onBack()
        }) { Text("Delete playlist", color = colors.danger) }
    }
}
