package dev.mediamanager.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.compose.runtime.key
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.playback.PlayerRepository

/**
 * The video that fills the artwork slot on the big player. Square frame, black,
 * letterboxed, with the fullscreen affordance in the corner.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoSlot(
    player: PlayerRepository,
    onExpand: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val now by player.nowPlaying.collectAsStateWithLifecycle(initialValue = null)
    val current = now ?: return
    val exo = PlaybackService.currentPlayer

    // Match the real video shape so there are never letterbox bars.
    var ratio by remember { mutableFloatStateOf(16f / 9f) }
    DisposableEffect(exo) {
        val known = exo?.videoSize
        if (known != null && known.width > 0 && known.height > 0) {
            ratio = (known.width * known.pixelWidthHeightRatio) / known.height
        }
        val listener = object : Player.Listener {
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    ratio = (size.width * size.pixelWidthHeightRatio) / size.height
                }
            }
        }
        exo?.addListener(listener)
        onDispose { exo?.removeListener(listener) }
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        if (exo != null) {
            // The service can recreate its ExoPlayer; key() rebuilds the view so the
            // pane follows the new instance instead of holding a released one.
            key(exo) {
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
        }
        Box(
            Modifier.fillMaxSize().clickable { player.toggle() },
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
