package dev.mediamanager.mobile.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dev.mediamanager.mobile.R

/** Owns the single ExoPlayer instance for the whole app. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {

    companion object {
        /** Widget transport, forwarded as start commands (receivers cannot bind). */
        const val ACTION_TOGGLE = "dev.mediamanager.mobile.playback.TOGGLE"
        const val ACTION_NEXT = "dev.mediamanager.mobile.playback.NEXT"
        const val ACTION_PREV = "dev.mediamanager.mobile.playback.PREV"
        const val ACTION_REPEAT = "dev.mediamanager.mobile.playback.REPEAT"

        /** In-process handle for the video surface; the session is still the source of truth. */
        @Volatile
        var currentPlayer: ExoPlayer? = null
            private set
    }

    private var player: ExoPlayer? = null
    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val exo = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = exo
        currentPlayer = exo
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelId("playback")
                .setChannelName(R.string.playback_channel)
                .setNotificationId(1001)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_mm) },
        )
        session = MediaSession.Builder(this, exo)
            .setSessionActivity(
                android.app.PendingIntent.getActivity(
                    this,
                    0,
                    android.content.Intent(this, dev.mediamanager.mobile.MainActivity::class.java),
                    android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> player?.let { if (it.isPlaying) it.pause() else it.play() }
            ACTION_NEXT -> player?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
            // Mirrors the app: restart the track unless it just started, then step back.
            ACTION_PREV -> player?.let {
                if (it.currentPosition > 3_000) it.seekTo(0)
                else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
            }
            ACTION_REPEAT -> player?.let {
                it.repeatMode = when (it.repeatMode) {
                    androidx.media3.common.Player.REPEAT_MODE_OFF -> androidx.media3.common.Player.REPEAT_MODE_ALL
                    androidx.media3.common.Player.REPEAT_MODE_ALL -> androidx.media3.common.Player.REPEAT_MODE_ONE
                    else -> androidx.media3.common.Player.REPEAT_MODE_OFF
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val playing = player?.playWhenReady == true && player?.playbackState == androidx.media3.common.Player.STATE_READY
        if (!playing) stopSelf()
    }

    override fun onDestroy() {
        session?.release()
        session = null
        player?.release()
        player = null
        currentPlayer = null
        super.onDestroy()
    }
}
