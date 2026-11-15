package dev.mediamanager.mobile.ui.downloads

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.DownloadService
import dev.mediamanager.mobile.ui.components.ConfirmDialog
import dev.mediamanager.mobile.ui.components.EmptyState
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@Composable
fun DownloadsScreen(
    container: AppContainer,
    player: dev.mediamanager.mobile.playback.PlayerRepository,
    onClose: () -> Unit,
) {
    androidx.activity.compose.BackHandler(onBack = onClose)
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    // Opening the queue is also the explicit "resume" gesture after a system kill.
    LaunchedEffect(Unit) {
        if (container.db.jobDao().activeJobs().isNotEmpty()) DownloadService.start(container.appContext)
    }
    val active by container.db.jobDao().observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val finished by container.db.jobDao().observeFinished().collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
        SectionTitle("Active")
        if (active.isEmpty()) {
            Text("Nothing downloading.", color = colors.muted)
        }
        active.forEach { job ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        job.url.substringAfterLast('/').ifBlank { job.url },
                        color = colors.ink,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    MmTextButton(
                        "Cancel",
                        Icons.Default.Close,
                        color = colors.danger,
                        onClick = {
                            DownloadService.cancel(container.appContext)
                            scope.launch { container.jobQueue.cancel(job.id) }
                        },
                    )
                }
                Text(
                    "${job.mode} · ${job.stage ?: job.status} · ${(job.progress * 100).toInt()}%" +
                        (job.error?.let { " · $it" } ?: ""),
                    color = if (job.error != null) colors.danger else colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    maxLines = 3,
                )
                if (job.stage == "downloading" && job.progress <= 0f) {
                    // YouTube often sends no Content-Length; an indeterminate bar is
                    // honest, a frozen 0% looks broken.
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = colors.accent,
                        trackColor = colors.panel2,
                    )
                } else {
                    LinearProgressIndicator(
                        progress = { job.progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        color = colors.accent,
                        trackColor = colors.panel2,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Finished")
            Spacer(Modifier.weight(1f))
            if (finished.isNotEmpty()) {
                MmTextButton("Clear", Icons.Default.DeleteSweep, color = colors.muted, onClick = { confirmClear = true })
            }
        }
        if (finished.isEmpty()) {
            Text("Finished downloads land in the library.", color = colors.muted)
        }
        finished.forEach { job -> FinishedRow(container, job, player) }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear finished?",
            body = "Removes the done, failed and cancelled entries. The downloaded media stays in the library.",
            confirmLabel = "Clear",
            onConfirm = { scope.launch { container.db.jobDao().clearFinished() } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun FinishedRow(
    container: AppContainer,
    job: JobEntity,
    player: dev.mediamanager.mobile.playback.PlayerRepository,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                job.url.substringAfterLast('/').ifBlank { job.url },
                color = colors.ink,
                maxLines = 1,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Text(
                when (job.status) {
                    "done" -> "Done · ${job.mode}"
                    "failed" -> job.error ?: "Failed"
                    else -> "Cancelled"
                },
                color = if (job.status == "failed") colors.danger else colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                maxLines = 2,
            )
        }
        if (job.status == "done") {
            MmTextButton("Open", Icons.Default.PlayArrow, onClick = {
                scope.launch {
                    job.itemId?.let { id ->
                        container.db.itemDao().get(id)?.let { item ->
                            player.playItems(listOf(item.toItem()), 0)
                        }
                    }
                }
            })
        }
        if (job.status == "failed") {
            MmTextButton("Retry", Icons.Default.Refresh, onClick = {
                scope.launch {
                    container.jobQueue.enqueue(job.url, DownloadMode.from(job.mode))
                    container.db.jobDao().deleteFinished(job.id)
                    DownloadService.start(container.appContext)
                }
            })
        }
        MmTextButton(
            "Dismiss",
            Icons.Default.Close,
            color = colors.muted,
            onClick = { scope.launch { container.db.jobDao().deleteFinished(job.id) } },
        )
    }
}
