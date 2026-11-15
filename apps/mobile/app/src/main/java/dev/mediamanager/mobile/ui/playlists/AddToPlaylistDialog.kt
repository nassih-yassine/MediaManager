package dev.mediamanager.mobile.ui.playlists

import androidx.compose.foundation.clickable
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
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch
import java.util.UUID

/** Adds [itemIds] to an existing playlist or to a new one. */
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
                                    container.db.playlistDao().addItems(
                                        playlist.id,
                                        itemIds,
                                        System.currentTimeMillis(),
                                    )
                                    onClose()
                                }
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(playlist.name, color = colors.ink)
                        Spacer(Modifier.weight(1f))
                        Text(
                            "add",
                            color = colors.accent,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
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
                        val playlist = PlaylistEntity(
                            id = UUID.randomUUID().toString(),
                            name = name,
                            createdAt = now,
                            updatedAt = now,
                        )
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
