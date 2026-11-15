package dev.mediamanager.mobile.ui.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.DownloadService
import dev.mediamanager.mobile.playback.PlayerRepository
import dev.mediamanager.mobile.ui.components.ConfirmDialog
import dev.mediamanager.mobile.ui.components.MmProgressBar
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.format.formatBytes
import dev.mediamanager.mobile.ui.format.formatDuration
import dev.mediamanager.mobile.ui.jobLabel
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DownloadsScreen(
    container: AppContainer,
    player: PlayerRepository,
    onClose: () -> Unit,
) {
    BackHandler(onBack = onClose)
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    // Opening the queue is also the explicit "resume" gesture after a system kill.
    LaunchedEffect(Unit) {
        if (container.db.jobDao().activeJobs().isNotEmpty()) DownloadService.start(container.appContext)
    }
    val active by container.db.jobDao().observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val finished by container.db.jobDao().observeFinished().collectAsStateWithLifecycle(initialValue = emptyList())
    var confirmClear by remember { mutableStateOf(false) }

    // Finished rows are about the item, not the job: look up title, art and size.
    val items by produceState<Map<String, Item>>(emptyMap(), finished) {
        value = withContext(Dispatchers.IO) {
            val map = HashMap<String, Item>()
            finished.forEach { job ->
                job.itemId?.let { id -> container.db.itemDao().get(id)?.toItem()?.let { map[it.id] = it } }
            }
            map
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.muted)
            }
            Spacer(Modifier.weight(1f))
            Text(
                "Downloads",
                color = colors.ink,
                style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.size(48.dp))
        }

        SectionTitle(
            if (active.isEmpty()) "Active" else "Active · ${active.size}",
            Icons.Default.Download,
        )
        if (active.isEmpty()) {
            Text(
                "Nothing downloading. Queued links start here.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        active.forEach { job -> ActiveCard(container, job) }

        Spacer(Modifier.height(18.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            SectionTitle(if (finished.isEmpty()) "Finished" else "Finished · ${finished.size}")
            Spacer(Modifier.weight(1f))
            if (finished.isNotEmpty()) {
                MmTextButton("Clear", Icons.Default.DeleteSweep, color = colors.muted, onClick = { confirmClear = true })
            }
        }
        if (finished.isEmpty()) {
            Text(
                "Finished downloads land in the library.",
                color = colors.muted,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        finished.forEach { job ->
            FinishedCard(
                container = container,
                job = job,
                item = job.itemId?.let { items[it] },
                player = player,
            )
        }
        Spacer(Modifier.height(40.dp))
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "Clear finished?",
            body = "Removes the done, failed and cancelled entries. The downloaded media stays in the library.",
            confirmLabel = "Clear",
            icon = Icons.Default.DeleteSweep,
            onConfirm = { scope.launch { container.db.jobDao().clearFinished() } },
            onDismiss = { confirmClear = false },
        )
    }
}

@Composable
private fun ActiveCard(container: AppContainer, job: JobEntity) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val thumb = job.metaThumbPath?.let { File(container.appContext.filesDir, it) }
    val percent = (job.progress.coerceIn(0f, 1f) * 100).toInt()

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            if (thumb != null && thumb.exists()) {
                coil.compose.AsyncImage(
                    model = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    job.metaTitle ?: jobLabel(job.url),
                    color = colors.ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                    style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                )
                if (job.progress > 0f) {
                    Spacer(Modifier.size(8.dp))
                    Text(
                        "$percent%",
                        color = colors.accent,
                        style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Text(
                buildString {
                    append(if (DownloadMode.from(job.mode) == DownloadMode.VIDEO) "Video" else "Audio")
                    job.metaDurationMs?.let { append(" · ").append(formatDuration(it)) }
                    append(" · ").append(if (job.status == "queued" && job.error != null) "retrying" else job.stage ?: job.status)
                },
                color = colors.muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            )
            MmProgressBar(
                progress = if (job.stage == "downloading" && job.progress <= 0f) null else job.progress,
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                trackColor = colors.panel2,
                fillColor = colors.accent,
            )
        }
        IconButton(
            onClick = {
                DownloadService.cancel(container.appContext, job.id)
                scope.launch { container.jobQueue.cancel(job.id) }
            },
        ) {
            Icon(Icons.Default.Close, contentDescription = "Cancel download", tint = colors.danger)
        }
    }
}

@Composable
private fun FinishedCard(
    container: AppContainer,
    job: JobEntity,
    item: Item?,
    player: PlayerRepository,
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    val done = job.status == "done"
    val cover = item?.thumbPath?.let { File(container.appContext.filesDir, it) }
    val title = item?.title ?: job.metaTitle ?: jobLabel(job.url)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(colors.panel)
            .border(1.dp, colors.line, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(colors.panel2),
            contentAlignment = Alignment.Center,
        ) {
            when {
                cover != null && cover.exists() -> coil.compose.AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                item?.isAudio == false -> Icon(
                    Icons.Default.Videocam,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
                else -> Icon(
                    Icons.Default.MusicNote,
                    contentDescription = null,
                    tint = colors.muted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp)) {
            Text(
                title,
                color = colors.ink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            )
            Row(
                Modifier.padding(top = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                StatusChip(
                    label = when (job.status) {
                        "done" -> "Done"
                        "failed" -> "Failed"
                        else -> "Cancelled"
                    },
                    tint = when (job.status) {
                        "done" -> colors.accent
                        "failed" -> colors.danger
                        else -> colors.muted
                    },
                )
                Text(
                    buildString {
                        append(if (DownloadMode.from(job.mode) == DownloadMode.VIDEO) "Video" else "Audio")
                        item?.sizeBytes?.takeIf { it > 0 }?.let { append(" · ").append(formatBytes(it)) }
                        if (!done) job.error?.let { append(" · ").append(it) }
                    },
                    color = colors.muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                )
            }
        }

        Box {
            IconButton(onClick = { menu = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = colors.muted)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (done) {
                    DropdownMenuItem(
                        text = { Text("Play") },
                        leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                        onClick = {
                            menu = false
                            item?.let { player.playItems(listOf(it), 0) }
                        },
                    )
                }
                if (!done) {
                    DropdownMenuItem(
                        text = { Text("Retry") },
                        leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                        onClick = {
                            menu = false
                            scope.launch {
                                val outcome = container.jobQueue.enqueue(job.url, DownloadMode.from(job.mode))
                                container.db.jobDao().deleteFinished(job.id)
                                if (!outcome.inLibrary) DownloadService.start(container.appContext)
                            }
                        },
                    )
                }
                DropdownMenuItem(
                    text = { Text("Dismiss", color = colors.muted) },
                    leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = colors.muted) },
                    onClick = {
                        menu = false
                        scope.launch { container.db.jobDao().deleteFinished(job.id) }
                    },
                )
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, tint: androidx.compose.ui.graphics.Color) {
    Text(
        label.uppercase(),
        color = tint,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}
