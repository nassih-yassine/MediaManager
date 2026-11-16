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
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import dev.mediamanager.mobile.MainActivity
import dev.mediamanager.mobile.R
import dev.mediamanager.mobile.playback.NowPlaying
import dev.mediamanager.mobile.playback.PlaybackService
import java.io.File

/**
 * Now-playing widget. It never renders the video and never holds a player; it reads
 * the session the app already runs and sends transport commands back through a
 * short-lived MediaController.
 */
class NowPlayingWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetPublisher.render(context, appWidgetManager, widgetState)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_TOGGLE -> transport(context) { c ->
                if (c.isPlaying) c.pause() else c.play()
            }
            ACTION_NEXT -> transport(context) { c ->
                if (c.hasNextMediaItem()) c.seekToNextMediaItem()
            }
        }
    }

    private fun transport(context: Context, block: (MediaController) -> Unit) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future: ListenableFuture<MediaController> =
            MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val controller = runCatching { future.get() }.getOrNull() ?: return@addListener
            // The service publishes on change and repaints us; just let go.
            runCatching { block(controller) }
            controller.release()
        }, androidx.core.content.ContextCompat.getMainExecutor(context))
    }

    companion object {
        const val ACTION_TOGGLE = "dev.mediamanager.mobile.widget.TOGGLE"
        const val ACTION_NEXT = "dev.mediamanager.mobile.widget.NEXT"
        const val EXTRA_OPEN_PLAYER = "open_player"

        /** Snapshot pushed by the app whenever playback changes. */
        @Volatile
        var widgetState: NowPlaying? = null
    }
}

/**
 * Renders the widget from a playback snapshot. Called by the provider on update and
 * by PlayerRepository every time it publishes, so the widget tracks the session
 * without polling.
 */
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
            Intent(context, MainActivity::class.java)
                .putExtra(NowPlayingWidget.EXTRA_OPEN_PLAYER, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        views.setOnClickPendingIntent(R.id.widget_cover, open)
        views.setOnClickPendingIntent(R.id.widget_title, open)

        if (state == null) {
            views.setTextViewText(R.id.widget_title, context.getString(R.string.widget_idle))
            views.setTextViewText(R.id.widget_subtitle, context.getString(R.string.widget_idle_hint))
            views.setImageViewResource(R.id.widget_play, R.drawable.ic_widget_play)
            views.setOnClickPendingIntent(R.id.widget_play, open)
            views.setOnClickPendingIntent(R.id.widget_next, open)
            return views
        }

        views.setTextViewText(R.id.widget_title, state.title)
        views.setTextViewText(
            R.id.widget_subtitle,
            if (state.isPlaying) context.getString(R.string.widget_playing) else context.getString(R.string.widget_paused),
        )
        views.setImageViewResource(
            R.id.widget_play,
            if (state.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
        )
        coverBitmap(context, state.thumbPath)?.let { views.setImageViewBitmap(R.id.widget_cover, it) }
        views.setOnClickPendingIntent(R.id.widget_play, broadcast(context, NowPlayingWidget.ACTION_TOGGLE, 1))
        views.setOnClickPendingIntent(R.id.widget_next, broadcast(context, NowPlayingWidget.ACTION_NEXT, 2))
        return views
    }

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
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
