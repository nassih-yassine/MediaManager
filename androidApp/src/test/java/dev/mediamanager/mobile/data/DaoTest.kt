package dev.mediamanager.mobile.data

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class DaoTest {

    private lateinit var db: AppDatabase

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
    }

    @After
    fun tearDown() = db.close()

    private fun item(
        id: String,
        kind: String = "audio",
        title: String = "Item $id",
        bytes: Long = 1_000,
        createdAt: Long = 1,
        positionMs: Long = 0,
        durationMs: Long? = null,
        lastPlayedAt: Long? = null,
    ) = ItemEntity(
        id = id,
        kind = kind,
        title = title,
        path = "library/$kind/${id.take(2)}/$id.mp3",
        mime = if (kind == "audio") "audio/mpeg" else "video/mp4",
        bytes = bytes,
        positionMs = positionMs,
        durationMs = durationMs,
        lastPlayedAt = lastPlayedAt,
        createdAt = createdAt,
        updatedAt = createdAt,
    )

    @Test
    fun `newest first and kind filter`() = runTest {
        db.itemDao().insert(item("a1", createdAt = 1))
        db.itemDao().insert(item("a2", createdAt = 2))
        db.itemDao().insert(item("v1", kind = "video", createdAt = 3))
        assertEquals(listOf("v1", "a2", "a1"), db.itemDao().observeAll(null).first().map { it.id })
        assertEquals(listOf("a2", "a1"), db.itemDao().observeAll("audio").first().map { it.id })
        assertEquals(listOf("v1"), db.itemDao().observeAll("video").first().map { it.id })
    }

    @Test
    fun `largest sort puts the big file first`() = runTest {
        db.itemDao().insert(item("a1", bytes = 4_000))
        db.itemDao().insert(item("a2", bytes = 1_200_000_000))
        assertEquals(listOf("a2", "a1"), db.itemDao().observeLargest(10).first().map { it.id })
    }

    @Test
    fun `search is case-insensitive contains`() = runTest {
        db.itemDao().insert(item("a1", title = "Long MIX 2026"))
        db.itemDao().insert(item("a2", title = "Concert"))
        assertEquals(listOf("a1"), db.itemDao().observeSearch("mix").first().map { it.id })
    }

    @Test
    fun `totals group by kind`() = runTest {
        db.itemDao().insert(item("a1", bytes = 1_000))
        db.itemDao().insert(item("a2", bytes = 2_000))
        db.itemDao().insert(item("v1", kind = "video", bytes = 5_000, createdAt = 2))
        val totals = db.itemDao().observeTotalsByKind().first().associateBy { it.kind }
        assertEquals(3_000L, totals.getValue("audio").bytes)
        assertEquals(2, totals.getValue("audio").count)
        assertEquals(5_000L, totals.getValue("video").bytes)
        assertEquals(8_000L, db.itemDao().observeTotalBytes().first())
    }

    @Test
    fun `home shelves pick the right rows`() = runTest {
        db.itemDao().insert(item("a1", createdAt = 1, positionMs = 4_000, durationMs = 100_000, lastPlayedAt = 100))
        db.itemDao().insert(item("a2", createdAt = 2, positionMs = 1_000, durationMs = 100_000, lastPlayedAt = 200))
        db.itemDao().insert(item("a3", createdAt = 3, positionMs = 98_000, durationMs = 100_000, lastPlayedAt = 50))
        db.itemDao().insert(item("a4", createdAt = 4))

        assertEquals(
            listOf("a4", "a3", "a2"),
            db.itemDao().observeRecentlyAdded(3).first().map { it.id },
        )
    }

    @Test
    fun `deleting an item drops playlist and queue rows`() = runTest {
        db.itemDao().insert(item("a1"))
        db.itemDao().insert(item("a2", createdAt = 2))
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Mix", createdAt = 1, updatedAt = 1))
        db.playlistDao().addItems("p1", listOf("a1", "a2"), now = 1)
        db.queueDao().replaceAll(listOf("a1", "a2"))

        db.itemDao().delete("a1")

        assertEquals(listOf("a2"), db.playlistDao().items("p1").map { it.itemId })
        assertEquals(listOf("a2"), db.queueDao().all().map { it.itemId })
        assertNull(db.itemDao().get("a1"))
    }

    @Test
    fun `reorder persists the given order`() = runTest {
        listOf("a1", "a2", "a3").forEachIndexed { index, id ->
            db.itemDao().insert(item(id, createdAt = index.toLong()))
        }
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Mix", createdAt = 1, updatedAt = 1))
        db.playlistDao().addItems("p1", listOf("a1", "a2", "a3"), now = 1)

        db.playlistDao().reorder("p1", listOf("a3", "a2", "a1"))

        assertEquals(listOf("a3", "a2", "a1"), db.playlistDao().items("p1").map { it.itemId })
        assertEquals(listOf(0, 1, 2), db.playlistDao().items("p1").map { it.position })
    }

    @Test
    fun `adding the same item to a playlist twice is a no-op`() = runTest {
        db.itemDao().insert(item("a1"))
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Mix", createdAt = 1, updatedAt = 1))
        db.playlistDao().addItems("p1", listOf("a1"), now = 1)
        db.playlistDao().addItems("p1", listOf("a1"), now = 2)
        assertEquals(1, db.playlistDao().count("p1"))
    }

    @Test
    fun `queue move and remove keep positions dense`() = runTest {
        listOf("a1", "a2", "a3").forEach { db.itemDao().insert(item(it)) }
        db.queueDao().replaceAll(listOf("a1", "a2", "a3"))
        db.queueDao().move(0, 2)
        assertEquals(listOf("a2", "a3", "a1"), db.queueDao().all().map { it.itemId })
        db.queueDao().removeAt(1)
        assertEquals(listOf(0, 1), db.queueDao().all().map { it.position })
        assertEquals(listOf("a2", "a1"), db.queueDao().all().map { it.itemId })
    }

    @Test
    fun `renaming a playlist keeps its items`() = runTest {
        db.itemDao().insert(item("a1"))
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Old", createdAt = 1, updatedAt = 1))
        db.playlistDao().addItems("p1", listOf("a1"), now = 1)
        db.playlistDao().rename("p1", "New", now = 2)
        assertEquals("New", db.playlistDao().get("p1")!!.name)
        assertEquals(1, db.playlistDao().count("p1"))
    }

    @Test
    fun `playlist cover path round-trips`() = runTest {
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Mix", createdAt = 1, updatedAt = 1))
        db.playlistDao().setCover("p1", "covers/p1.jpg", now = 2)
        assertEquals("covers/p1.jpg", db.playlistDao().get("p1")!!.coverPath)
        db.playlistDao().setCover("p1", null, now = 3)
        assertNull(db.playlistDao().get("p1")!!.coverPath)
    }

    @Test
    fun `deleting a playlist keeps its items`() = runTest {
        db.itemDao().insert(item("a1"))
        db.playlistDao().upsert(PlaylistEntity(id = "p1", name = "Mix", createdAt = 1, updatedAt = 1))
        db.playlistDao().addItems("p1", listOf("a1"), now = 1)
        db.playlistDao().delete("p1")
        assertEquals(1, db.itemDao().count())
        assertTrue(db.playlistDao().items("p1").isEmpty())
    }
}
