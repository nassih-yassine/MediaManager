package dev.mediamanager.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface QueueDao {

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    fun observe(): Flow<List<QueueItemEntity>>

    @Query("SELECT * FROM queue_items ORDER BY position ASC")
    suspend fun all(): List<QueueItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(rows: List<QueueItemEntity>)

    @Query("DELETE FROM queue_items")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(itemIds: List<String>) {
        clear()
        insertAll(itemIds.mapIndexed { index, id -> QueueItemEntity(index, id) })
    }

    @Transaction
    suspend fun move(from: Int, to: Int) {
        val ids = all().map { it.itemId }.toMutableList()
        if (from !in ids.indices || to !in ids.indices || from == to) return
        val moved = ids.removeAt(from)
        ids.add(to, moved)
        replaceAll(ids)
    }

    @Transaction
    suspend fun removeAt(position: Int) {
        val ids = all().sortedBy { it.position }.map { it.itemId }
        if (position !in ids.indices) return
        replaceAll(ids.filterIndexed { index, _ -> index != position })
    }
}
