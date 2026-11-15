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
import java.time.LocalTime

/**
 * The landing screen: shelves of the things a person actually returns for, in the
 * order they usually want them.
 */
@Composable
fun HomeScreen(
    container: AppContainer,
    player: PlayerRepository,
    onAdd: () -> Unit,
    onOpenPlaylists: () -> Unit,
) {
    val colors = LocalAppColors.current
    val filesDir = container.appContext.filesDir

    val inProgress by container.db.itemDao().observeInProgress(12)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyPlayed by container.db.itemDao().observeRecentlyPlayed(12)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val recentlyAdded by container.db.itemDao().observeRecentlyAdded(20)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val playlists by container.db.playlistDao().observePlaylists()
        .collectAsStateWithLifecycle(initialValue = emptyList())

    val inProgressItems = remember(inProgress) { inProgress.map { it.toItem() } }
    // The same item should not appear twice above the fold: it is either in
    // "Continue listening" or in "Recently played", never both.
    val playedItems = remember(recentlyPlayed, inProgress) {
        val resumed = inProgress.map { it.id }.toSet()
        recentlyPlayed.map { it.toItem() }.filterNot { it.id in resumed }
    }
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
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    greeting(),
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.displaySmall,
                )
                Text(
                    "${addedItems.size} item${if (addedItems.size == 1) "" else "s"} · " +
                        "${playlists.size} playlist${if (playlists.size == 1) "" else "s"}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }
        }

        if (inProgressItems.isNotEmpty()) {
            item {
                Shelf(
                    title = "Continue listening",
                    items = inProgressItems,
                    filesDir = filesDir,
                    onItem = { item -> player.playItems(inProgressItems, inProgressItems.indexOf(item)) },
                )
            }
        }

        if (playedItems.isNotEmpty()) {
            item {
                Shelf(
                    title = "Recently played",
                    items = playedItems,
                    filesDir = filesDir,
                    onItem = { item -> player.playItems(playedItems, playedItems.indexOf(item)) },
                )
            }
        }

        if (playlists.isNotEmpty()) {
            item {
                PlaylistShelf(
                    playlists = playlists.map { it.name },
                    covers = playlists.map { it.coverPath },
                    filesDir = filesDir,
                    onOpen = onOpenPlaylists,
                )
            }
        }

        item {
            Shelf(
                title = "Recently added",
                items = addedItems,
                filesDir = filesDir,
                onItem = { item -> player.playItems(addedItems, addedItems.indexOf(item)) },
            )
        }
    }
}

private fun greeting(): String = when (LocalTime.now().hour) {
    in 5..11 -> "Good morning"
    in 12..17 -> "Good afternoon"
    else -> "Good evening"
}

@Composable
private fun Shelf(
    title: String,
    items: List<Item>,
    filesDir: java.io.File,
    onItem: (Item) -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(top = 12.dp)) {
        Text(
            title,
            color = colors.ink,
            style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { _, item ->
                ShelfCard(item = item, filesDir = filesDir, onClick = { onItem(item) })
            }
        }
    }
}

@Composable
private fun ShelfCard(item: Item, filesDir: java.io.File, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Column(
        Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel)
            .clickable(onClick = onClick)
            .padding(8.dp),
    ) {
        Thumb(item, filesDir, Modifier.fillMaxWidth().height(120.dp))
        Spacer(Modifier.height(8.dp))
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
                formatDuration(item.durationMs).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
            },
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
        val fraction = item.durationMs?.takeIf { it > 0 }?.let { item.positionMs.toFloat() / it } ?: 0f
        if (fraction > 0f) {
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(colors.panel2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(colors.accent),
                )
            }
        }
    }
}

@Composable
private fun PlaylistShelf(
    playlists: List<String>,
    covers: List<String?>,
    filesDir: java.io.File,
    onOpen: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(Modifier.padding(top = 12.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Playlists",
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            Text(
                "See all",
                color = colors.accent,
                modifier = Modifier.clickable(onClick = onOpen),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
        }
        Spacer(Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            itemsIndexed(playlists) { index, name ->
                Column(
                    Modifier
                        .width(112.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.panel)
                        .clickable(onClick = onOpen)
                        .padding(8.dp),
                ) {
                    val cover = covers.getOrNull(index)
                    Box(
                        Modifier
                            .size(96.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.panel2),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (cover != null) {
                            coil.compose.AsyncImage(
                                model = java.io.File(filesDir, cover),
                                contentDescription = name,
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                modifier = Modifier.size(96.dp),
                            )
                        } else {
                            Icon(
                                Icons.Default.LibraryMusic,
                                contentDescription = null,
                                tint = colors.muted,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        name,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}
