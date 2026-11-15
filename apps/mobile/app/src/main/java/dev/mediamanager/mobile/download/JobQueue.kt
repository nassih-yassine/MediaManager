package dev.mediamanager.mobile.download

import dev.mediamanager.mobile.data.JobDao
import dev.mediamanager.mobile.data.JobEntity
import java.util.UUID

class JobQueue(
    private val dao: JobDao,
    private val clock: () -> Long = System::currentTimeMillis,
    private val newId: () -> String = { UUID.randomUUID().toString() },
    /** True when the library already holds this URL; a second copy is never queued. */
    private val existsInLibrary: suspend (String) -> Boolean = { false },
) {

    data class EnqueueResult(
        val job: JobEntity?,
        val duplicate: Boolean,
        val inLibrary: Boolean = false,
    )

    suspend fun enqueue(url: String, mode: DownloadMode): EnqueueResult {
        val now = clock()
        val cleanUrl = normalizeUrl(url)
        val key = dedupeKey(cleanUrl, mode)
        dao.activeByDedupeKey(key)?.let { return EnqueueResult(it, duplicate = true) }
        if (existsInLibrary(cleanUrl)) return EnqueueResult(null, duplicate = false, inLibrary = true)
        val job = JobEntity(
            id = newId(),
            dedupeKey = key,
            url = cleanUrl,
            mode = mode.dbValue,
            status = "queued",
            progress = 0f,
            attempts = 0,
            scheduledAt = now,
            createdAt = now,
            updatedAt = now,
        )
        dao.insert(job)
        return EnqueueResult(job, duplicate = false)
    }

    suspend fun claim(workerId: String): JobEntity? = dao.claim(workerId, clock())

    suspend fun heartbeat(id: String, progress: Float, stage: String?) =
        dao.heartbeat(id, clock(), progress, stage)

    suspend fun finish(id: String, itemId: String) = dao.markDone(id, itemId, clock())

    suspend fun fail(id: String, message: String, permanent: Boolean): Boolean {
        val job = dao.get(id) ?: return false
        val now = clock()
        if (permanent || job.attempts >= job.maxAttempts) {
            dao.markFailed(id, "failed", message, now)
            return false
        }
        // A dropped connection must not burn the retry budget: give the attempt
        // back and come around again shortly.
        if (isNetworkTrouble(message)) {
            dao.requeue(id, now + 15_000, (job.attempts - 1).coerceAtLeast(0), message, now)
            return true
        }
        val backoff = 60_000L shl (job.attempts - 1).coerceIn(0, 5)
        // Keep the reason visible on the row: a retry that says nothing is undebuggable.
        dao.requeue(id, now + backoff, job.attempts, message, now)
        return true
    }

    /** Re-queue without touching the attempt count: network drops and lease recovery. */
    suspend fun requeue(id: String, stage: String? = null) {
        val job = dao.get(id) ?: return
        val now = clock()
        if (stage != null) dao.park(id, stage, now)
        else dao.requeue(id, now, job.attempts, job.error, now)
    }

    suspend fun cancel(id: String) = dao.cancel(id, clock())

    suspend fun reapExpired(leaseMs: Long = 60_000L): Int {
        dao.clampProgress()
        return dao.requeueStale(clock() - leaseMs, clock())
    }
}
