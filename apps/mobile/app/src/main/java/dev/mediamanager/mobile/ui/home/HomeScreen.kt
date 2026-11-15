package dev.mediamanager.mobile.ui.home

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

    LazyColumn(
        Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                Text(
                    "${addedItems.size} item${if (addedItems.size == 1) "" else "s"}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        }

        item {
            Column(Modifier.padding(top = 12.dp)) {
                Text(
                    "Recently added",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                addedItems.take(8).chunked(2).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        row.forEach { item ->
                            JumpCard(
                                item = item,
                                filesDir = filesDir,
                                modifier = Modifier.weight(1f),
                                onClick = { player.playItems(addedItems, addedItems.indexOf(item)) },
                            )
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun JumpCard(
    item: Item,
    filesDir: java.io.File,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Row(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumb(item, filesDir, Modifier.size(48.dp))
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(
                item.title,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (item.isAudio) "Audio" else "Video",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}
