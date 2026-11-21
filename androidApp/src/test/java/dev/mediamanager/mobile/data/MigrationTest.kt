package dev.mediamanager.mobile.data

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Room throws at open time when a migration does not match the entity, which on a phone
 * means the app cannot start after an update. Each migration runs against a database
 * shaped like the version before it.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MigrationTest {

    private fun open(): SupportSQLiteDatabase {
        val context: Application = ApplicationProvider.getApplicationContext()
        val config = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("migration-${System.nanoTime()}.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) = Unit
                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    private fun columns(db: SupportSQLiteDatabase, table: String): Set<String> =
        db.query("PRAGMA table_info($table)").use { cursor ->
            buildSet {
                val name = cursor.getColumnIndex("name")
                while (cursor.moveToNext()) add(cursor.getString(name))
            }
        }

    @Test
    fun `migration 1 to 2 adds the playlist cover column`() {
        val db = open()
        db.execSQL("CREATE TABLE playlists (id TEXT NOT NULL PRIMARY KEY)")
        MIGRATION_1_2.migrate(db)
        assertTrue(columns(db, "playlists").contains("coverPath"))
    }

    @Test
    fun `migration 2 to 3 adds the job metadata columns`() {
        val db = open()
        db.execSQL("CREATE TABLE jobs (id TEXT NOT NULL PRIMARY KEY)")
        MIGRATION_2_3.migrate(db)
        assertEquals(
            setOf("id", "metaTitle", "metaDurationMs", "metaThumbPath"),
            columns(db, "jobs"),
        )
    }

    @Test
    fun `migration 3 to 4 adds playlist import columns`() {
        val db = open()
        db.execSQL("CREATE TABLE jobs (id TEXT NOT NULL PRIMARY KEY)")
        db.execSQL("CREATE TABLE playlists (id TEXT NOT NULL PRIMARY KEY)")
        MIGRATION_3_4.migrate(db)
        assertTrue(columns(db, "jobs").containsAll(listOf("playlistId", "playlistPosition")))
        assertTrue(columns(db, "playlists").contains("sourceUrl"))
    }
}
