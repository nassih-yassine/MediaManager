package dev.mediamanager.mobile.media

import dev.mediamanager.mobile.data.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class DeletedItem(val id: String, val freedBytes: Long)
data class RecheckResult(val missing: List<String>, val resized: List<Pair<String, Long>>)

class ItemRepository(
    private val db: AppDatabase,
    private val paths: LibraryPaths,
    private val now: () -> Long = System::currentTimeMillis,
) {

    /**
     * Deletes the row (cascading playlists and queue) and the files. A missing file is
     * not an error: the row still goes, and 0 bytes are reported as freed.
     */
    suspend fun delete(id: String): DeletedItem = withContext(Dispatchers.IO) {
        val item = db.itemDao().get(id) ?: return@withContext DeletedItem(id, 0)
        val media = paths.resolve(item.path)
        val thumb = item.thumbPath?.let { paths.resolve(it) }
        val freed = (media?.length() ?: 0L) + (thumb?.length() ?: 0L)
        db.itemDao().delete(id)
        media?.takeIf { it.exists() }?.delete()
        thumb?.takeIf { it.exists() }?.delete()
        DeletedItem(id, freed)
    }

    /** Compares DB sizes against the filesystem. Flags missing files, fixes drifted sizes. */
    suspend fun recheck(): RecheckResult = withContext(Dispatchers.IO) {
        val missing = mutableListOf<String>()
        val resized = mutableListOf<Pair<String, Long>>()
        val stamp = now()
        for (item in db.itemDao().all()) {
            val file: File? = paths.resolve(item.path)
            if (file == null || !file.exists()) {
                missing += item.id
                db.itemDao().setAvailable(item.id, false, stamp)
                continue
            }
            if (!item.available) db.itemDao().setAvailable(item.id, true, stamp)
            val actual = file.length()
            if (actual != item.bytes) {
                resized += item.id to actual
                db.itemDao().setBytes(item.id, actual, stamp)
            }
        }
        RecheckResult(missing, resized)
    }
}
