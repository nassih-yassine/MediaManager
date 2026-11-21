package dev.mediamanager.mobile.download

import androidx.room.withTransaction
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.PlaylistEntity
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Result of one playlist import, in the copy the Add screen shows. */
data class ImportOutcome(
    val queued: Int,
    val existing: Int,
    /** Already downloading for another playlist; it cannot be linked here. */
    val skipped: Int,
) {
    val nothing: Boolean get() = queued == 0 && existing == 0
}

/**
 * Turns a summary into a playlist plus one job per missing video, and links the videos
 * that are already in the library at their source positions.
 *
 * The playlist row is created first: jobs can finish before this function returns, and
 * the pipeline links an item only when the playlist already exists.
 */
class PlaylistImportRunner(
    private val db: AppDatabase,
    private val queue: JobQueue,
    private val startService: () -> Unit = {},
) {

    suspend fun import(
        summary: PlaylistSummary,
        mode: DownloadMode,
        selected: List<PlaylistEntry>,
    ): ImportOutcome = withContext(Dispatchers.IO) {
        if (selected.isEmpty()) return@withContext ImportOutcome(0, 0, 0)

        val now = System.currentTimeMillis()
        // Non-YouTube listings carry their own URL; only YouTube needs the query form.
        val sourceUrl = if (summary.listId.startsWith("http")) {
            summary.listId
        } else {
            "https://www.youtube.com/playlist?list=${summary.listId}"
        }
        val existingPlaylist = db.playlistDao().bySourceUrl(sourceUrl)
        val playlistId = existingPlaylist?.id ?: UUID.randomUUID().toString()
        if (existingPlaylist == null) {
            db.playlistDao().upsert(
                PlaylistEntity(
                    id = playlistId,
                    name = summary.name,
                    sourceUrl = sourceUrl,
                    coverPath = null,
                    createdAt = now,
                    updatedAt = now,
                ),
            )
        }
        val base = if (existingPlaylist == null) 0 else db.playlistDao().maxPosition(playlistId) + 1

        var queued = 0
        var existing = 0
        var skipped = 0
        val links = mutableListOf<Pair<String, Int>>()

        selected.forEachIndexed { index, entry ->
            val position = base + index
            val result = queue.enqueue(
                url = entry.url,
                mode = mode,
                playlistId = playlistId,
                playlistPosition = position,
            )
            when {
                result.libraryItemId != null -> {
                    existing++
                    links += result.libraryItemId to position
                }

                // Already downloading for another playlist: link it when the row exists,
                // otherwise report it instead of silently leaving a hole.
                result.duplicate -> {
                    val itemId = result.job?.itemId
                    if (itemId != null) {
                        existing++
                        links += itemId to position
                    } else {
                        skipped++
                    }
                }

                result.job != null -> queued++

                else -> skipped++
            }
        }

        if (links.isNotEmpty()) {
            db.withTransaction {
                links.forEach { (itemId, position) ->
                    db.playlistDao().insertAt(playlistId, itemId, position, now)
                }
            }
        }
        if (queued == 0 && existing == 0 && existingPlaylist == null) {
            db.playlistDao().delete(playlistId)
        } else if (queued > 0) {
            startService()
        }
        ImportOutcome(queued = queued, existing = existing, skipped = skipped)
    }
}
