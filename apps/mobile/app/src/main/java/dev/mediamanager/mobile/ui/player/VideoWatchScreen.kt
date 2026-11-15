package dev.mediamanager.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.playback.playbackSpeeds
import dev.mediamanager.mobile.playback.speedLabel
import dev.mediamanager.mobile.ui.components.Scrubber
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * Video in portrait, the way YouTube shows it: a 16:9 box at the top with the
 * transport underneath and what is next below. Fullscreen swaps in the immersive
 * player; leaving that returns to the box.
 */
@Composable
fun VideoWatchScreen(
    player: PlayerRepository,
    onClose: () -> Unit,
) {
    val colors = LocalAppColors.current
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val queue by player.queue.collectAsStateWithLifecycle(initialValue = emptyList())
    var expanded by remember { mutableStateOf(false) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val current = now ?: return

    if (expanded) {
        VideoPlayerScreen(player = player, onClose = { expanded = false })
        return
    }

    val duration = current.durationMs.coerceAtLeast(1)
    Column(
        Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.muted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Now playing",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        VideoBox(
            player = player,
            onExpand = { expanded = true },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        )

        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Spacer(Modifier.height(12.dp))
            Text(
                current.title,
                color = colors.ink,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                "Video · ${formatDuration(current.durationMs).ifEmpty { "--:--" }}",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 2.dp),
            )

            Scrubber(
                value = scrubbing ?: (current.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onScrub = { scrubbing = it },
                onCommit = {
                    scrubbing?.let { player.seekTo((it * duration).toLong()) }
                    scrubbing = null
                },
                modifier = Modifier.padding(top = 6.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    formatDuration(scrubbing?.times(duration)?.toLong() ?: current.positionMs).ifEmpty { "0:00" },
                    color = colors.muted,
                )
                Text(formatDuration(current.durationMs).ifEmpty { "--:--" }, color = colors.muted)
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
                IconButton(onClick = { player.previous() }) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "Previous", tint = colors.ink)
                }
                IconButton(onClick = { player.skipBy(-10_000) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "Back 10 seconds", tint = colors.ink)
                }
                IconButton(onClick = { player.toggle() }) {
                    Icon(
                        if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (current.isPlaying) "Pause" else "Play",
                        tint = colors.accentInk,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(colors.accent)
                            .padding(14.dp),
                    )
                }
                IconButton(onClick = { player.skipBy(10_000) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "Forward 10 seconds", tint = colors.ink)
                }
                IconButton(onClick = { player.next() }) {
                    Icon(Icons.Default.SkipNext, contentDescription = "Next", tint = colors.ink)
                }
                IconButton(onClick = { player.cycleRepeat() }) {
                    Icon(
                        if (current.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                        contentDescription = "Repeat",
                        tint = if (current.repeatMode == Player.REPEAT_MODE_OFF) colors.muted else colors.accent,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Up next · ${queue.size}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                SelectMenu(
                    label = "Speed",
                    options = playbackSpeeds.map { it to speedLabel(it) },
                    selected = current.speed,
                    onSelect = { player.setSpeed(it) },
                )
            }

            LazyColumn(Modifier.weight(1f).padding(top = 4.dp)) {
                itemsIndexed(queue, key = { _, item -> item.id }) { index, item ->
                    val active = item.id == current.itemId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { player.playItems(queue, index) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (active) colors.accent else colors.panel2),
                        )
                        Text(
                            item.title,
                            color = if (active) colors.accent else colors.ink,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            formatDuration(item.durationMs),
                            color = colors.muted,
                            style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        }
    }
}

/** The video itself: 16:9, rounded, black, with a fullscreen button in the corner. */
@Composable
private fun VideoBox(
    player: PlayerRepository,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val current = now ?: return
    val exo = PlaybackService.currentPlayer

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (exo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = exo
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
        // Tap anywhere on the frame to play or pause, YouTube-style.
        Box(
            Modifier
                .fillMaxSize()
                .clickable { player.toggle() },
            contentAlignment = Alignment.Center,
        ) {
            if (!current.isPlaying) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(28.dp))
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(12.dp),
                )
            }
        }
        IconButton(
            onClick = onExpand,
            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp),
        ) {
            Icon(
                Icons.Default.Fullscreen,
                contentDescription = "Fullscreen",
                tint = Color.White,
                modifier = Modifier
                    .size(28.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.35f))
                    .padding(3.dp),
            )
        }
    }
}
