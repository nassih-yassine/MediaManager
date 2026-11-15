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
import dev.mediamanager.mobile.download.resumeTarget
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
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var controller: MediaController? = null
    private var ticker: Job? = null
    private var current: Item? = null
    private var ticks = 0

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

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
                        _queue.value.firstOrNull { it.id == id }?.let { current = it }
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
            val index = ids.indexOfFirst { it == items.first().id }.coerceAtLeast(0)
            _queue.value = items
            c.setMediaItems(items.map(::mediaItem), index, 0)
            val start = items.getOrNull(index)
            current = start
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
        scope.launch { db.queueDao().replaceAll(items.map { it.id }) }
        c.setMediaItems(items.map(::mediaItem), index, 0)
        val target = resumeTarget(current?.positionMs ?: 0, current?.durationMs)
        if (target > 0) c.seekTo(index, target)
        c.prepare()
        c.play()
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
