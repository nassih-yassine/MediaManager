package dev.mediamanager.mobile.download

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import dev.mediamanager.mobile.App
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.JobEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

class DownloadService : Service() {

    companion object {
        const val ACTION_CANCEL = "dev.mediamanager.mobile.CANCEL_DOWNLOAD"
        const val EXTRA_JOB_ID = "job_id"
        private const val TAG = "DownloadService"
        private const val MAX_PARALLEL_JOBS = 3

        private val _state = MutableStateFlow(DownloadState())
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }

        /** Cancels one download, or every running one when [jobId] is null. */
        fun cancel(context: Context, jobId: String? = null) {
            context.startService(
                Intent(context, DownloadService::class.java)
                    .setAction(ACTION_CANCEL)
                    .putExtra(EXTRA_JOB_ID, jobId),
            )
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workerId = UUID.randomUUID().toString()

    /** Downloads in flight, keyed by job id. Guarded by [poolLock]. */
    private val poolLock = Any()
    private val pool = LinkedHashMap<String, ActiveDownload>()

    /** Pipeline runs, so a cancel can actually stop the work. */
    private val runs = Collections.synchronizedMap(HashMap<String, Job>())

    private var waitingForWifi = false
    private var waitingForNetwork = false

    /**
     * One loop per service. Every `startService` (app open, Add, Wi-Fi return) would
     * otherwise launch another loop; the first one to finish calls stopSelf and kills
     * a download that is still running.
     */
    private var loop: Job? = null

    private class ActiveDownload(
        var title: String,
        var progress: Float,
        var stage: String? = null,
    ) {
        var lastBeatAt: Long = 0
        var lastNotifyAt: Long = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        QueueNotifications.channel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val target = intent.getStringExtra(EXTRA_JOB_ID)
            scope.launch {
                val ids = if (target != null) listOf(target) else activeIds()
                ids.forEach { cancelOne(it) }
            }
            return START_NOT_STICKY
        }
        startForegroundCompat(QueueNotifications.build(this, _state.value, null))
        if (loop?.isActive != true) loop = scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private fun container() = (application as App).container

    private fun pathsCleanup(app: AppContainer, jobId: String) {
        runCatching { app.paths.deleteRecursively(app.paths.incomingDir(jobId)) }
    }

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                QueueNotifications.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            startForeground(QueueNotifications.NOTIFICATION_ID, notification)
        }
    }

    private fun activeIds(): List<String> = synchronized(poolLock) { pool.keys.toList() }

    /**
     * The queue is drained by a small pool instead of a single loop. Each job still
     * splits its file over four connections, so two jobs keep eight sockets busy
     * without starving either one.
     */
    private suspend fun runQueue() {
        val app = container()
        try {
            app.jobQueue.reapExpired()
            awaitNetwork(app)
            if (shouldWaitForWifi(app.settings.wifiOnly.first(), app.connectivity.unmetered())) {
                waitingForWifi = true
                publishState()
                return
            }
            val limit = app.settings.concurrentDownloads.first().coerceIn(1, MAX_PARALLEL_JOBS)
            coroutineScope {
                repeat(limit) { index -> launch { worker(app, index) } }
            }
        } catch (e: Exception) {
            Log.e(TAG, "download loop crashed", e)
        } finally {
            synchronized(poolLock) { pool.clear() }
            waitingForNetwork = false
            publishState()
            withContext(Dispatchers.Main) { stopForegroundCompatAndSelf() }
        }
    }

    /** Holds the queue while the phone is offline, then springs every backoff forward once. */
    private suspend fun awaitNetwork(app: AppContainer) {
        if (app.connectivity.online()) return
        waitingForNetwork = true
        publishState()
        while (!app.connectivity.online()) {
            if (app.db.jobDao().queuedCount() == 0) return
            delay(5_000)
        }
        waitingForNetwork = false
        app.db.jobDao().pullForward(System.currentTimeMillis())
        publishState()
    }

    private suspend fun worker(app: AppContainer, index: Int) {
        val worker = "$workerId-$index"
        while (true) {
            if (!app.connectivity.online()) {
                awaitNetwork(app)
            }
            val claimed = app.jobQueue.claim(worker)
            if (claimed == null) {
                if (app.db.jobDao().queuedCount() == 0) return
                // Rows exist but are waiting out a backoff. Park briefly and look again.
                delay(5_000)
                continue
            }
            runJob(app, claimed)
        }
    }

    private suspend fun runJob(app: AppContainer, job: JobEntity) {
        val title = job.url.substringAfterLast('/').ifBlank { "Media" }
        val active = ActiveDownload(title = title, progress = job.progress.coerceIn(0f, 1f))
        synchronized(poolLock) { pool[job.id] = active }
        publishState()

        val run = scope.async {
            app.pipeline.run(
                job = job,
                onProgress = { progress ->
                    val fraction = progress.coerceIn(0f, 1f)
                    active.progress = fraction
                    val now = System.currentTimeMillis()
                    publishState()
                    if (now - active.lastBeatAt > 900) {
                        active.lastBeatAt = now
                        scope.launch {
                            app.jobQueue.heartbeat(job.id, fraction, active.stage)
                        }
                    }
                    if (now - active.lastNotifyAt > 900) {
                        active.lastNotifyAt = now
                        notify(_state.value)
                    }
                },
                onMeta = { metaTitle, durationMs, thumb ->
                    val relative = thumb?.let { file -> ".incoming/${job.id}/${file.name}" }
                    active.title = metaTitle
                    publishState()
                    scope.launch {
                        app.db.jobDao().setMeta(
                            job.id,
                            metaTitle,
                            durationMs,
                            relative,
                            System.currentTimeMillis(),
                        )
                        notify(_state.value)
                    }
                },
                onStage = { stage ->
                    active.stage = stage
                    if (stage != "downloading") active.progress = 0f
                    publishState()
                    scope.launch { app.jobQueue.heartbeat(job.id, active.progress, stage) }
                },
            )
        }
        runs[job.id] = run
        val result = runCatching { run.await() }
            .getOrElse { DownloadPipeline.Result.Cancelled }
        runs.remove(job.id)
        synchronized(poolLock) { pool.remove(job.id) }
        publishState()

        // The user may have cancelled while the extractor was busy; a cancelled
        // row must not be flipped to done or failed afterwards.
        if (app.db.jobDao().get(job.id)?.status == "cancelled") {
            pathsCleanup(app, job.id)
            return
        }
        when (result) {
            is DownloadPipeline.Result.Done -> {
                app.jobQueue.finish(job.id, result.itemId)
                val item = app.db.itemDao().get(result.itemId)
                notifyResult(
                    title = item?.title ?: "Download finished",
                    text = "Saved to your library",
                    failure = false,
                )
            }

            is DownloadPipeline.Result.Failed -> {
                Log.w(TAG, "job ${job.id} failed: ${result.message} (retry=${result.retryable})")
                val willRetry = app.jobQueue.fail(job.id, result.message, permanent = !result.retryable)
                if (!willRetry) {
                    notifyResult(
                        title = "Download failed",
                        text = result.message,
                        failure = true,
                    )
                }
            }
            DownloadPipeline.Result.Cancelled -> {
                app.jobQueue.cancel(job.id)
                pathsCleanup(app, job.id)
            }
        }
    }

    private suspend fun cancelOne(id: String) {
        runs.remove(id)?.cancel()
        synchronized(poolLock) { pool.remove(id) }
        publishState()
        runCatching { container().extractor.cancel(id) }
        container().jobQueue.cancel(id)
    }

    /** The notification mirrors the oldest active job; the badge counts them all. */
    private fun publishState() {
        val (id, primary, count) = synchronized(poolLock) {
            val first = pool.entries.firstOrNull()
            Triple(first?.key, first?.value, pool.size)
        }
        _state.value = DownloadState(
            running = count > 0,
            activeJobId = id,
            activeTitle = primary?.title,
            progress = primary?.progress ?: 0f,
            stage = primary?.stage,
            waitingForWifi = waitingForWifi,
            waitingForNetwork = waitingForNetwork,
            activeCount = count,
        )
    }

    private fun notifyResult(title: String, text: String, failure: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this).notify(
                QueueNotifications.RESULT_NOTIFICATION_ID,
                QueueNotifications.buildResult(this, title, text, failure),
            )
        }
    }

    private fun notify(state: DownloadState, title: String? = state.activeTitle) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching {
            NotificationManagerCompat.from(this)
                .notify(QueueNotifications.NOTIFICATION_ID, QueueNotifications.build(this, state, title))
        }
    }

    private fun stopForegroundCompatAndSelf() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) stopForeground(STOP_FOREGROUND_REMOVE)
        else @Suppress("DEPRECATION") stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}
