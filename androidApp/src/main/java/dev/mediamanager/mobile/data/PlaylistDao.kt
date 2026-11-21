package dev.mediamanager.mobile.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(playlist: PlaylistEntity)

    @Query("SELECT * FROM playlists WHERE id = :id")
    suspend fun get(id: String): PlaylistEntity?

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    fun observePlaylists(): Flow<List<PlaylistEntity>>

    @Query("SELECT * FROM playlists ORDER BY createdAt DESC")
    suspend fun playlists(): List<PlaylistEntity>

    @Query("SELECT * FROM playlists WHERE sourceUrl = :sourceUrl LIMIT 1")
    suspend fun bySourceUrl(sourceUrl: String): PlaylistEntity?

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE playlists SET name = :name, updatedAt = :now WHERE id = :id")
    suspend fun rename(id: String, name: String, now: Long)

    @Query("UPDATE playlists SET coverPath = :coverPath, updatedAt = :now WHERE id = :id")
    suspend fun setCover(id: String, coverPath: String?, now: Long)

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeItems(playlistId: String): Flow<List<PlaylistItemEntity>>

    @Query("SELECT * FROM playlist_items WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun items(playlistId: String): List<PlaylistItemEntity>

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertRows(rows: List<PlaylistItemEntity>)

    @Transaction
    suspend fun addItems(playlistId: String, itemIds: List<String>, now: Long) {
        val start = maxPosition(playlistId) + 1
        insertRows(
            itemIds.mapIndexed { index, id ->
                PlaylistItemEntity(playlistId, id, start + index, now)
            },
        )
    }

    /** Links an item at the position it held in the source playlist. */
    @Transaction
    suspend fun insertAt(playlistId: String, itemId: String, position: Int, now: Long) {
        insertRows(listOf(PlaylistItemEntity(playlistId, itemId, position, now)))
        touch(playlistId, now)
    }

    @Query("UPDATE playlists SET updatedAt = :now WHERE id = :id")
    suspend fun touch(id: String, now: Long)

    @Query("DELETE FROM playlist_items WHERE playlistId = :playlistId AND itemId = :itemId")
    suspend fun removeItem(playlistId: String, itemId: String)

    @Query("UPDATE playlist_items SET position = :position WHERE playlistId = :playlistId AND itemId = :itemId")
    suspend fun setPosition(playlistId: String, itemId: String, position: Int)

    @Transaction
    suspend fun reorder(playlistId: String, orderedIds: List<String>) {
        orderedIds.forEachIndexed { index, id -> setPosition(playlistId, id, index) }
    }

    @Query("SELECT p.* FROM playlists p INNER JOIN playlist_items i ON i.playlistId = p.id WHERE i.itemId = :itemId ORDER BY p.name")
    suspend fun playlistsContaining(itemId: String): List<PlaylistEntity>

    @Query("SELECT COUNT(*) FROM playlist_items WHERE playlistId = :playlistId")
    suspend fun count(playlistId: String): Int
}
