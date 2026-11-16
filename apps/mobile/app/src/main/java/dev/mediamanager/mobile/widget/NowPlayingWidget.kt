package dev.mediamanager.mobile.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.RemoteViews
import androidx.media3.common.Player
import dev.mediamanager.mobile.MainActivity
import dev.mediamanager.mobile.R
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlaybackService
import dev.mediamanager.mobile.ui.format.formatDuration
import java.io.File

/**
 * Home-screen twin of the mini player: cover, title, clock, progress line, and the
 * two controls people actually reach for.
 *
 * Transport is forwarded to [PlaybackService] as a start command. A broadcast
 * receiver is forbidden from binding to a service, so the widget never builds a
 * MediaController itself; it only wakes the service that already owns the player.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetPublisher.render(context, appWidgetManager, NowPlayingWidget.widgetState)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        val command = when (action) {
            ACTION_TOGGLE -> PlaybackService.ACTION_TOGGLE
            ACTION_NEXT -> PlaybackService.ACTION_NEXT
            ACTION_PREV -> PlaybackService.ACTION_PREV
            ACTION_REPEAT -> PlaybackService.ACTION_REPEAT
            else -> return
        }
        if (PlaybackService.currentPlayer != null) {
            // Same process, service alive: hand it the command directly.
            runCatching {
                context.startService(Intent(context, PlaybackService::class.java).setAction(command))
            }
        } else {
            // Nothing loaded: the useful thing is the app, on the player.
            runCatching { context.startActivity(openPlayerIntent(context)) }
        }
    }

    companion object {
        const val ACTION_TOGGLE = "dev.mediamanager.mobile.widget.TOGGLE"
        const val ACTION_NEXT = "dev.mediamanager.mobile.widget.NEXT"
        const val ACTION_PREV = "dev.mediamanager.mobile.widget.PREV"
        const val ACTION_REPEAT = "dev.mediamanager.mobile.widget.REPEAT"
        const val EXTRA_OPEN_PLAYER = "open_player"

        /** Snapshot pushed by the app whenever playback moves. */
        @Volatile
        var widgetState: NowPlaying? = null

        fun openPlayerIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
}

/** Renders the widget from a playback snapshot; called by the app and the provider. */
object WidgetPublisher {

    fun render(
        context: Context,
        manager: AppWidgetManager = AppWidgetManager.getInstance(context),
        state: NowPlaying?,
    ) {
        val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
        if (ids.isEmpty()) return
        ids.forEach { id -> manager.updateAppWidget(id, build(context, state)) }
    }

    private fun build(context: Context, state: NowPlaying?): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_now_playing)
        val open = PendingIntent.getActivity(
            context,
            0,
            NowPlayingWidget.openPlayerIntent(context),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_cover, open)
        views.setOnClickPendingIntent(R.id.widget_title, open)

        if (state == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle))
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_idle_hint))
            views.setImageViewResource(R.id.widget_play, R.drawable.ic_widget_play)
            views.setProgressBar(R.id.widget_progress, 1000, 0, false)
            views.setImageViewResource(R.id.widget_repeat, R.drawable.ic_widget_repeat)
            views.setOnClickPendingIntent(R.id.widget_play, open)
            views.setOnClickPendingIntent(R.id.widget_next, open)
            views.setOnClickPendingIntent(R.id.widget_prev, open)
            views.setOnClickPendingIntent(R.id.widget_repeat, open)
            return views
        }

        val clock = buildString {
            append(formatDuration(state.positionMs).ifEmpty { "0:00" })
            append(" / ")
            append(formatDuration(state.durationMs).ifEmpty { "--:--" })
        }
        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(R.id.widget_subtitle, clock)
        views.setImageViewResource(
            R.id.widget_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        val fraction = if (state.durationMs > 0) {
            (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f)
        } else {
            0f
        }
        views.setProgressBar(R.id.widget_progress, 1000, (fraction * 1000).toInt(), false)
        coverBitmap(context, state.thumbPath)?.let { views.setImageViewBitmap(R.id.widget_cover, it) }

        views.setImageViewResource(
            R.id.widget_repeat,
            when (state.repeatMode) {
                Player.REPEAT_MODE_ONE -> R.drawable.ic_widget_repeat_one
                Player.REPEAT_MODE_ALL -> R.drawable.ic_widget_repeat_on
                else -> R.drawable.ic_widget_repeat
            },
        )
        views.setOnClickPendingIntent(R.id.widget_play, command(context, NowPlayingWidget.ACTION_TOGGLE, 1))
        views.setOnClickPendingIntent(R.id.widget_next, command(context, NowPlayingWidget.ACTION_NEXT, 2))
        views.setOnClickPendingIntent(R.id.widget_prev, command(context, NowPlayingWidget.ACTION_PREV, 3))
        views.setOnClickPendingIntent(R.id.widget_repeat, command(context, NowPlayingWidget.ACTION_REPEAT, 4))
        return views
    }

    private fun command(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            code,
            Intent(context, NowPlayingWidget::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /** Covers are small; decode downsampled so the widget stays under the binder limit. */
    private fun coverBitmap(context: Context, thumbPath: String?): Bitmap? {
        val file = thumbPath?.let { File(context.filesDir, it) } ?: return null
        if (!file.exists()) return null
        return runCatching {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.path, bounds)
            var sample = 1
            while (bounds.outWidth / sample > 256) sample *= 2
            BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })
        }.getOrNull()
    }
}
