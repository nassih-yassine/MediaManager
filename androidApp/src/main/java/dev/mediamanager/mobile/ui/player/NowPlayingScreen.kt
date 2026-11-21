package dev.mediamanager.mobile.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.media.AudioOutputMonitor
import dev.mediamanager.mobile.media.AudioRoute
import dev.mediamanager.mobile.media.VolumeControl
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.playback.playbackSpeeds
import dev.mediamanager.mobile.playback.speedLabel
import dev.mediamanager.mobile.ui.adaptive.HeightClass
import dev.mediamanager.mobile.ui.adaptive.LocalWindowInfo
import dev.mediamanager.mobile.ui.components.Scrubber
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/**
 * Full-screen player. Artwork on top, a frosted panel underneath with the transport,
 * volume, output route and the up-next queue. Drag the artwork down to leave.
 */
@Composable
fun NowPlayingScreen(
    player: PlayerRepository,
    filesDir: File,
    audio: AudioOutputMonitor,
    volume: VolumeControl,
    onClose: () -> Unit,
    /** Replaces the square artwork; the video slot brings its own aspect ratio. */
    mediaSlot: (@Composable () -> Unit)? = null,
) {
    val colors = LocalAppColors.current
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val resumePoint by player.resumePrompt.collectAsStateWithLifecycle(initialValue = null)
    val queue by player.queue.collectAsStateWithLifecycle(initialValue = emptyList())
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val route by audio.route.collectAsStateWithLifecycle()
    var volumeLevel by remember { mutableIntStateOf(volume.current) }
    // Kept so the speaker glyph can bring the volume back to what it was.
    var rememberedLevel by remember { mutableIntStateOf(volume.current.coerceAtLeast(volume.max / 2)) }
    val scope = rememberCoroutineScope()
    val dragOffset = remember { Animatable(0f) }
    val dismissAt = with(LocalDensity.current) { 120.dp.toPx() }

    val current = now ?: return
    // Registered only with content: an empty overlay must not eat the Back press.
    BackHandler(onBack = onClose)

    var volumeTouching by remember { mutableStateOf(false) }
    val sleepMinutes by player.sleepMinutes.collectAsStateWithLifecycle(initialValue = 0)
    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2_000)
            // Never overwrite the thumb while a finger is on the volume row.
            if (!volumeTouching) volumeLevel = volume.current
        }
    }

    Box(Modifier.fillMaxSize().background(colors.canvas)) {
        val backdrop = current.thumbPath?.let { File(filesDir, it) }
        if (backdrop != null && backdrop.exists()) {
            coil.compose.AsyncImage(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(64.dp).graphicsLayer { alpha = 0.85f },
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to colors.canvas.copy(alpha = 0.05f),
                        0.45f to colors.canvas.copy(alpha = 0.30f),
                        1f to colors.canvas.copy(alpha = 0.65f),
                    ),
                ),
            )
        }

        resumePoint?.let { point ->
            ResumeDialog(
                positionMs = point.positionMs,
                onResume = { player.resumeFromPrompt() },
                onStartOver = { player.startOver() },
            )
        }

        val window = LocalWindowInfo.current
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    translationY = dragOffset.value
                    val leaving = (dragOffset.value / dismissAt.coerceAtLeast(1f)).coerceIn(0f, 1f)
                    alpha = 1f - ((leaving - 0.3f) / 0.7f).coerceIn(0f, 1f) * 0.95f
                    val shrink = 1f - leaving * 0.05f
                    scaleX = shrink
                    scaleY = shrink
                }
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(window) {
                        detectVerticalDragGestures(
                            onVerticalDrag = { _, delta ->
                                scope.launch {
                                    dragOffset.snapTo((dragOffset.value + delta).coerceAtLeast(0f))
                                }
                            },
                            onDragEnd = {
                                if (dragOffset.value > dismissAt) {
                                    onClose()
                                } else {
                                    scope.launch { dragOffset.animateTo(0f) }
                                }
                            },
                            onDragCancel = { scope.launch { dragOffset.animateTo(0f) } },
                        )
                    },
            ) {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close player", tint = colors.muted)
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
            }

            if (window.isWide) {
                // Any landscape: media left, controls right. Stacking them leaves the
                // transport panel under 150 dp, where the buttons fall below the fold.
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    MediaPane(
                        current = current,
                        filesDir = filesDir,
                        mediaSlot = mediaSlot,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(vertical = 8.dp),
                        boundedByHeight = true,
                    )
                    PlayerPanel(
                        player = player,
                        current = current,
                        queue = queue,
                        volume = volume,
                        audio = audio,
                        modifier = Modifier.weight(1f).fillMaxHeight().padding(start = 12.dp),
                    )
                }
            } else {
                Spacer(Modifier.height(4.dp))
                if (window.heightClass == HeightClass.SHORT) {
                    // A landscape phone has ~411 dp: the square artwork used to eat
                    // all of it, leaving the transport panel at zero height.
                    MediaPane(
                        current = current,
                        filesDir = filesDir,
                        mediaSlot = mediaSlot,
                        modifier = Modifier.fillMaxWidth().height(168.dp),
                        boundedByHeight = true,
                    )
                } else {
                    MediaPane(
                        current = current,
                        filesDir = filesDir,
                        mediaSlot = mediaSlot,
                        modifier = Modifier.fillMaxWidth(),
                        boundedByHeight = false,
                    )
                }
                Spacer(Modifier.height(12.dp))
                PlayerPanel(
                    player = player,
                    current = current,
                    queue = queue,
                    volume = volume,
                    audio = audio,
                    modifier = Modifier.fillMaxWidth().weight(1f),
                )
            }
        }
    }
}

/** Square artwork, or the video slot, sized by whichever axis is scarce. */
@Composable
private fun MediaPane(
    current: NowPlaying,
    filesDir: File,
    mediaSlot: (@Composable () -> Unit)?,
    modifier: Modifier,
    boundedByHeight: Boolean,
) {
    val colors = LocalAppColors.current
    Box(modifier, contentAlignment = Alignment.Center) {
        if (mediaSlot != null) {
            mediaSlot()
        } else {
            Artwork(
                current = current,
                filesDir = filesDir,
                placeholder = colors.panel2,
                glyphTint = colors.muted,
                modifier = if (boundedByHeight) {
                    Modifier.fillMaxHeight().aspectRatio(1f)
                } else {
                    Modifier.fillMaxWidth().aspectRatio(1f)
                },
            )
        }
    }
}

/**
 * Transport, volume, route and queue. A LazyColumn so a short landscape pane scrolls
 * instead of clipping, and a long queue only composes the rows on screen.
 */
@Composable
private fun PlayerPanel(
    player: PlayerRepository,
    current: NowPlaying,
    queue: List<Item>,
    volume: VolumeControl,
    audio: AudioOutputMonitor,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    val route by audio.route.collectAsStateWithLifecycle()
    var volumeLevel by remember { mutableIntStateOf(volume.current) }
    var rememberedLevel by remember { mutableIntStateOf(volume.current.coerceAtLeast(volume.max / 2)) }
    var volumeTouching by remember { mutableStateOf(false) }
    val sleepMinutes by player.sleepMinutes.collectAsStateWithLifecycle(initialValue = 0)

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(2_000)
            if (!volumeTouching) volumeLevel = volume.current
        }
    }

    LazyColumn(
        modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .background(colors.panel.copy(alpha = 0.38f))
            .border(
                width = 0.5.dp,
                color = colors.line.copy(alpha = 0.85f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        item {
            Text(
                current.title,
                color = colors.ink,
                maxLines = 2,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                if (current.isVideo) "Video" else "Audio",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
            )
        }

        item {
            val duration = current.durationMs.coerceAtLeast(1)
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
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 2.dp),
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
        }

        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                val muted = volumeLevel == 0
                IconButton(
                    onClick = {
                        if (muted) {
                            volumeLevel = rememberedLevel.coerceAtLeast(1)
                        } else {
                            rememberedLevel = volumeLevel
                            volumeLevel = 0
                        }
                        volume.set(volumeLevel)
                    },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        when {
                            muted -> Icons.Default.VolumeOff
                            volumeLevel < volume.max / 2 -> Icons.Default.VolumeDown
                            else -> Icons.Default.VolumeUp
                        },
                        contentDescription = if (muted) "Unmute" else "Mute",
                        tint = if (muted) colors.accent else colors.muted,
                        modifier = Modifier.size(20.dp),
                    )
                }
                Scrubber(
                    value = volumeLevel.toFloat() / volume.max,
                    onScrub = { fraction ->
                        volumeTouching = true
                        volumeLevel = (fraction * volume.max).toInt().coerceIn(0, volume.max)
                        if (volumeLevel > 0) rememberedLevel = volumeLevel
                        volume.set(volumeLevel)
                    },
                    onCommit = {
                        volume.set(volumeLevel)
                        volumeTouching = false
                    },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Icon(
                    when (route) {
                        AudioRoute.BLUETOOTH -> Icons.Default.Bluetooth
                        AudioRoute.HEADPHONES -> Icons.Default.Headphones
                        AudioRoute.USB -> Icons.Default.Usb
                        else -> Icons.Default.PhoneAndroid
                    },
                    contentDescription = route.label,
                    tint = if (route == AudioRoute.SPEAKER) colors.muted else colors.accent,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Up next · ${queue.size}",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.weight(1f))
                SelectMenu(
                    label = "Sleep",
                    options = listOf(0, 15, 30, 45, 60).map { it to if (it == 0) "Sleep off" else "$it min" },
                    selected = sleepMinutes,
                    onSelect = { player.setSleepTimer(it) },
                )
                Spacer(Modifier.size(8.dp))
                SelectMenu(
                    label = "Speed",
                    options = playbackSpeeds.map { it to speedLabel(it) },
                    selected = current.speed,
                    onSelect = { player.setSpeed(it) },
                )
            }
        }

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
                IconButton(
                    onClick = { player.removeFromQueue(item.id) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Remove from queue",
                        tint = colors.muted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun Artwork(
    current: NowPlaying,
    filesDir: File,
    placeholder: androidx.compose.ui.graphics.Color,
    glyphTint: androidx.compose.ui.graphics.Color,
    modifier: Modifier,
) {
    val frame = modifier
        .clip(RoundedCornerShape(20.dp))
        .background(placeholder)

    val cover = current.thumbPath?.let { File(filesDir, it) }
    Box(frame, contentAlignment = Alignment.Center) {
        if (cover != null && cover.exists()) {
            coil.compose.AsyncImage(
                model = cover,
                contentDescription = current.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                if (current.isVideo) Icons.Default.Videocam else Icons.Default.MusicNote,
                contentDescription = null,
                tint = glyphTint,
                modifier = Modifier.size(64.dp),
            )
        }
    }
}
