package dev.mediamanager.mobile.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import dev.mediamanager.mobile.MainActivity
import dev.mediamanager.mobile.R

data class DownloadState(
    val running: Boolean = false,
    val activeJobId: String? = null,
    val activeTitle: String? = null,
    val progress: Float = 0f,
    val waitingForWifi: Boolean = false,
    val waitingForNetwork: Boolean = false,
)

object QueueNotifications {

    const val CHANNEL_ID = "downloads"
    const val NOTIFICATION_ID = 41

    fun summary(state: DownloadState, activeTitle: String?): String = when {
        state.waitingForWifi -> "Waiting for Wi-Fi"
        state.waitingForNetwork -> "Waiting for the network"
        state.activeJobId == null -> "Idle"
        else -> "Downloading · ${activeTitle ?: "Media"} · ${(state.progress * 100).toInt()}%"
    }

    fun channel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Media downloads"
                    setShowBadge(false)
                },
            )
        }
    }

    fun build(context: Context, state: DownloadState, activeTitle: String?): Notification {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val cancel = PendingIntent.getService(
            context,
            1,
            Intent(context, DownloadService::class.java).setAction(DownloadService.ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_logo_fg)
            .setContentTitle("MediaManager")
            .setContentText(summary(state, activeTitle))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setProgress(100, (state.progress * 100).toInt(), state.activeJobId == null)
            .addAction(0, "Cancel", cancel)
            .build()
    }
}
