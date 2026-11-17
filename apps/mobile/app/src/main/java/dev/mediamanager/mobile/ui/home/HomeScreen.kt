package dev.mediamanager.mobile.ui.home

import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import dev.mediamanager.mobile.ui.components.EmptyState
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * The landing screen: shelves of the things a person actually returns for, in the
 * order they usually want them.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    player: PlayerRepository,
    onAdd: () -> Unit,
) {
    val colors = LocalAppColors.current
    val filesDir = container.appContext.filesDir

    val recentlyAdded by container.db.itemDao().observeRecentlyAdded(20)
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val addedItems = remember(recentlyAdded) { recentlyAdded.map { it.toItem() } }

    if (addedItems.isEmpty()) {
        EmptyState(
            title = "Welcome to MediaManager",
            body = "Paste a link and it lands here with cover art. YouTube works, and so does anything else yt-dlp knows.",
            actionLabel = "Paste a link",
            onAction = onAdd,
        )
        return
    }

    val recent = addedItems.take(12)
    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item {
            // One header line, like the library: section on the left, count on the right.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Recently added",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${addedItems.size} item${if (addedItems.size == 1) "" else "s"}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
        }
        items(recent, key = { it.id }) { item ->
            RecentRow(
                item = item,
                filesDir = filesDir,
                onClick = { player.playItems(addedItems, addedItems.indexOf(item)) },
            )
        }
    }
}

@Composable
private fun RecentRow(
    item: Item,
    filesDir: java.io.File,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    // Flat row: same rhythm as the library, no card border to add noise.
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
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
                    formatDuration(item.durationMs).takeIf { it.isNotEmpty() }
                        ?.let { append(" · ").append(it) }
                },
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
        Icon(
            Icons.Default.PlayArrow,
            contentDescription = "Play",
            tint = colors.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}
