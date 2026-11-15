package dev.mediamanager.mobile.ui.player

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.PlayerView
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.io.File

@Composable
fun MiniPlayer(player: PlayerRepository, filesDir: File, onOpen: () -> Unit) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val current = now ?: return
    val colors = LocalAppColors.current
    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.panel)
            .clickable(onClick = onOpen)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(
                item = dev.mediamanager.mobile.data.Item(
                    id = current.itemId,
                    kind = if (current.isVideo) "video" else "audio",
                    title = current.title,
                    sizeBytes = 0,
                    path = "",
                    thumbPath = current.thumbPath,
                    available = true,
                    positionMs = current.positionMs,
                    durationMs = current.durationMs,
                    mime = "",
                    sourceUrl = null,
                    createdAt = 0,
                ),
                filesDir = filesDir,
                modifier = Modifier.size(40.dp),
            )
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    current.title,
                    color = colors.ink,
                    maxLines = 1,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${formatDuration(current.positionMs)} / ${formatDuration(current.durationMs)}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { player.toggle() }) {
                Icon(
                    if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (current.isPlaying) "Pause" else "Play",
                    tint = colors.ink,
                )
            }
            IconButton(onClick = { player.next() }) {
                Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.ink)
            }
        }
        val fraction = if (current.durationMs > 0) current.positionMs.toFloat() / current.durationMs else 0f
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp),
            color = colors.accent,
            trackColor = colors.panel2,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(player: PlayerRepository, filesDir: File, onClose: () -> Unit) {
    val colors = LocalAppColors.current
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val queue by player.queue.collectAsStateWithLifecycle(initialValue = emptyList())
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val current = now

    ModalBottomSheet(onDismissRequest = onClose, containerColor = colors.canvas) {
        if (current == null) {
            Text("Nothing playing", color = colors.muted, modifier = Modifier.padding(24.dp))
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            if (current.isVideo) {
                val exo = PlaybackService.currentPlayer
                if (exo != null) {
                    AndroidView(
                        factory = { ctx ->
                            PlayerView(ctx).apply {
                                this.player = exo
                                useController = false
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    )
                }
            }
            Text(
                current.title,
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp),
            )
            val duration = current.durationMs.coerceAtLeast(1)
            Slider(
                value = scrubbing ?: (current.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { scrubbing = it },
                onValueChangeFinished = {
                    scrubbing?.let { player.seekTo((it * duration).toLong()) }
                    scrubbing = null
                },
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(current.positionMs), color = colors.muted)
                Text(formatDuration(current.durationMs), color = colors.muted)
            }
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = colors.ink)
                }
                IconButton(onClick = { player.toggle() }) {
                    Icon(
                        if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = colors.accent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.ink)
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.QueueMusic, contentDescription = null, tint = colors.muted)
                Text(
                    "Queue · ${queue.size}",
                    color = colors.muted,
                    modifier = Modifier.padding(start = 8.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { player.stop(); onClose() }) {
                    Icon(Icons.Default.Close, contentDescription = "Stop", tint = colors.muted)
                }
            }
            LazyColumn(Modifier.height(220.dp)) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (item.id == current.itemId) colors.accent else colors.panel2),
                        )
                        Text(
                            item.title,
                            color = if (item.id == current.itemId) colors.accent else colors.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        IconButton(onClick = {
                            val ids = queue.map { it.id }.toMutableList()
                            if (index == 0) return@IconButton
                            val moved = ids.removeAt(index)
                            ids.add(index - 1, moved)
                            player.reorderQueue(ids)
                        }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move up", tint = colors.muted) }
                        IconButton(onClick = {
                            val ids = queue.map { it.id }.toMutableList()
                            if (index == queue.lastIndex) return@IconButton
                            val moved = ids.removeAt(index)
                            ids.add(index + 1, moved)
                            player.reorderQueue(ids)
                        }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move down", tint = colors.muted) }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
