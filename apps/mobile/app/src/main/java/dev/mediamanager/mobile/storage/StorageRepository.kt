package dev.mediamanager.mobile.storage

import android.os.StatFs
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.Item
import dev.mediamanager.mobile.data.toItem
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

data class FileStats(val totalBytes: Long, val freeBytes: Long)

data class StorageSnapshot(
    val mediaBytes: Long = 0,
    val audioBytes: Long = 0,
    val videoBytes: Long = 0,
    val thumbBytes: Long = 0,
    val tempBytes: Long = 0,
    val itemCount: Int = 0,
    val audioCount: Int = 0,
    val videoCount: Int = 0,
    val deviceTotal: Long = 0,
    val deviceFree: Long = 0,
) {
    val libraryBytes: Long get() = mediaBytes + thumbBytes + tempBytes
}

data class SizeRow(val item: Item, val share: Float)

fun share(part: Long, whole: Long): Float =
    if (whole <= 0L) 0f else (part.toDouble() / whole.toDouble()).toFloat().coerceIn(0f, 1f)

fun usedFraction(totalBytes: Long, freeBytes: Long): Float =
    if (totalBytes <= 0L) 0f else ((totalBytes - freeBytes).toDouble() / totalBytes).toFloat().coerceIn(0f, 1f)

class StorageRepository(
    private val db: AppDatabase,
    private val paths: LibraryPaths,
    private val stats: () -> FileStats = { deviceStats(paths) },
) {

    fun observe(tempBytes: StateFlow<Long>): Flow<StorageSnapshot> = combine(
        db.itemDao().observeTotalsByKind(),
        db.itemDao().observeThumbBytes(),
        tempBytes,
    ) { totals, thumbs, temp ->
        val audio = totals.firstOrNull { it.kind == "audio" }
        val video = totals.firstOrNull { it.kind == "video" }
        val media = totals.sumOf { it.bytes }
        val fileStats = stats()
        StorageSnapshot(
            mediaBytes = media,
            audioBytes = audio?.bytes ?: 0,
            videoBytes = video?.bytes ?: 0,
            thumbBytes = thumbs,
            tempBytes = temp,
            itemCount = totals.sumOf { it.count },
            audioCount = audio?.count ?: 0,
            videoCount = video?.count ?: 0,
            deviceTotal = fileStats.totalBytes,
            deviceFree = fileStats.freeBytes,
        )
    }

    /** One directory level, bounded by the queue length. Never walks the library. */
    suspend fun measureTemp(): Long = withContext(Dispatchers.IO) {
        val dir = paths.incomingRoot
        val dirs = dir.listFiles() ?: return@withContext 0L
        dirs.sumOf { jobDir -> jobDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } }
    }

    fun largestItems(limit: Int = 50): Flow<List<SizeRow>> =
        db.itemDao().observeLargest(limit).map { entities ->
            val total = entities.sumOf { it.bytes }
            entities.map { SizeRow(it.toItem(), share(it.bytes, total)) }
        }

    fun currentStats(): FileStats = stats()
}

fun deviceStats(paths: LibraryPaths): FileStats = try {
    val stat = StatFs(paths.libraryDir.parentFile?.absolutePath ?: paths.libraryDir.absolutePath)
    FileStats(totalBytes = stat.totalBytes, freeBytes = stat.availableBytes)
} catch (e: Exception) {
    val file = File("/")
    FileStats(totalBytes = file.totalSpace, freeBytes = file.usableSpace)
}
