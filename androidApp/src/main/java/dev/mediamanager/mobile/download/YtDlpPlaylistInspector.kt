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
class YtDlpPlaylistInspector(private val context: Context) : PlaylistInspector {

    override suspend fun inspect(url: String): PlaylistSummary = withContext(Dispatchers.IO) {
        YtDlpExtractor.init(context)
        val request = YoutubeDLRequest(url).apply {
            addOption("--flat-playlist")
            addOption("--dump-single-json")
            // YouTube's player challenge needs a JS runtime; a flat listing never does.
            addOption("--no-js-runtimes")
            addOption("--socket-timeout", "20")
            addOption("--retries", "1")
        }
        val json = try {
            YoutubeDL.getInstance().execute(request).out
        } catch (e: Exception) {
            android.util.Log.w("PlaylistInspector", "flat listing failed", e)
            throw IllegalStateException(e.message ?: "Could not read that playlist", e)
        }
        parseFlatPlaylist(json, playlistListId(url) ?: url)
            ?: run {
                android.util.Log.w("PlaylistInspector", "unparsable: ${json.take(300)}")
                throw IllegalStateException("That link is not a readable playlist")
            }
    }
}
