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
import androidx.core.content.getSystemService
import dev.mediamanager.mobile.App
import dev.mediamanager.mobile.AppContainer
import dev.mediamanager.mobile.data.JobEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class DownloadService : Service() {

    companion object {
        const val ACTION_CANCEL = "dev.mediamanager.mobile.CANCEL_DOWNLOAD"
        private const val TAG = "DownloadService"

        private val _state = MutableStateFlow(DownloadState())
        val state: StateFlow<DownloadState> = _state.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, DownloadService::class.java).setAction(ACTION_CANCEL))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val workerId = UUID.randomUUID().toString()
    private var activeJob: JobEntity? = null

    /**
     * One loop per service. Every `startService` (app open, Add, Wi-Fi return) would
     * otherwise launch another loop; the first one to finish calls stopSelf and kills
     * a download that is still running.
     */
    private var loop: kotlinx.coroutines.Job? = null

    /** The pipeline run for the active job, so Cancel can actually stop it. */
    private var activeRun: kotlinx.coroutines.Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        QueueNotifications.channel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val job = activeJob
            if (job != null) {
                activeRun?.cancel()
                runCatching { container().extractor.cancel(job.id) }
                scope.launch { container().jobQueue.cancel(job.id) }
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

    private suspend fun runQueue() {
        val app = container()
        var wasOffline = !app.connectivity.online()
        try {
            app.jobQueue.reapExpired()
            while (true) {
                if (shouldWaitForWifi(app.settings.wifiOnly.first(), app.connectivity.unmetered())) {
                    _state.value = _state.value.copy(running = false, waitingForWifi = true)
                    notify(DownloadState(running = false, waitingForWifi = true))
                    break
                }
                val claimed = app.jobQueue.claim(workerId)
                if (claimed == null) {
                    // A backoff can mean "the network is gone". Stay alive, keep the
                    // notification, and continue the moment connectivity returns.
                    if (app.db.jobDao().queuedCount() == 0) break
                    val isOnline = app.connectivity.online()
                    if (!isOnline) {
                        _state.value = DownloadState(waitingForNetwork = true)
                        notify(_state.value, null)
                    } else {
                        if (wasOffline) {
                            app.db.jobDao().pullForward(System.currentTimeMillis())
                            wasOffline = false
                            continue
                        }
                        _state.value = DownloadState()
                        notify(_state.value, null)
                    }
                    wasOffline = !isOnline
                    delay(10_000)
                    continue
                }
                val job = claimed
                wasOffline = !app.connectivity.online()
                activeJob = job
                val title = job.url.substringAfterLast('/').ifBlank { "Media" }
                _state.value = DownloadState(
                    running = true,
                    activeJobId = job.id,
                    activeTitle = title,
                    progress = job.progress,
                )
                notify(_state.value, title)

                val run = scope.async {
                    app.pipeline.run(
                        job = job,
                        onProgress = { progress ->
                            _state.value = _state.value.copy(progress = progress)
                            scope.launch {
                                app.jobQueue.heartbeat(job.id, progress, _state.value.stage)
                                notify(_state.value, title)
                            }
                        },
                        onStage = { stage ->
                            _state.value = _state.value.copy(stage = stage, progress = if (stage == "downloading") _state.value.progress else 0f)
                            scope.launch { app.jobQueue.heartbeat(job.id, _state.value.progress, stage) }
                        },
                    )
                }
                activeRun = run
                val result = runCatching { run.await() }
                    .getOrElse { DownloadPipeline.Result.Cancelled }
                activeRun = null

                // The user may have cancelled while the extractor was busy; a cancelled
                // row must not be flipped to done or failed afterwards.
                if (app.db.jobDao().get(job.id)?.status == "cancelled") {
                    pathsCleanup(app, job.id)
                } else {
                    when (result) {
                        is DownloadPipeline.Result.Done -> app.jobQueue.finish(job.id, result.itemId)
                        is DownloadPipeline.Result.Failed -> {
                            Log.w(TAG, "job ${job.id} failed: ${result.message} (retry=${result.retryable})")
                            app.jobQueue.fail(job.id, result.message, permanent = !result.retryable)
                        }
                        DownloadPipeline.Result.Cancelled -> {
                            app.jobQueue.cancel(job.id)
                            pathsCleanup(app, job.id)
                        }
                    }
                }
                activeJob = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "download loop crashed", e)
        } finally {
            _state.value = DownloadState()
            withContext(Dispatchers.Main) { stopForegroundCompatAndSelf() }
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
