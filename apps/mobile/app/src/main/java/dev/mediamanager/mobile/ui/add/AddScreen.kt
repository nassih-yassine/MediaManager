package dev.mediamanager.mobile.ui.add

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.HorizontalDivider
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.DownloadService
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.Segmented
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun AddScreen(container: AppContainer) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var form by remember { mutableStateOf(AddFormState()) }
    var message by remember { mutableStateOf<String?>(null) }
    val recent by container.settings.recentUrls.collectAsStateWithLifecycle(initialValue = emptyList())
    val finished by container.db.jobDao().observeFinished().collectAsStateWithLifecycle(initialValue = emptyList())

    Column(Modifier.fillMaxWidth().padding(16.dp)) {
        Text("Paste a link", color = colors.ink, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = form.url,
            onValueChange = { form = form.copy(url = it, error = null) },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://youtube.com/watch?v=…") },
            isError = form.error != null,
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            trailingIcon = {
                TextButton(onClick = {
                    clipboard.getText()?.text?.let { form = form.copy(url = it, error = null) }
                }) { Text("Paste", color = colors.accent) }
            },
        )
        form.error?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, color = colors.danger, style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Segmented(
                options = listOf(
                    DownloadMode.AUDIO.name to "Audio",
                    DownloadMode.VIDEO.name to "Video",
                ),
                selected = form.mode.name,
                onSelect = { form = form.copy(mode = DownloadMode.valueOf(it)) },
            )
            Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = {
                val validated = form.validate()
                form = validated.copy(submitting = true)
                if (validated.error != null) {
                    form = validated.copy(submitting = false)
                    return@Button
                }
                scope.launch {
                    val result = container.jobQueue.enqueue(validated.url, validated.mode)
                    container.settings.addRecentUrl(validated.url)
                    DownloadService.start(container.appContext)
                    message = if (result.duplicate) "Already in the queue" else "Queued · ${validated.mode.name.lowercase()}"
                    form = AddFormState(mode = validated.mode)
                }
            },
            enabled = !form.submitting,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (form.mode == DownloadMode.AUDIO) "Download audio" else "Download video") }

        message?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, color = colors.accent)
        }

        if (finished.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                SectionTitle("Recent downloads")
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { scope.launch { container.db.jobDao().clearFinished() } }) {
                    Text("Clear", color = colors.muted)
                }
            }
            finished.take(4).forEach { job ->
                val failed = job.status == "failed"
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.Top,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            dev.mediamanager.mobile.ui.jobLabel(job.url),
                            color = colors.ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            when {
                                failed -> job.error ?: "Failed"
                                job.status == "done" -> "Downloaded · " + job.mode
                                else -> "Cancelled"
                            },
                            color = if (failed) colors.danger else colors.muted,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = { scope.launch { container.db.jobDao().deleteFinished(job.id) } }) {
                        Text("Dismiss", color = colors.muted)
                    }
                }
            }
            HorizontalDivider(color = colors.line, modifier = Modifier.padding(vertical = 8.dp))
        }

        if (recent.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            SectionTitle("Recent links")
            recent.take(6).forEach { url ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { form = form.copy(url = url, error = null) }
                        .padding(vertical = 12.dp),
                ) {
                    Text(
                        url,
                        color = colors.muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                        style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "Audio keeps cover art and goes to mp3. Video keeps the picture and tops out " +
                "at the height in Settings.",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Downloads run in the background. Wi-Fi-only is in Settings.",
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        )
    }
}
