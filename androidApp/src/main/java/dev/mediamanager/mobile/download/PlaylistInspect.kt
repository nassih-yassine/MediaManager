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
