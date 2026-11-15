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
import dev.mediamanager.mobile.data.JobEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        QueueNotifications.channel(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) {
            val job = activeJob
            if (job != null) scope.launch { container().jobQueue.cancel(job.id) }
            return START_NOT_STICKY
        }
        startForegroundCompat(QueueNotifications.build(this, _state.value, null))
        scope.launch { runQueue() }
        return START_NOT_STICKY
    }

    private fun container() = (application as App).container

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
        try {
            app.jobQueue.reapExpired()
            while (true) {
                if (shouldWaitForWifi(app.settings.wifiOnly.first(), app.connectivity.unmetered())) {
                    _state.value = _state.value.copy(running = false, waitingForWifi = true)
                    notify(DownloadState(running = false, waitingForWifi = true))
                    break
                }
                val job = app.jobQueue.claim(workerId) ?: break
                activeJob = job
                val title = job.url.substringAfterLast('/').ifBlank { "Media" }
                _state.value = DownloadState(
                    running = true,
                    activeJobId = job.id,
                    activeTitle = title,
                    progress = job.progress,
                )
                notify(_state.value, title)

                val result = app.pipeline.run(job) { progress ->
                    _state.value = _state.value.copy(progress = progress)
                    scope.launch {
                        app.jobQueue.heartbeat(job.id, progress, "fetching")
                        notify(_state.value, title)
                    }
                }
                when (result) {
                    is DownloadPipeline.Result.Done -> app.jobQueue.finish(job.id, result.itemId)
                    is DownloadPipeline.Result.Failed -> {
                        app.jobQueue.fail(job.id, result.message, permanent = !result.retryable)
                    }
                    DownloadPipeline.Result.Cancelled -> app.jobQueue.cancel(job.id)
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
