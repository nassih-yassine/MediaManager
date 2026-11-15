package dev.mediamanager.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
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
import androidx.media3.common.Player
import dev.mediamanager.mobile.playback.playbackSpeeds
import dev.mediamanager.mobile.playback.speedLabel
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.io.File

@Composable
fun MiniPlayer(player: PlayerRepository, filesDir: File, onOpen: () -> Unit) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val current = now ?: return
    val colors = LocalAppColors.current
    val item = dev.mediamanager.mobile.data.Item(
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
    )

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.panel.copy(alpha = 0.88f))
            .border(
                width = 0.5.dp,
                color = colors.line.copy(alpha = 0.7f),
                shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onOpen)
                .padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Thumb(item = item, filesDir = filesDir, modifier = Modifier.size(44.dp))
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    current.title,
                    color = colors.ink,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "${formatDuration(current.positionMs)} / ${formatDuration(current.durationMs)}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { player.stop() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Stop and close player", tint = colors.muted)
            }
        }

        val fraction = if (current.durationMs > 0) current.positionMs.toFloat() / current.durationMs else 0f
        LinearProgressIndicator(
            progress = { fraction.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(2.dp).padding(top = 2.dp),
            color = colors.accent,
            trackColor = colors.panel2,
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniControl(
                icon = Icons.Default.Shuffle,
                description = "Shuffle",
                tint = if (current.shuffle) colors.accent else colors.muted,
                onClick = { player.toggleShuffle() },
            )
            MiniControl(
                icon = Icons.Default.SkipPrevious,
                description = "Previous",
                tint = colors.ink,
                onClick = { player.previous() },
            )
            MiniControl(
                icon = Icons.Default.Replay10,
                description = "Back 10 seconds",
                tint = colors.ink,
                onClick = { player.skipBy(-10_000) },
            )
            IconButton(
                onClick = { player.toggle() },
                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(22.dp)).background(colors.accent),
            ) {
                Icon(
                    if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (current.isPlaying) "Pause" else "Play",
                    tint = colors.accentInk,
                )
            }
            MiniControl(
                icon = Icons.Default.Forward10,
                description = "Forward 10 seconds",
                tint = colors.ink,
                onClick = { player.skipBy(10_000) },
            )
            MiniControl(
                icon = Icons.Default.SkipNext,
                description = "Next",
                tint = colors.ink,
                onClick = { player.next() },
            )
            MiniControl(
                icon = if (current.repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                description = "Repeat",
                tint = if (current.repeatMode == androidx.media3.common.Player.REPEAT_MODE_OFF) colors.muted else colors.accent,
                onClick = { player.cycleRepeat() },
            )
        }
    }
}

@Composable
private fun MiniControl(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
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
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.toggleShuffle() }) {
                    Icon(
                        Icons.Default.Shuffle,
                        contentDescription = "Shuffle",
                        tint = if (current.shuffle) colors.accent else colors.muted,
                    )
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (current.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (current.repeatMode == Player.REPEAT_MODE_OFF) colors.muted else colors.accent,
                    )
                }
                SelectMenu(
                    label = "Speed",
                    options = playbackSpeeds.map { it to speedLabel(it) },
                    selected = current.speed,
                    onSelect = { player.setSpeed(it) },
                )
            }

            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = colors.ink)
                }
                IconButton(onClick = { player.skipBy(-10_000) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = colors.ink)
                }
                IconButton(onClick = { player.toggle() }) {
                    Icon(
                        if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Play or pause",
                        tint = colors.accent,
                        modifier = Modifier.size(40.dp),
                    )
                }
                IconButton(onClick = { player.skipBy(10_000) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = colors.ink)
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
                        Modifier.fillMaxWidth().padding(vertical = 8.dp),
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
                            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
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
