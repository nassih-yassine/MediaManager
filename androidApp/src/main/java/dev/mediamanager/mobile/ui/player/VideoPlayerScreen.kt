package dev.mediamanager.mobile.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.playback.playbackSpeeds
import dev.mediamanager.mobile.playback.speedLabel
import dev.mediamanager.mobile.ui.components.Scrubber
import dev.mediamanager.mobile.ui.components.SelectMenu
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTapGestures

/**
 * Picks the right surface for the current item: a video owns the screen edge to edge,
 * audio keeps the frosted artwork panel.
 */
@Composable
fun PlayerOverlay(
    player: PlayerRepository,
    filesDir: java.io.File,
    audio: dev.mediamanager.mobile.media.AudioOutputMonitor,
    volume: dev.mediamanager.mobile.media.VolumeControl,
    onClose: () -> Unit,
) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    var expanded by remember { mutableStateOf(false) }
    if (now?.isVideo == true && expanded) {
        VideoPlayerScreen(player = player, onClose = { expanded = false })
        return
    }
    NowPlayingScreen(
        player = player,
        filesDir = filesDir,
        audio = audio,
        volume = volume,
        onClose = onClose,
        mediaSlot = if (now?.isVideo == true) {
            { VideoSlot(player = player, onExpand = { expanded = true }) }
        } else {
            null
        },
    )
}

/**
 * Immersive video surface. Unlike the audio player, the frame owns the whole screen
 * and the transport floats over it, fading out while playback runs.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    player: PlayerRepository,
    onClose: () -> Unit,
) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val current = now ?: return
    val colors = LocalAppColors.current
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val configuration = LocalConfiguration.current

    var controlsVisible by remember { mutableStateOf(true) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    var zoomed by remember { mutableStateOf(false) }
    var seekFlash by remember { mutableStateOf<String?>(null) }

    BackHandler(onBack = onClose)

    // Hide the bars for the duration of the video and give them back on the way out.
    DisposableEffect(Unit) {
        val window = activity?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, it.decorView) }
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    LaunchedEffect(controlsVisible, current.isPlaying, scrubbing) {
        if (controlsVisible && current.isPlaying && scrubbing == null) {
            delay(3_200)
            controlsVisible = false
        }
    }

    LaunchedEffect(seekFlash) {
        if (seekFlash != null) {
            delay(650)
            seekFlash = null
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        val exo = PlaybackService.currentPlayer
        if (exo != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        keepScreenOn = true
                        this.player = exo
                    }
                },
                update = { view ->
                    view.resizeMode =
                        if (zoomed) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else AspectRatioFrameLayout.RESIZE_MODE_FIT
                    view.keepScreenOn = true
                },
                modifier = Modifier.fillMaxSize(),
            )
            DisposableEffect(exo) {
                onDispose { exo.clearVideoSurface() }
            }
        }

        // Tap toggles the chrome; double-tap on a side jumps ten seconds.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { controlsVisible = !controlsVisible },
                        onDoubleTap = { offset ->
                            val width = size.width.toFloat()
                            when {
                                offset.x < width / 3f -> {
                                    player.skipBy(-10_000)
                                    seekFlash = "−10s"
                                }
                                offset.x > width * 2f / 3f -> {
                                    player.skipBy(10_000)
                                    seekFlash = "+10s"
                                }
                                else -> controlsVisible = !controlsVisible
                            }
                        },
                    )
                },
        )

        seekFlash?.let { label ->
            Box(
                Modifier
                    .align(Alignment.Center)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 18.dp, vertical = 10.dp),
            ) {
                Text(label, color = Color.White)
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize(),
        ) {
            VideoChrome(
                current = current,
                scrubbing = scrubbing,
                zoomed = zoomed,
                landscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE,
                accent = colors.accent,
                accentInk = colors.accentInk,
                onClose = onClose,
                onScrub = { scrubbing = it },
                onCommitScrub = {
                    scrubbing?.let { player.seekTo((it * current.durationMs.coerceAtLeast(1)).toLong()) }
                    scrubbing = null
                },
                onToggleZoom = { zoomed = !zoomed },
                onToggleOrientation = {
                    val landscape =
                        activity?.resources?.configuration?.orientation ==
                            Configuration.ORIENTATION_LANDSCAPE
                    activity?.requestedOrientation = if (landscape) {
                        ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                    } else {
                        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }
                },
                player = player,
            )
        }
    }
}

@Composable
private fun VideoChrome(
    current: NowPlaying,
    scrubbing: Float?,
    zoomed: Boolean,
    landscape: Boolean,
    accent: Color,
    accentInk: Color,
    player: PlayerRepository,
    onClose: () -> Unit,
    onScrub: (Float) -> Unit,
    onCommitScrub: () -> Unit,
    onToggleZoom: () -> Unit,
    onToggleOrientation: () -> Unit,
) {
    val duration = current.durationMs.coerceAtLeast(1)
    val position = scrubbing?.times(duration)?.toLong() ?: current.positionMs

    Box(Modifier.fillMaxSize()) {
        // Scrims so the white icons survive a bright frame.
        Box(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Transparent,
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .align(Alignment.BottomCenter)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(
                current.title,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 12.dp),
                style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
            )
        }

        Row(
            Modifier.align(Alignment.Center),
            horizontalArrangement = Arrangement.spacedBy(28.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChromeButton(
                icon = Icons.Default.Replay10,
                description = "Back 10 seconds",
                size = 44.dp,
                onClick = { player.skipBy(-10_000) },
            )
            IconButton(
                onClick = { player.toggle() },
                modifier = Modifier.size(72.dp),
            ) {
                Icon(
                    if (current.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (current.isPlaying) "Pause" else "Play",
                    tint = accentInk,
                    modifier = Modifier
                        .size(68.dp)
                        .clip(RoundedCornerShape(34.dp))
                        .background(accent)
                        .padding(18.dp),
                )
            }
            ChromeButton(
                icon = Icons.Default.Forward10,
                description = "Forward 10 seconds",
                size = 44.dp,
                onClick = { player.skipBy(10_000) },
            )
        }

        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            Scrubber(
                value = scrubbing ?: (current.positionMs.toFloat() / duration).coerceIn(0f, 1f),
                onScrub = onScrub,
                onCommit = onCommitScrub,
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(formatDuration(position).ifEmpty { "0:00" }, color = Color.White.copy(alpha = 0.85f))
                Text(formatDuration(current.durationMs).ifEmpty { "--:--" }, color = Color.White.copy(alpha = 0.85f))
            }
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                SelectMenu(
                    label = speedLabel(current.speed),
                    options = playbackSpeeds.map { it to speedLabel(it) },
                    selected = current.speed,
                    onSelect = { player.setSpeed(it) },
                )
                Spacer(Modifier.weight(1f))
                ChromeButton(
                    icon = if (zoomed) Icons.Default.ZoomOut else Icons.Default.ZoomIn,
                    description = if (zoomed) "Fit to screen" else "Fill screen",
                    size = 40.dp,
                    onClick = onToggleZoom,
                )
                ChromeButton(
                    icon = if (landscape) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                    description = if (landscape) "Leave fullscreen" else "Fullscreen",
                    size = 40.dp,
                    onClick = onToggleOrientation,
                )
            }
        }
    }
}

@Composable
private fun ChromeButton(
    icon: ImageVector,
    description: String,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(size + 8.dp)) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(size - 12.dp))
    }
}

/** The activity behind a composable, unwrapping the ContextWrapper chain. */
internal fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}
