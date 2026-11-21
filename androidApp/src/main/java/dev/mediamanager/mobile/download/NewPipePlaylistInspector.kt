package dev.mediamanager.mobile.download

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.playlist.PlaylistInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/**
 * Enumerates a YouTube playlist without downloading anything. NewPipe is already a
 * dependency for stream extraction and needs no JS runtime or PO token.
 */
class NewPipePlaylistInspector(
    private val cap: Int = PLAYLIST_ENTRY_CAP,
) : PlaylistInspector {

    override suspend fun inspect(url: String): PlaylistSummary = withContext(Dispatchers.IO) {
        NewPipeInit.ensure()
        val first = try {
            PlaylistInfo.getInfo(url)
        } catch (e: Exception) {
            throw IllegalStateException(e.message ?: "Could not read that playlist", e)
        }

        val entries = mutableListOf<PlaylistEntry>()
        first.relatedItems.forEach { item -> item.toEntry()?.let(entries::add) }

        var nextPage = first.nextPage
        while (entries.size < cap && nextPage != null) {
            currentCoroutineContext().ensureActive()
            val more = PlaylistInfo.getMoreItems(ServiceList.YouTube, url, nextPage)
            more.items.forEach { item -> item.toEntry()?.let(entries::add) }
            nextPage = more.nextPage
        }

        summarizePlaylist(
            listId = playlistListId(url) ?: url,
            name = first.name.orEmpty(),
            uploader = first.uploaderName,
            coverUrl = first.thumbnails.maxByOrNull { it.height }?.url,
            entries = entries,
            total = first.streamCount.takeIf { it > 0 }?.toInt(),
            cap = cap,
        )
    }

    private fun StreamInfoItem.toEntry(): PlaylistEntry? {
        val link = url.takeIf { it.isNotBlank() } ?: return null
        return PlaylistEntry(
            url = link,
            title = name.orEmpty().ifBlank { "Untitled" },
            durationMs = duration.takeIf { it > 0 }?.times(1000),
        )
    }
}
