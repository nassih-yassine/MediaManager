package dev.mediamanager.mobile.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import dev.mediamanager.mobile.data.PlaylistEntity
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.PlaylistEntry
import dev.mediamanager.mobile.download.PlaylistSummary
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of one playlist import, in the copy the Add screen shows. */
data class ImportOutcome(
    val queued: Int,
    val existing: Int,
    val duplicates: Int,
) {
    /** Nothing at all was added: empty selection, or every entry already queued. */
    val nothing: Boolean get() = queued == 0 && existing == 0
}

/**
 * Turns a summary into a real playlist plus one job per missing video, and links the
 * videos that are already in the library at their source positions.
 *
 * The playlist row is created first: jobs can finish before this function returns, and
 * the pipeline links an item only when the playlist already exists.
 */
class PlaylistImportRunner(
    private val db: dev.mediamanager.mobile.data.AppDatabase,
    private val queue: dev.mediamanager.mobile.download.JobQueue,
    private val startService: () -> Unit = {},
) {

    suspend fun import(
        summary: PlaylistSummary,
        mode: DownloadMode,
        selected: List<PlaylistEntry>,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext ImportOutcome(0, 0, 0)

        val now = System.currentTimeMillis()
        val playlistId = UUID.randomUUID().toString()
        db.playlistDao().upsert(
            PlaylistEntity(
                id = playlistId,
                name = summary.name,
                sourceUrl = "https://www.youtube.com/playlist?list=${summary.listId}",
                coverPath = null,
                createdAt = now,
                updatedAt = now,
            ),
        )

        var queued = 0
        var existing = 0
        var duplicates = 0
        val links = mutableListOf<Pair<String, Int>>()

        selected.forEachIndexed { index, entry ->
            val result = queue.enqueue(
                url = entry.url,
                mode = mode,
                playlistId = playlistId,
                playlistPosition = index,
            )
            when {
                result.libraryItemId != null -> {
                    existing++
                    links += result.libraryItemId!! to index
                }

                // A duplicate returns the existing job; it must not count as new work.
                result.duplicate -> duplicates++

                result.job != null -> queued++

                else -> duplicates++
            }
        }

        if (links.isNotEmpty()) {
            db.withTransaction {
                links.forEach { (itemId, position) ->
                    db.playlistDao().insertAt(playlistId, itemId, position, now)
                }
            }
        }
        if (queued == 0 && existing == 0) {
            db.playlistDao().delete(playlistId)
        } else if (queued > 0) {
            startService()
        }
        ImportOutcome(queued = queued, existing = existing, duplicates = duplicates)
    }
}

/** Choice sheet: import everything, or open the picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistImportSheet(
    summary: PlaylistSummary,
    mode: DownloadMode,
    onMode: (DownloadMode) -> Unit,
    onAll: () -> Unit,
    onChoose: () -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
            Text(
                summary.name,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                buildString {
                    summary.uploader?.takeIf { it.isNotBlank() }?.let { append(it).append(" · ") }
                    append("${summary.count} item${if (summary.count == 1) "" else "s"}")
                    if (summary.truncated) append(" (first ${summary.count} of ${summary.total})")
                },
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )

            Row(
                Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ModeChoice(
                    title = "Audio",
                    icon = Icons.Default.MusicNote,
                    selected = mode == DownloadMode.AUDIO,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(DownloadMode.AUDIO) },
                )
                ModeChoice(
                    title = "Video",
                    icon = Icons.Default.Videocam,
                    selected = mode == DownloadMode.VIDEO,
                    modifier = Modifier.weight(1f),
                    onClick = { onMode(DownloadMode.VIDEO) },
                )
            }

            Button(
                onClick = onAll,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.accentInk),
            ) {
                Text("Download all")
            }
            TextButton(onClick = onChoose, modifier = Modifier.fillMaxWidth()) {
                Text("Choose media", color = colors.accent)
            }
            Text(
                "Items already in your library are added to the new playlist, not downloaded again.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}

/** Picker sheet: search by title, select all or none, then queue the subset. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistSelectSheet(
    summary: PlaylistSummary,
    onConfirm: (List<PlaylistEntry>) -> Unit,
    onDismiss: () -> Unit,
) {
    val colors = LocalAppColors.current
    var query by remember(summary) { mutableStateOf("") }
    var selected by remember(summary) { mutableStateOf(summary.entries.map { it.url }.toSet()) }
    val visible = remember(summary, query) {
        if (query.isBlank()) {
            summary.entries
        } else {
            summary.entries.filter { it.title.contains(query.trim(), ignoreCase = true) }
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.panel,
    ) {
        Column(Modifier.fillMaxWidth().heightIn(max = 520.dp).padding(horizontal = 16.dp).padding(bottom = 16.dp)) {
            // One control: All is on when every entry is selected. Unticking it clears
            // the selection so the user can pick only what they need.
            val allState = when {
                selected.isEmpty() -> ToggleableState.Off
                selected.size == summary.count -> ToggleableState.On
                else -> ToggleableState.Indeterminate
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${selected.size} of ${summary.count} selected",
                    color = colors.ink,
                    modifier = Modifier.weight(1f),
                    style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
                )
                Text(
                    "All",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
                TriStateCheckbox(
                    state = allState,
                    onClick = {
                        selected = if (allState == ToggleableState.On) {
                            emptySet()
                        } else {
                            summary.entries.map { it.url }.toSet()
                        }
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = colors.accent,
                        checkmarkColor = colors.accentInk,
                        uncheckedColor = colors.muted,
                    ),
                )
            }

            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search titles") },
                shape = RoundedCornerShape(12.dp),
            )

            if (visible.isEmpty()) {
                Text(
                    "No titles match “${query.trim()}”.",
                    color = colors.muted,
                    modifier = Modifier.padding(vertical = 24.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            } else {
                LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(top = 4.dp)) {
                    items(visible, key = { it.url }) { entry ->
                        val checked = entry.url in selected
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selected = if (checked) selected - entry.url else selected + entry.url
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = null,
                                colors = CheckboxDefaults.colors(
                                    checkedColor = colors.accent,
                                    uncheckedColor = colors.muted,
                                ),
                            )
                            Text(
                                entry.title,
                                color = colors.ink,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f).padding(start = 4.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChoice(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.18f) else colors.panel2)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) colors.accent else colors.muted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        Text(title, color = if (selected) colors.accent else colors.ink)
        if (selected) {
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Check, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
        }
    }
}
