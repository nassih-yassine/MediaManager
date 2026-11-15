package dev.mediamanager.mobile.media

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.ItemEntity
import dev.mediamanager.mobile.data.PlaylistEntity
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class ItemRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var paths: LibraryPaths
    private lateinit var repo: ItemRepository
    private var now = 5_000L

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        paths = LibraryPaths(tmp.newFolder("library-root"))
        repo = ItemRepository(db, paths, now = { now })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(
        id: String,
        bytesWritten: Int = 1_200_000,
        withThumb: Boolean = true,
    ): ItemEntity {
        val media = paths.itemFile("audio", id, "mp3")
        media.parentFile!!.mkdirs()
        media.writeBytes(ByteArray(bytesWritten))
        val thumbPath = if (withThumb) {
            val thumb = paths.thumbFile(id)
            thumb.parentFile!!.mkdirs()
            thumb.writeBytes(ByteArray(40_000))
            paths.thumbRelativePath(id)
        } else null
        val item = ItemEntity(
            id = id,
            kind = "audio",
            title = "Item $id",
            path = paths.itemRelativePath("audio", id, "mp3"),
            thumbPath = thumbPath,
            mime = "audio/mpeg",
            bytes = bytesWritten.toLong(),
            thumbBytes = if (withThumb) 40_000 else 0,
            createdAt = 1,
            updatedAt = 1,
        )
        db.itemDao().insert(item)
        return item
    }

    @Test
    fun `delete removes row, file, thumb and memberships`() = runTest {
        seed("ab12cd")
        db.playlistDao().upsert(PlaylistEntity("p1", "Mix", 1, 1))
        db.playlistDao().addItems("p1", listOf("ab12cd"), now = 1)
        db.queueDao().replaceAll(listOf("ab12cd"))

        val result = repo.delete("ab12cd")

        assertEquals(1_240_000L, result.freedBytes)
        assertFalse(paths.itemFile("audio", "ab12cd", "mp3").exists())
        assertFalse(paths.thumbFile("ab12cd").exists())
        assertNull(db.itemDao().get("ab12cd"))
        assertTrue(db.playlistDao().items("p1").isEmpty())
        assertTrue(db.queueDao().all().isEmpty())
    }

    @Test
    fun `delete of an already missing file still removes the row`() = runTest {
        seed("ab12cd")
        paths.itemFile("audio", "ab12cd", "mp3").delete()
        val result = repo.delete("ab12cd")
        assertEquals(40_000L, result.freedBytes)
        assertNull(db.itemDao().get("ab12cd"))
    }

    @Test
    fun `delete of an unknown id frees nothing and does not throw`() = runTest {
        assertEquals(0L, repo.delete("nope").freedBytes)
    }

    @Test
    fun `recheck flags missing files and wrong sizes`() = runTest {
        seed("aa11", bytesWritten = 100)
        seed("bb22", bytesWritten = 100)
        seed("cc33", bytesWritten = 100)
        paths.itemFile("audio", "bb22", "mp3").delete()
        val grown = paths.itemFile("audio", "cc33", "mp3")
        grown.writeBytes(ByteArray(900))

        val result = repo.recheck()

        assertEquals(listOf("bb22"), result.missing)
        assertEquals(listOf("cc33" to 900L), result.resized)
        assertFalse(db.itemDao().get("bb22")!!.available)
        assertTrue(db.itemDao().get("aa11")!!.available)
        assertEquals(900L, db.itemDao().get("cc33")!!.bytes)
    }

    @Test
    fun `recheck brings a file back when it reappears`() = runTest {
        seed("dd44", bytesWritten = 100)
        paths.itemFile("audio", "dd44", "mp3").delete()
        repo.recheck()
        assertFalse(db.itemDao().get("dd44")!!.available)

        paths.itemFile("audio", "dd44", "mp3").writeBytes(ByteArray(100))
        repo.recheck()
        assertTrue(db.itemDao().get("dd44")!!.available)
    }
}
