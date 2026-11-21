package dev.mediamanager.mobile.download

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Lists a playlist with the bundled yt-dlp instead of NewPipe. `--flat-playlist`
 * never resolves a stream, so it needs no JS runtime, and it works for every site
 * yt-dlp knows, not just YouTube.
 */
class YtDlpPlaylistInspector(
    private val context: Context,
    private val cache: PlaylistCache = PlaylistCache(),
    private val unmetered: () -> Boolean = { true },
) : PlaylistInspector {

    /**
     * The bundled yt-dlp stops a YouTube playlist at the first 100 entries. A current
     * stable release paginates properly, so refresh once when the binary is old.
     */
    private fun refreshExtractorIfOld() {
        if (refreshed) return
        refreshed = true
        if (!unmetered()) {
            // A 3 MB binary is not worth someone's mobile data; the old one still lists,
            // though YouTube caps it at the first 100 entries.
            android.util.Log.d("PlaylistInspector", "skipping yt-dlp refresh on a metered network")
            return
        }
        val current = YtDlpExtractor.versionName(context)
        android.util.Log.d("PlaylistInspector", "yt-dlp $current")
        // A null version means the bundled zip never registered one; update once.
        if (current != null && current >= MIN_YTDLP) return
        android.util.Log.d("PlaylistInspector", "updating yt-dlp from $current")
        val updated = YtDlpExtractor.update(context)
        android.util.Log.d(
            "PlaylistInspector",
            "yt-dlp update=$updated now=${YtDlpExtractor.versionName(context)}",
        )
    }

    private companion object {
        const val MIN_YTDLP = "2025.06.01"

        @Volatile
        private var refreshed = false
    }

    override suspend fun inspect(url: String): PlaylistSummary = withContext(Dispatchers.IO) {
        val cacheKey = playlistListId(url) ?: url
        cache.get(cacheKey)?.let {
            android.util.Log.d("PlaylistInspector", "cache hit for $url")
            return@withContext it
        }
        YtDlpExtractor.init(context)
        refreshExtractorIfOld()
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            // YouTube's player challenge needs a JS runtime; a flat listing never does.
            addOption("--no-js-runtimes")
            addOption("--socket-timeout", "20")
            addOption("--retries", "1")
        }
        val started = System.currentTimeMillis()
        val json = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: Exception) {
            android.util.Log.w("PlaylistInspector", "flat listing failed", e)
            throw IllegalStateException(e.message ?: "Could not read that playlist", e)
        }
        val listedAt = System.currentTimeMillis()
        val summary = parseFlatPlaylist(json, playlistListId(url) ?: url)
        android.util.Log.d(
            "PlaylistInspector",
            "listed ${json.length} bytes in ${listedAt - started} ms, " +
                "parsed ${summary?.count ?: -1} entries in ${System.currentTimeMillis() - listedAt} ms",
        )
        summary?.let { cache.put(cacheKey, it) }
        summary
            ?: run {
                android.util.Log.w("PlaylistInspector", "unparsable: ${json.take(300)}")
                throw IllegalStateException("That link is not a readable playlist")
            }
    }
}
