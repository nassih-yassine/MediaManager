package dev.mediamanager.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface JobDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(job: JobEntity)

    @Query("SELECT * FROM jobs WHERE id = :id")
    suspend fun get(id: String): JobEntity?

    @Query("SELECT * FROM jobs WHERE status = 'queued' AND scheduledAt <= :now AND attempts < maxAttempts ORDER BY priority DESC, createdAt ASC LIMIT 1")
    suspend fun nextQueued(now: Long): JobEntity?

    @Query("UPDATE jobs SET status = 'running', claimedBy = :worker, claimedAt = :now, heartbeatAt = :now, attempts = attempts + 1, updatedAt = :now WHERE id = :id")
    suspend fun markRunning(id: String, worker: String, now: Long)

    /**
     * Claim one job. Select-then-update inside a transaction: `RETURNING` needs
     * SQLite 3.35 and minSdk 26 ships 3.18.
     */
    @Transaction
    suspend fun claim(worker: String, now: Long): JobEntity? {
        val job = nextQueued(now) ?: return null
        markRunning(job.id, worker, now)
        return job.copy(
            status = "running",
            claimedBy = worker,
            claimedAt = now,
            heartbeatAt = now,
            attempts = job.attempts + 1,
        )
    }

    @Query("SELECT * FROM jobs WHERE dedupeKey = :key AND status IN ('queued','running') LIMIT 1")
    suspend fun activeByDedupeKey(key: String): JobEntity?

    @Query("UPDATE jobs SET heartbeatAt = :now, progress = :progress, stage = :stage, updatedAt = :now WHERE id = :id")
    suspend fun heartbeat(id: String, now: Long, progress: Float, stage: String?)

    @Query("UPDATE jobs SET status = 'done', itemId = :itemId, progress = 1, stage = NULL, error = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markDone(id: String, itemId: String, now: Long)

    @Query("UPDATE jobs SET status = :status, error = :error, stage = NULL, claimedBy = NULL, updatedAt = :now WHERE id = :id")
    suspend fun markFailed(id: String, status: String, error: String?, now: Long)

    @Query("UPDATE jobs SET status = 'queued', claimedBy = NULL, heartbeatAt = NULL, scheduledAt = :scheduledAt, attempts = :attempts, updatedAt = :now WHERE id = :id")
    suspend fun requeue(id: String, scheduledAt: Long, attempts: Int, now: Long)

    @Query("UPDATE jobs SET status = 'queued', stage = :stage, claimedBy = NULL, heartbeatAt = NULL, updatedAt = :now WHERE id = :id")
    suspend fun park(id: String, stage: String, now: Long)

    @Query("UPDATE jobs SET status = 'queued', claimedBy = NULL, heartbeatAt = NULL, updatedAt = :now WHERE status = 'running' AND (heartbeatAt IS NULL OR heartbeatAt < :threshold)")
    suspend fun requeueStale(threshold: Long, now: Long): Int

    @Query("UPDATE jobs SET status = 'cancelled', stage = NULL, claimedBy = NULL, updatedAt = :now WHERE id = :id AND status IN ('queued','running')")
    suspend fun cancel(id: String, now: Long): Int

    @Query("SELECT * FROM jobs WHERE status IN ('queued','running') ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('done','failed','cancelled') ORDER BY updatedAt DESC LIMIT 100")
    fun observeFinished(): Flow<List<JobEntity>>

    @Query("SELECT * FROM jobs WHERE status IN ('queued','running') ORDER BY createdAt ASC")
    suspend fun activeJobs(): List<JobEntity>

    @Query("DELETE FROM jobs WHERE status IN ('done','failed','cancelled')")
    suspend fun clearFinished(): Int

    @Query("DELETE FROM jobs WHERE status IN ('done','failed','cancelled') AND id = :id")
    suspend fun deleteFinished(id: String)
}
