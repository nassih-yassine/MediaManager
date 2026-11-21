package dev.mediamanager.mobile.ui.add

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.DownloadService
import dev.mediamanager.mobile.download.PlaylistEntry
import dev.mediamanager.mobile.download.PlaylistSummary
import dev.mediamanager.mobile.download.playlistListId
import dev.mediamanager.mobile.ui.components.MmSurface
import dev.mediamanager.mobile.ui.components.MmTextButton
import dev.mediamanager.mobile.ui.components.SectionTitle
import dev.mediamanager.mobile.ui.theme.LocalAppColors
import kotlinx.coroutines.launch

@androidx.compose.material3.ExperimentalMaterial3Api
@Composable
fun AddScreen(
    container: AppContainer,
    player: dev.mediamanager.mobile.playback.PlayerRepository,
    onOpenDownloads: () -> Unit = {},
    sharedUrl: String? = null,
    onSharedHandled: () -> Unit = {},
) {
    val colors = LocalAppColors.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var form by remember { mutableStateOf(AddFormState()) }
    var message by remember { mutableStateOf<String?>(null) }
    var sharedSheet by remember { mutableStateOf(false) }
    var inspected by remember { mutableStateOf<PlaylistSummary?>(null) }
    var choosingPlaylist by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var inspecting by remember { mutableStateOf(false) }
    val importRunner = remember(container) {
        PlaylistImportRunner(container.db, container.jobQueue) {
            DownloadService.start(container.appContext)
        }
    }
    val inspectPlaylist: (String, DownloadMode) -> Unit = { url, mode ->
        val validated = AddFormState(url = url, mode = mode).validate()
        form = validated
        inspecting = true
        scope.launch {
            val summary = kotlinx.coroutines.withTimeoutOrNull(60_000) {
                runCatching { container.playlistInspector.inspect(validated.url) }.getOrNull()
            }
            inspecting = false
            if (summary == null) {
                message = "Could not read that playlist. Check the link and try again."
            } else {
                inspected = summary
            }
        }
    }
    val startImport: (PlaylistSummary, List<PlaylistEntry>) -> Unit = { summary, chosen ->
        scope.launch {
            importing = true
            val outcome = importRunner.import(summary, form.mode, chosen)
            message = when {
                outcome.nothing -> "Nothing new: those videos are already queued."

                outcome.queued == 0 -> {
                    val plural = if (outcome.existing == 1) "video" else "videos"
                    "Added ${outcome.existing} existing $plural to “${summary.name}”."
                }

                outcome.existing > 0 -> "Queued ${outcome.queued} · ${outcome.existing} already in your library."

                else -> "Queued ${outcome.queued} from “${summary.name}”."
            }
            if (outcome.queued + outcome.existing > 0) container.settings.addRecentUrl(summary.listId)
            inspected = null
            choosingPlaylist = false
            importing = false
            form = AddFormState(mode = form.mode)
        }
    }

    androidx.compose.runtime.LaunchedEffect(sharedUrl) {
        if (sharedUrl != null) {
            form = form.copy(url = sharedUrl, error = null)
            sharedSheet = true
        }
    }
    val finished by container.db.jobDao().observeFinished().collectAsStateWithLifecycle(initialValue = emptyList())
    val activeJobs by container.db.jobDao().observeActive().collectAsStateWithLifecycle(initialValue = emptyList())
    val online by container.connectivity.observeOnline().collectAsStateWithLifecycle(initialValue = true)
    val active = activeJobs.firstOrNull { it.status == "running" } ?: activeJobs.firstOrNull()

    Column(
        Modifier
            .fillMaxSize()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Paste a link. YouTube or anything yt-dlp knows.",
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (active != null) {
                    if (active.progress > 0f) {
                        androidx.compose.material3.CircularProgressIndicator(
                            progress = { active.progress.coerceIn(0f, 1f) },
                            modifier = Modifier.size(42.dp),
                            color = colors.accent,
                            trackColor = colors.panel2,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        androidx.compose.material3.CircularProgressIndicator(
                            modifier = Modifier.size(42.dp),
                            color = colors.accent,
                            trackColor = colors.panel2,
                            strokeWidth = 2.dp,
                        )
                    }
                }
                IconButton(onClick = onOpenDownloads) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = "Downloads",
                        tint = if (active != null) colors.accent else colors.muted,
                    )
                }
            }
        }

        if (!online && activeJobs.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.CloudOff,
                    contentDescription = null,
                    tint = colors.danger,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    "You're offline. Downloads are paused and resume automatically.",
                    color = colors.danger,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
            }
        }

        if (active != null) {
            Text(
                buildString {
                    append("Downloading")
                    active.stage?.let { append(" · ").append(it) }
                    if (active.progress > 0f) append(" · ").append((active.progress * 100).toInt()).append("%")
                },
                color = colors.accent,
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        Spacer(Modifier.height(12.dp))
        MmSurface(Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = form.url,
                onValueChange = {
                    form = form.copy(url = it, error = null)
                    message = null
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtube.com/watch?v=…") },
                isError = form.error != null,
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(12.dp),
                keyboardOptions = KeyboardOptions.Default,
                trailingIcon = {
                    IconButton(onClick = {
                        clipboard.getText()?.text?.let {
                            form = form.copy(url = it, error = null)
                            message = null
                        }
                    }) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste", tint = colors.accent)
                    }
                },
            )
            form.error?.let {
                Text(
                    it,
                    color = colors.danger,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        Text(
            "Format",
            color = colors.ink,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ModeCard(
                icon = Icons.Default.MusicNote,
                title = "Audio",
                subtitle = "mp3 with cover art",
                selected = form.mode == DownloadMode.AUDIO,
                modifier = Modifier.weight(1f),
                onClick = { form = form.copy(mode = DownloadMode.AUDIO) },
            )
            ModeCard(
                icon = Icons.Default.Videocam,
                title = "Video",
                subtitle = "mp4",
                selected = form.mode == DownloadMode.VIDEO,
                modifier = Modifier.weight(1f),
                onClick = { form = form.copy(mode = DownloadMode.VIDEO) },
            )
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
                if (validated.sourcePlaylistId != null) {
                    form = validated.copy(submitting = false)
                    inspectPlaylist(validated.url, validated.mode)
                    return@Button
                }
                scope.launch {
                    val result = container.jobQueue.enqueue(validated.url, validated.mode)
                    container.settings.addRecentUrl(validated.url)
                    DownloadService.start(container.appContext)
                    message = when {
                        result.inLibrary -> "Already in your library."
                        result.duplicate -> "Already in the downloads."
                        else -> "Queued. It keeps going in the background."
                    }
                    form = AddFormState(mode = validated.mode)
                }
            },
            enabled = !form.submitting && !importing && !inspecting,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = colors.accent,
                contentColor = colors.accentInk,
            ),
        ) {
            if (inspecting) {
                androidx.compose.material3.CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.accentInk,
                )
            } else {
                Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.size(8.dp))
            Text(
                when {
                    inspecting -> "Reading playlist…"
                    form.mode == DownloadMode.AUDIO -> "Download audio"
                    else -> "Download video"
                },
            )
        }

        message?.let {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = colors.accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(8.dp))
                Text(it, color = colors.accent, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
            }
        }

        if (finished.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Recent downloads")
                Spacer(Modifier.weight(1f))
                MmTextButton(
                    "Clear",
                    Icons.Default.DeleteSweep,
                    color = colors.muted,
                    onClick = { scope.launch { container.db.jobDao().clearFinished() } },
                )
            }
            MmSurface(Modifier.fillMaxWidth()) {
                finished.take(4).forEachIndexed { index, job ->
                    val failed = job.status == "failed"
                    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Icon(
                                if (failed) Icons.Default.ErrorOutline else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (failed) colors.danger else colors.accent,
                                modifier = Modifier.size(18.dp).padding(top = 2.dp),
                            )
                            Column(Modifier.weight(1f).padding(start = 10.dp)) {
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
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(start = 28.dp),
                            horizontalArrangement = Arrangement.End,
                        ) {
                            if (failed) {
                                IconButton(
                                    onClick = {
                                        scope.launch {
                                            container.jobQueue.enqueue(
                                                job.url,
                                                DownloadMode.from(job.mode),
                                            )
                                            container.db.jobDao().deleteFinished(job.id)
                                            DownloadService.start(container.appContext)
                                            message = "Retrying"
                                        }
                                    },
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Retry download",
                                        tint = colors.accent,
                                    )
                                }
                            }
                            MmTextButton(
                                "Dismiss",
                                Icons.Default.Close,
                                color = colors.muted,
                                onClick = {
                                    scope.launch { container.db.jobDao().deleteFinished(job.id) }
                                },
                            )
                        }
                    }
                    if (index != finished.take(4).lastIndex) {
                        HorizontalDivider(color = colors.line, modifier = Modifier.padding(start = 28.dp))
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    inspected?.let { summary ->
        if (choosingPlaylist) {
            PlaylistSelectSheet(
                summary = summary,
                onConfirm = { chosen -> startImport(summary, chosen) },
                onDismiss = { choosingPlaylist = false },
            )
        } else {
            PlaylistImportSheet(
                summary = summary,
                mode = form.mode,
                onMode = { form = form.copy(mode = it) },
                onAll = { startImport(summary, summary.entries) },
                onChoose = { choosingPlaylist = true },
                onDismiss = { inspected = null },
            )
        }
    }

    if (sharedSheet && sharedUrl != null) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = {
                sharedSheet = false
                onSharedHandled()
            },
            containerColor = colors.panel,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(
                    "Download this link?",
                    color = colors.ink,
                    style = androidx.compose.material3.MaterialTheme.typography.titleLarge,
                )
                Text(
                    dev.mediamanager.mobile.ui.jobLabel(sharedUrl),
                    color = colors.muted,
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Spacer(Modifier.height(12.dp))
                SharedChoice(
                    title = "Audio",
                    subtitle = "mp3 with cover art",
                    onClick = {
                        if (playlistListId(sharedUrl) != null) {
                            sharedSheet = false
                            onSharedHandled()
                            inspectPlaylist(sharedUrl, DownloadMode.AUDIO)
                            return@SharedChoice
                        }
                        scope.launch {
                            val result = container.jobQueue.enqueue(sharedUrl, DownloadMode.AUDIO)
                            container.settings.addRecentUrl(sharedUrl)
                            DownloadService.start(container.appContext)
                            message = if (result.duplicate) {
                                "Already in the downloads"
                            } else {
                                "Queued as audio"
                            }
                            form = AddFormState(mode = DownloadMode.AUDIO)
                            sharedSheet = false
                            onSharedHandled()
                        }
                    },
                )
                SharedChoice(
                    title = "Video",
                    subtitle = "mp4",
                    onClick = {
                        if (playlistListId(sharedUrl) != null) {
                            sharedSheet = false
                            onSharedHandled()
                            inspectPlaylist(sharedUrl, DownloadMode.VIDEO)
                            return@SharedChoice
                        }
                        scope.launch {
                            val result = container.jobQueue.enqueue(sharedUrl, DownloadMode.VIDEO)
                            container.settings.addRecentUrl(sharedUrl)
                            DownloadService.start(container.appContext)
                            message = if (result.duplicate) {
                                "Already in the downloads"
                            } else {
                                "Queued as video"
                            }
                            form = AddFormState(mode = DownloadMode.VIDEO)
                            sharedSheet = false
                            onSharedHandled()
                        }
                    },
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ModeCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = LocalAppColors.current
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) colors.accent.copy(alpha = 0.12f) else colors.panel)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) colors.accent else colors.line,
                shape = RoundedCornerShape(16.dp),
            )
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) colors.accent else colors.muted,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            title,
            color = colors.ink,
            style = androidx.compose.material3.MaterialTheme.typography.titleMedium,
        )
        Text(
            subtitle,
            color = colors.muted,
            style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun SharedChoice(title: String, subtitle: String, onClick: () -> Unit) {
    val colors = LocalAppColors.current
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = colors.ink, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
            Text(subtitle, color = colors.muted, style = androidx.compose.material3.MaterialTheme.typography.bodySmall)
        }
        Icon(Icons.Default.Download, contentDescription = null, tint = colors.accent)
    }
}
