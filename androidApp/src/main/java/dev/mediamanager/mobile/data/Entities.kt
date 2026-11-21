package dev.mediamanager.mobile.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "items",
    indices = [Index("kind", "createdAt"), Index("title")],
)
data class ItemEntity(
    @PrimaryKey val id: String,
    val kind: String,
    val title: String,
    val sourceUrl: String? = null,
    val path: String,
    val thumbPath: String? = null,
    val mime: String,
    val bytes: Long,
    val thumbBytes: Long = 0,
    val available: Boolean = true,
    val durationMs: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val positionMs: Long = 0,
    val lastPlayedAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "jobs",
    indices = [Index("status", "priority", "scheduledAt"), Index("dedupeKey")],
)
data class JobEntity(
    @PrimaryKey val id: String,
    val dedupeKey: String,
    val url: String,
    val mode: String,
    val status: String = "queued",
    val stage: String? = null,
    val progress: Float = 0f,
    val error: String? = null,
    val metaTitle: String? = null,
    val metaDurationMs: Long? = null,
    val metaThumbPath: String? = null,
    val itemId: String? = null,
    /** Set when this job belongs to a playlist import; the item lands here on success. */
    val playlistId: String? = null,
    val playlistPosition: Int? = null,
    val attempts: Int = 0,
    val maxAttempts: Int = 5,
    val priority: Int = 0,
    val scheduledAt: Long,
    val claimedBy: String? = null,
    val claimedAt: Long? = null,
    val heartbeatAt: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    /** The playlist it was imported from, for a future refresh. */
    val sourceUrl: String? = null,
    val coverPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "playlist_items",
    primaryKeys = ["playlistId", "itemId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId", "position"), Index("itemId")],
)
data class PlaylistItemEntity(val playlistId: String, val itemId: String, val position: Int, val addedAt: Long)

@Entity(
    tableName = "queue_items",
    foreignKeys = [
        ForeignKey(
            entity = ItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("itemId")],
)
data class QueueItemEntity(@PrimaryKey val position: Int, val itemId: String)

data class KindTotal(val kind: String, val bytes: Long, val count: Int)
