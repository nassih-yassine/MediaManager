package dev.mediamanager.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * A thin track with tap-to-seek and drag-to-scrub. No thumb: the filled end of the
 * bar is the whole indicator, which keeps the row quiet at any length.
 */
@Composable
fun Scrubber(
    value: Float,
    onScrub: (Float) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalAppColors.current
    var width by remember { mutableFloatStateOf(1f) }
    val clamped = value.coerceIn(0f, 1f)

    Box(
        modifier
            .fillMaxWidth()
            .height(28.dp)
            .onSizeChanged { width = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onScrub((offset.x / width).coerceIn(0f, 1f))
                    onCommit()
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset -> onScrub((offset.x / width).coerceIn(0f, 1f)) },
                    onHorizontalDrag = { change, _ ->
                        onScrub((change.position.x / width).coerceIn(0f, 1f))
                    },
                    onDragEnd = { onCommit() },
                    onDragCancel = { onCommit() },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.panel2),
        )
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(colors.accent),
        )
    }
}

/**
 * A plain track-and-fill bar. Material's LinearProgressIndicator adds a stop dot at
 * the end, which showed up as a stray dot beside the fill.
 */
@Composable
fun MmProgressBar(
    progress: Float?,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 4.dp,
    trackColor: androidx.compose.ui.graphics.Color,
    fillColor: androidx.compose.ui.graphics.Color,
) {
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(height / 2))
            .background(trackColor),
    ) {
        val fraction = progress?.coerceIn(0f, 1f)
        if (fraction != null) {
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(height)
                    .background(fillColor),
            )
        } else {
            // Waiting for the first byte: a faint full track reads better than a frozen 0%.
            Box(
                Modifier
                    .fillMaxWidth(0.35f)
                    .height(height)
                    .background(fillColor.copy(alpha = 0.55f)),
            )
        }
    }
}
