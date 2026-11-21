package dev.mediamanager.mobile.ui.update

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.mediamanager.mobile.data.update.UpdateInfo
import dev.mediamanager.mobile.ui.theme.LocalAppColors

/**
 * Themed update prompt: version, short notes, one full-width accent action. "Later"
 * stores the tag so the same release does not ask again.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun UpdateSheet(
    info: UpdateInfo,
    onDownload: () -> Unit,
    onLater: () -> Unit,
) {
    val colors = LocalAppColors.current

    ModalBottomSheet(
        onDismissRequest = onLater,
        containerColor = colors.panel,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                "Update available",
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Text(
                "v${info.versionName} · you have ${dev.mediamanager.mobile.BuildConfig.VERSION_NAME}",
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
            )
            if (info.notes.isNotBlank()) {
                Text(
                    info.notes,
                    color = colors.muted,
                    maxLines = 6,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 16.dp),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
            }

            Button(
                onClick = onDownload,
                modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.accentInk,
                ),
            ) {
                Text("Download update")
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onLater) {
                    Text("Later", color = colors.muted)
                }
            }
            Text(
                "The APK downloads in your browser; Android will ask before installing it.",
                color = colors.muted,
                modifier = Modifier.padding(top = 4.dp),
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
        }
    }
}
