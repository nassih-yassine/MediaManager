package dev.mediamanager.mobile.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN coverPath TEXT")
    }
}

@Database(
    entities = [
        ItemEntity::class,
        JobEntity::class,
        PlaylistEntity::class,
        PlaylistItemEntity::class,
        QueueItemEntity::class,
    ],
    version = 2,
    exportSchema = true,
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun jobDao(): JobDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
}
