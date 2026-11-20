package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.ItemEntity
import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.CancellationException
import java.io.File
import java.util.UUID

data class FetchResult(
    val info: MediaInfo,
    val mediaFile: File,
    val thumbFile: File?,
)

/** Downloads into `.incoming/<jobId>/` and returns what landed there. */
interface Extractor {
    suspend fun fetch(
        job: JobEntity,
        onProgress: (Float) -> Unit,
        onStage: (String) -> Unit = {},
        onMeta: (title: String, durationMs: Long?, thumbFile: File?) -> Unit = { _, _, _ -> },
    ): FetchResult
}

class Thumbnailer(private val paths: LibraryPaths) {
    /** Copies (never moves) the sidecar JPEG into `thumbs/` and reports its size. */
    fun saveThumb(itemId: String, sourceJpeg: File): Long {
        val target = paths.thumbFile(itemId)
        target.parentFile?.mkdirs()
        sourceJpeg.copyTo(target, overwrite = true)
        return target.length()
    }
}

/**
 * Fetch → space check → move → insert → done. The order is load-bearing: a crash after
 * the move but before the insert leaves a file, never a row pointing at nothing.
 */
class DownloadPipeline(
    private val extractor: Extractor,
    private val paths: LibraryPaths,
    private val db: AppDatabase,
    private val thumbnailer: Thumbnailer,
    private val freeBytes: () -> Long,
    private val now: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
) {

    sealed interface Result {
        data class Done(val itemId: String) : Result
        data class Failed(val message: String, val retryable: Boolean) : Result
        data object Cancelled : Result
    }

    suspend fun run(
        job: JobEntity,
        onProgress: (Float) -> Unit = {},
        onStage: (String) -> Unit = {},
        onMeta: (title: String, durationMs: Long?, thumbFile: File?) -> Unit = { _, _, _ -> },
    ): Result {
        val incoming = paths.incomingDir(job.id)
        // A killed process leaves partial files behind; never mix them into this run.
        runCatching { paths.deleteRecursively(incoming) }
        val fetched = try {
            extractor.fetch(job, onProgress, onStage, onMeta)
        } catch (c: CancellationException) {
            paths.deleteRecursively(incoming)
            return Result.Cancelled
        } catch (e: Exception) {
            paths.deleteRecursively(incoming)
            val failure = classify(e.message ?: "Download failed")
            return when (failure) {
                is Failure.Permanent -> Result.Failed(failure.message, retryable = false)
                is Failure.Retryable -> Result.Failed(failure.message, retryable = true)
            }
        }

        if (!fetched.mediaFile.exists()) {
            paths.deleteRecursively(incoming)
            return Result.Failed("The download produced no file", retryable = true)
        }

        when (val verdict = checkSpace(fetched.info.approxBytes, freeBytes())) {
            is SpaceVerdict.TooLarge -> {
                paths.deleteRecursively(incoming)
                return Result.Failed(
                    "Not enough space: needs about ${fetched.info.approxBytes?.div(1024 * 1024)} MB, " +
                        "${verdict.freeBytes / (1024 * 1024)} MB free",
                    retryable = false,
                )
            }
            SpaceVerdict.Fits -> Unit
        }

        onStage("finalizing")
        val itemId = newId()
        val kind = job.mode
        val ext = fetched.mediaFile.extension.ifBlank { "bin" }
        val target = paths.itemFile(kind, itemId, ext)
        target.parentFile?.mkdirs()
        if (!fetched.mediaFile.renameTo(target)) {
            fetched.mediaFile.copyTo(target, overwrite = true)
            fetched.mediaFile.delete()
        }

        val thumbBytes = fetched.thumbFile
            ?.takeIf { it.exists() }
            ?.let { runCatching { thumbnailer.saveThumb(itemId, it) }.getOrDefault(0L) }
            ?: 0L

        val stamp = now()
        val item = ItemEntity(
            id = itemId,
            kind = kind,
            title = fetched.info.title,
            sourceUrl = job.url,
            path = paths.itemRelativePath(kind, itemId, ext),
            thumbPath = if (thumbBytes > 0) paths.thumbRelativePath(itemId) else null,
            mime = mimeFor(ext),
            bytes = target.length(),
            thumbBytes = thumbBytes,
            available = true,
            durationMs = fetched.info.durationMs,
            width = fetched.info.width,
            height = fetched.info.height,
            createdAt = stamp,
            updatedAt = stamp,
        )

        val inserted = runCatching { db.itemDao().insert(item) }.isSuccess
        if (!inserted) {
            target.delete()
            if (thumbBytes > 0) paths.thumbFile(itemId).delete()
            paths.deleteRecursively(incoming)
            return Result.Failed("Could not save the item", retryable = false)
        }

        paths.deleteRecursively(incoming)
        return Result.Done(itemId)
    }

    private fun mimeFor(ext: String): String = when (ext.lowercase()) {
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus" -> "audio/opus"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mp4" -> "video/mp4"
        else -> "application/octet-stream"
    }
}
