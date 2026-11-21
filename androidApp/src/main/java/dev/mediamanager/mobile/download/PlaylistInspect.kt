package dev.mediamanager.mobile.download

/** One row of a source playlist, before it becomes a job. */
data class PlaylistEntry(
    val url: String,
    val title: String,
    val durationMs: Long?,
)

/** What the import sheet needs to know before queueing anything. */
data class PlaylistSummary(
    val listId: String,
    val name: String,
    val uploader: String?,
    val coverUrl: String?,
    val entries: List<PlaylistEntry>,
    /** Reported by the extractor; may exceed [entries] when the list was capped. */
    val total: Int?,
    val truncated: Boolean,
) {
    val count: Int get() = entries.size
}

/**
 * Parses `yt-dlp --flat-playlist --dump-single-json`. Entries normally carry
 * `webpage_url`; YouTube sometimes only carries `id`, which maps to a watch URL.
 */
fun parseFlatPlaylist(
    json: String,
    listId: String,
    cap: Int = PLAYLIST_ENTRY_CAP,
): PlaylistSummary? = runCatching {
    val root = org.json.JSONObject(json)
    val entriesJson = root.optJSONArray("entries") ?: return null
    val entries = (0 until entriesJson.length()).mapNotNull { index ->
        val entry = entriesJson.optJSONObject(index) ?: return@mapNotNull null
        val link = entry.optString("webpage_url").takeIf { it.startsWith("http") }
            ?: entry.optString("url").takeIf { it.startsWith("http") }
            ?: entry.optString("id").takeIf { it.isNotBlank() }?.let { "https://www.youtube.com/watch?v=$it" }
            ?: return@mapNotNull null
        PlaylistEntry(
            url = link,
            title = entry.optString("title").ifBlank { "Untitled" },
            durationMs = entry.optDouble("duration", 0.0).takeIf { it > 0 }?.times(1000)?.toLong(),
        )
    }
    if (entries.isEmpty()) return null

    val thumbs = root.optJSONArray("thumbnails")
    val cover = thumbs?.let { list ->
        (0 until list.length()).mapNotNull { list.optJSONObject(it)?.optString("url") }.lastOrNull()
    } ?: root.optString("thumbnail").takeIf { it.startsWith("http") }

    summarizePlaylist(
        listId = listId,
        name = root.optString("title"),
        uploader = root.optString("uploader").takeIf { it.isNotBlank() },
        coverUrl = cover,
        entries = entries,
        total = root.optInt("playlist_count", entries.size).takeIf { it > 0 },
        cap = cap,
    )
}.getOrNull()

/**
 * Listings are expensive (a fresh yt-dlp process is ~4-5 s on a phone) and a user
 * usually reopens the same playlist while picking. Keep the last few in memory.
 */
class PlaylistCache(
    private val ttlMs: Long = 10 * 60 * 1000L,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 4,
) {
    private data class Entry(val summary: PlaylistSummary, val at: Long)

    private val cache = object : LinkedHashMap<String, Entry>(maxEntries, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Entry>): Boolean =
            size > maxEntries
    }

    @Synchronized
    fun get(url: String): PlaylistSummary? {
        val entry = cache[url] ?: return null
        if (clock() - entry.at > ttlMs) {
            cache.remove(url)
            return null
        }
        return entry.summary
    }

    @Synchronized
    fun put(url: String, summary: PlaylistSummary) {
        cache[url] = Entry(summary, clock())
    }

    @Synchronized
    fun clear() = cache.clear()
}

interface PlaylistInspector {
    suspend fun inspect(url: String): PlaylistSummary
}

/**
 * Caps how much of a huge playlist is enumerated. Each entry becomes a job, and the
 * dataSync service has a daily budget on Android 15+.
 */
const val PLAYLIST_ENTRY_CAP = 500

fun summarizePlaylist(
    listId: String,
    name: String,
    uploader: String?,
    coverUrl: String?,
    entries: List<PlaylistEntry>,
    total: Int? = null,
    cap: Int = PLAYLIST_ENTRY_CAP,
): PlaylistSummary {
    val kept = entries.distinctBy { it.url }.take(cap)
    return PlaylistSummary(
        listId = listId,
        name = name.ifBlank { "Playlist" },
        uploader = uploader,
        coverUrl = coverUrl,
        entries = kept,
        total = total ?: entries.size,
        truncated = entries.size > kept.size || (total ?: 0) > kept.size,
    )
}
