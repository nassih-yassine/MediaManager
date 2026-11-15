package dev.mediamanager.mobile.ui.playlists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
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

/** Adds [itemIds] to an existing playlist, or to a new one typed inline. */
@OptIn(ExperimentalMaterial3Api::class)
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

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.panel) {
        Column(
            Modifier
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                if (itemIds.size == 1) "Add to playlist" else "Add ${itemIds.size} items",
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.height(8.dp))

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
                    Text(
                        playlist.name,
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyLarge,
                    )
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Add",
                        tint = colors.accent,
                        modifier = Modifier.size(20.dp),
                    )
                }
                HorizontalDivider(color = colors.line)
            }
            if (playlists.isEmpty()) {
                Text("No playlists yet. Create one below.", color = colors.muted)
                Spacer(Modifier.height(8.dp))
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New playlist") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onClose, modifier = Modifier.weight(1f)) {
                    Text("Cancel", color = colors.muted)
                }
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
                    modifier = Modifier.weight(2f).height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentInk,
                    ),
                ) { Text("Create and add") }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
