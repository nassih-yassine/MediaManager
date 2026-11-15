package dev.mediamanager.mobile

import android.app.Application
import dev.mediamanager.mobile.download.DownloadService
import dev.mediamanager.mobile.download.DownloadSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

class App : Application() {
    lateinit var container: AppContainer
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.player.connect()

        scope.launch {
            combine(container.settings.wifiOnly, container.settings.maxHeight) { wifi, height ->
                DownloadSettings(wifiOnly = wifi, maxHeight = height)
            }.collect { container.downloadSettings = it }
        }
        scope.launch {
            container.connectivity.observeOnline().collect { online ->
                if (online && hasQueuedWork()) runCatching { DownloadService.start(this@App) }
            }
        }
        // The service can be killed while the process survives (OEM battery managers).
        // Whenever queued work exists, try to bring it back; if the app is in the
        // background Android rejects the start and the next open retries.
        scope.launch {
            container.db.jobDao().observeActive().collect { jobs ->
                if (jobs.any { it.status == "queued" || it.status == "running" }) {
                    runCatching { DownloadService.start(this@App) }
                }
            }
        }
    }

    private suspend fun hasQueuedWork(): Boolean =
        container.db.jobDao().activeJobs().isNotEmpty()
}
