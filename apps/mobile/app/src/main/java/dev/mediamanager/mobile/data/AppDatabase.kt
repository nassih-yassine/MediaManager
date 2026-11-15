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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE jobs ADD COLUMN metaTitle TEXT")
        db.execSQL("ALTER TABLE jobs ADD COLUMN metaDurationMs INTEGER")
        db.execSQL("ALTER TABLE jobs ADD COLUMN metaThumbPath TEXT")
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
    version = 3,
    exportSchema = true,
)

abstract class AppDatabase : RoomDatabase() {
    abstract fun itemDao(): ItemDao
    abstract fun jobDao(): JobDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao
}
