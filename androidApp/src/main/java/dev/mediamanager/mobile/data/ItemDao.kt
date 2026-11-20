package dev.mediamanager.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Query("SELECT * FROM items WHERE (:kind IS NULL OR kind = :kind) ORDER BY createdAt DESC")
    fun observeAll(kind: String?): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY bytes DESC LIMIT :limit")
    fun observeLargest(limit: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE title LIKE '%' || :q || '%' ORDER BY createdAt DESC")
    fun observeSearch(q: String): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun get(id: String): ItemEntity?

    @Query("SELECT * FROM items WHERE sourceUrl = :url ORDER BY createdAt DESC LIMIT 1")
    suspend fun getBySourceUrl(url: String): ItemEntity?

    /** Used to refuse a second copy of something already downloaded. */
    @Query("SELECT EXISTS(SELECT 1 FROM items WHERE sourceUrl = :url)")
    suspend fun existsBySourceUrl(url: String): Boolean

    @Query("SELECT * FROM items ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecentlyAdded(limit: Int): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items ORDER BY createdAt DESC")
    suspend fun all(): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: ItemEntity)

    @Query("UPDATE items SET positionMs = :positionMs, lastPlayedAt = :playedAt, updatedAt = :playedAt WHERE id = :id")
    suspend fun updatePosition(id: String, positionMs: Long, playedAt: Long)

    @Query("UPDATE items SET available = :available, updatedAt = :now WHERE id = :id")
    suspend fun setAvailable(id: String, available: Boolean, now: Long)

    @Query("UPDATE items SET bytes = :bytes, updatedAt = :now WHERE id = :id")
    suspend fun setBytes(id: String, bytes: Long, now: Long)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun delete(id: String)

    @Query("SELECT kind, SUM(bytes) AS bytes, COUNT(*) AS count FROM items GROUP BY kind")
    fun observeTotalsByKind(): Flow<List<KindTotal>>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM items")
    fun observeTotalBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(thumbBytes), 0) FROM items")
    fun observeThumbBytes(): Flow<Long>

    @Query("SELECT COALESCE(SUM(bytes), 0) FROM items")
    suspend fun totalBytes(): Long

    @Query("SELECT COUNT(*) FROM items")
    suspend fun count(): Int
}
