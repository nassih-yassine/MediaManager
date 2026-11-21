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
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.PlayerView
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.playback.playbackSpeeds
import dev.mediamanager.mobile.playback.speedLabel
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.components.Thumb
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.io.File

@Composable
fun MiniPlayer(
    player: PlayerRepository,
    filesDir: File,
    onOpen: () -> Unit,
    /** One short row for landscape phones, where height is the scarce axis. */
    compact: Boolean = false,
) {
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

    if (compact) {
        val fraction = if (current.durationMs > 0) current.positionMs.toFloat() / current.durationMs else 0f
        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.panel.copy(alpha = 0.85f))
                .padding(horizontal = 12.dp, vertical = 4.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onOpen),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Thumb(item = item, filesDir = filesDir, modifier = Modifier.size(36.dp))
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(
                        current.title,
                        color = colors.ink,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "${formatDuration(current.positionMs).ifEmpty { "0:00" }} / " +
                            formatDuration(current.durationMs).ifEmpty { "--:--" },
                        color = colors.muted,
                        maxLines = 1,
                        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                    )
                }
                IconButton(onClick = { player.toggle() }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (current.isPlaying) "Pause" else "Play",
                        tint = colors.accent,
                    )
                }
                IconButton(onClick = { player.next() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.ink)
                }
                IconButton(onClick = { player.stop() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Stop and close player", tint = colors.muted)
                }
            }
            dev.mediamanager.mobile.ui.components.MmProgressBar(
                progress = fraction.coerceIn(0f, 1f),
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                height = 2.dp,
                trackColor = colors.panel2,
                fillColor = colors.accent,
            )
        }
        return
    }

    Column(
        Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                clip = false,
                ambientColor = colors.accent.copy(alpha = 0.16f),
                spotColor = colors.accent.copy(alpha = 0.22f),
            )
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
            .background(colors.panel.copy(alpha = 0.72f))
            .border(
                width = 0.5.dp,
                color = colors.line.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
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
                    "${formatDuration(current.positionMs).ifEmpty { "0:00" }} / " +
                        formatDuration(current.durationMs).ifEmpty { "--:--" },
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
            }
            IconButton(onClick = { player.stop() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Stop and close player", tint = colors.muted)
            }
        }

        val fraction = if (current.durationMs > 0) current.positionMs.toFloat() / current.durationMs else 0f
        dev.mediamanager.mobile.ui.components.MmProgressBar(
            progress = fraction.coerceIn(0f, 1f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            height = 3.dp,
            trackColor = colors.panel2,
            fillColor = colors.accent,
        )

        Row(
            Modifier.fillMaxWidth(),
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
                icon = if (current.repeatMode ==
                    androidx.media3.common.Player.REPEAT_MODE_ONE
                ) {
                    Icons.Default.RepeatOne
                } else {
                    Icons.Default.Repeat
                },
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
