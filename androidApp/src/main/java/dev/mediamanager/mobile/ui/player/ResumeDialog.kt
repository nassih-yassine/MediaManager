package dev.mediamanager.mobile.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * Only long media asks this. Short clips restart silently, so the question never
 * becomes a tax on listening to a three-minute track.
 */
@Composable
fun ResumeDialog(
    positionMs: Long,
    onResume: () -> Unit,
    onStartOver: () -> Unit,
) {
    val colors = LocalAppColors.current
    val at = formatDuration(positionMs).ifEmpty { "0:00" }

    Dialog(onDismissRequest = onStartOver) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(colors.panel)
                .border(1.dp, colors.line, RoundedCornerShape(24.dp))
                .padding(20.dp),
        ) {
            Text(
                "Continue where you stopped?",
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                "You were at $at.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(18.dp))
            Row(Modifier.fillMaxWidth()) {
                TextButton(
                    onClick = onStartOver,
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Start over", color = colors.muted)
                }
                Button(
                    onClick = onResume,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = colors.accent,
                        contentColor = colors.accentInk,
                    ),
                ) {
                    Text("Resume")
                }
            }
        }
    }
}
