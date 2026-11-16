package dev.mediamanager.mobile.playback

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.data.SettingsStore
import dev.mediamanager.mobile.download.resumeTarget
import dev.mediamanager.mobile.download.restoreIndex
import kotlinx.coroutines.flow.first
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

data class NowPlaying(
    val itemId: String,
    val title: String,
    val isPlaying: Boolean,
    val positionMs: Long,
    val durationMs: Long,
    val hasNext: Boolean,
    val thumbPath: String?,
    val isVideo: Boolean,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val shuffle: Boolean = false,
    val speed: Float = 1f,
)

class PlayerRepository(
    private val context: Context,
    private val db: AppDatabase,
    private val paths: LibraryPaths,
    private val settings: SettingsStore,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var current: Item? = null
    private var ticks = 0

    /** Waiting for a yes/no on long media that already has a saved position. */
    data class ResumePoint(val itemId: String, val positionMs: Long)

    private val _resumePrompt = MutableStateFlow<ResumePoint?>(null)
    val resumePrompt: StateFlow<ResumePoint?> = _resumePrompt.asStateFlow()

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    /** Bumped whenever something asks for the big player; the shell listens. */
    private val _openRequests = MutableStateFlow(0)
    val openRequests: StateFlow<Int> = _openRequests.asStateFlow()

    fun openPlayer() {
        _openRequests.value = _openRequests.value + 1
    }

    private val _queue = MutableStateFlow<List<Item>>(emptyList())
    val queue: StateFlow<List<Item>> = _queue.asStateFlow()

    fun connect() {
        if (controller != null) return
        val token = SessionToken(
            context,
            ComponentName(context, PlaybackService::class.java),
        )
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener({
            val c = runCatching { future.get() }.getOrNull() ?: return@addListener
            controller = c
            c.addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    publish()
                    if (isPlaying) startTicker() else {
                        stopTicker()
                        savePosition()
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    mediaItem?.mediaId?.let { id ->
                        _queue.value.firstOrNull { it.id == id }?.let {
                            current = it
                            scope.launch { settings.setLastItemId(it.id) }
                        }
                    }
                    publish()
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    publish()
                    if (playbackState == Player.STATE_ENDED) savePosition(reset = true)
                }
            })
            restore(c)
        }, ContextCompat.getMainExecutor(context))
    }

    fun disconnect() {
        stopTicker()
        controller?.release()
        controller = null
    }

    private fun restore(c: MediaController) {
        scope.launch {
            val ids = db.queueDao().all().map { it.itemId }
            if (ids.isEmpty()) return@launch
            val items = ids.mapNotNull { db.itemDao().get(it)?.toItem() }
            if (items.isEmpty()) return@launch
            // Park on the item that was playing, and put the cursor back on the frame
            // where it stopped. Restoring at zero silently threw the resume point away.
            val index = restoreIndex(items.map { it.id }, settings.lastItemId.first())
            val start = items[index]
            _queue.value = items
            current = start
            c.setMediaItems(
                items.map(::mediaItem),
                index,
                resumeTarget(start.positionMs, start.durationMs),
            )
            c.prepare()
            c.playWhenReady = false
            publish()
        }
    }

    /** Play [items] starting at [startIndex]; the queue is seeded with the whole list. */
    fun playItems(items: List<Item>, startIndex: Int) {
        val c = controller ?: return
        if (items.isEmpty()) return
        val index = startIndex.coerceIn(0, items.lastIndex)
        _queue.value = items
        current = items[index]
        scope.launch {
            db.queueDao().replaceAll(items.map { it.id })
            settings.setLastItemId(items[index].id)
        }
        val target = resumeTarget(current?.positionMs ?: 0, current?.durationMs)
        c.setMediaItems(items.map(::mediaItem), index, 0)
        c.prepare()
        if (target > 0) {
            // Long media: hold on the first frame and ask, so nobody is dropped into
            // the middle of something they meant to restart.
            c.playWhenReady = false
            _resumePrompt.value = ResumePoint(items[index].id, target)
        } else {
            c.play()
        }
        publish()
        // Tapping a piece of media always lands on the big player.
        openPlayer()
    }

    /** Continue from the saved frame and start playing. */
    fun resumeFromPrompt() {
        val point = _resumePrompt.value ?: return
        controller?.seekTo(point.positionMs.coerceAtLeast(0))
        controller?.play()
        _resumePrompt.value = null
        publish()
    }

    /** Ignore the saved frame and begin at the first second. */
    fun startOver() {
        controller?.seekTo(0)
        controller?.play()
        _resumePrompt.value = null
        publish()
    }

    fun playNext(item: Item) {
        val c = controller ?: return
        val index = (c.currentMediaItemIndex + 1).coerceAtLeast(0)
        c.addMediaItem(index, mediaItem(item))
        val next = _queue.value.toMutableList().apply { add(index.coerceAtMost(size), item) }
        _queue.value = next
        scope.launch { db.queueDao().replaceAll(next.map { it.id }) }
    }

    fun addToQueue(item: Item) {
        val c = controller ?: return
        c.addMediaItem(mediaItem(item))
        val next = _queue.value + item
        _queue.value = next
        scope.launch { db.queueDao().replaceAll(next.map { it.id }) }
    }

    /** Drops one entry from the play queue, playing or not. */
    fun removeFromQueue(id: String) {
        val c = controller ?: return
        val index = _queue.value.indexOfFirst { it.id == id }
        if (index < 0) return
        val removingCurrent = _queue.value[index].id == current?.id
        val next = _queue.value.filterNot { it.id == id }
        _queue.value = next
        scope.launch { db.queueDao().replaceAll(next.map { it.id }) }
        c.removeMediaItem(index)
        if (next.isEmpty()) {
            _resumePrompt.value = null
            current = null
            _nowPlaying.value = null
            return
        }
        if (removingCurrent) {
            current = next.getOrNull(index.coerceAtMost(next.lastIndex))
            c.prepare()
        }
        publish()
    }

    fun reorderQueue(orderedIds: List<String>) {
        val byId = _queue.value.associateBy { it.id }
        val next = orderedIds.mapNotNull { byId[it] }
        if (next.size != _queue.value.size) return
        _queue.value = next
        scope.launch { db.queueDao().replaceAll(next.map { it.id }) }
        val c = controller ?: return
        val currentId = current?.id
        val position = c.currentPosition
        c.clearMediaItems()
        c.setMediaItems(next.map(::mediaItem), next.indexOfFirst { it.id == currentId }.coerceAtLeast(0), position)
        c.prepare()
    }

    fun cycleRepeat() {
        val c = controller ?: return
        c.repeatMode = nextRepeatMode(c.repeatMode)
        publish()
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
        publish()
    }

    fun setSpeed(speed: Float) {
        controller?.setPlaybackSpeed(speed)
        publish()
    }

    fun skipBy(deltaMs: Long) {
        val c = controller ?: return
        c.seekTo((c.currentPosition + deltaMs).coerceAtLeast(0))
        publish()
    }

    fun toggle() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.let { if (it.hasNextMediaItem()) it.seekToNextMediaItem() }
    }

    fun previous() {
        controller?.let {
            if (it.currentPosition > 3_000) it.seekTo(0) else if (it.hasPreviousMediaItem()) it.seekToPreviousMediaItem()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs.coerceAtLeast(0))
        publish()
    }

    fun stop() {
        _resumePrompt.value = null
        savePosition()
        controller?.stop()
        controller?.clearMediaItems()
        _nowPlaying.value = null
        _queue.value = emptyList()
        current = null
    }

    suspend fun onItemDeleted(id: String) {
        if (current?.id == id) {
            _queue.value = _queue.value.filterNot { it.id == id }
            stop()
        } else {
            _queue.value = _queue.value.filterNot { it.id == id }
        }
    }

    fun savePosition(reset: Boolean = false) {
        val c = controller ?: return
        val item = current ?: return
        val position = if (reset) 0L else c.currentPosition.coerceAtLeast(0)
        scope.launch {
            db.itemDao().updatePosition(item.id, position, System.currentTimeMillis())
        }
    }

    private fun mediaItem(item: Item): MediaItem {
        val file = paths.resolve(item.path)
        val thumb = item.thumbPath?.let { paths.resolve(it) }
        return MediaItem.Builder()
            .setMediaId(item.id)
            .setUri(file?.toUri() ?: "".toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(item.title)
                    .setArtist(if (item.isVideo) "Video" else "Audio")
                    .setAlbumTitle("MediaManager")
                    .setArtworkUri(thumb?.toUri())
                    .build(),
            )
            .build()
    }

    private fun publish() {
        val c = controller ?: return
        val item = current ?: return
        _nowPlaying.value = NowPlaying(
            itemId = item.id,
            title = item.title,
            isPlaying = c.isPlaying,
            positionMs = c.currentPosition.coerceAtLeast(0),
            durationMs = c.duration.takeIf { it > 0 } ?: (item.durationMs ?: 0),
            hasNext = c.hasNextMediaItem(),
            thumbPath = item.thumbPath,
            isVideo = item.isVideo,
            repeatMode = c.repeatMode,
            shuffle = c.shuffleModeEnabled,
            speed = c.playbackParameters.speed,
        )
    }

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                delay(1_000)
                publish()
                ticks++
                if (ticks % 5 == 0) savePosition()
            }
        }
    }

    private fun stopTicker() {
        ticker?.cancel()
        ticker = null
    }
}
