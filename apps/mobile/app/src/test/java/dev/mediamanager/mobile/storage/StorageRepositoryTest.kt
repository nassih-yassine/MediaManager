package dev.mediamanager.mobile.storage

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.ItemEntity
import dev.mediamanager.mobile.media.LibraryPaths
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
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
class StorageRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var paths: LibraryPaths
    private lateinit var repo: StorageRepository
    private val temp = MutableStateFlow(0L)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        paths = LibraryPaths(tmp.newFolder("root"))
        repo = StorageRepository(db, paths, stats = { FileStats(128_000_000_000, 82_000_000_000) })
    }

    @After
    fun tearDown() = db.close()

    private suspend fun seed(id: String, kind: String, bytes: Long, thumb: Long = 1_000) {
        db.itemDao().insert(
            ItemEntity(
                id = id,
                kind = kind,
                title = "Item $id",
                path = "library/$kind/${id.take(2)}/$id.bin",
                mime = "application/octet-stream",
                bytes = bytes,
                thumbBytes = thumb,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    @Test
    fun `snapshot sums media, thumbs and temp`() = runTest {
        seed("a1", "audio", 2_110_000_000)
        seed("a2", "audio", 1_000)
        seed("v1", "video", 2_060_000_000)
        temp.value = 12_000_000

        val snap = repo.observe(temp).first()

        assertEquals(4_170_001_000L, snap.mediaBytes)
        assertEquals(2_110_001_000L, snap.audioBytes)
        assertEquals(2_060_000_000L, snap.videoBytes)
        assertEquals(3_000L, snap.thumbBytes)
        assertEquals(12_000_000L, snap.tempBytes)
        assertEquals(4_182_004_000L, snap.libraryBytes)
        assertEquals(3, snap.itemCount)
        assertEquals(2, snap.audioCount)
        assertEquals(1, snap.videoCount)
        assertEquals(128_000_000_000L, snap.deviceTotal)
        assertEquals(82_000_000_000L, snap.deviceFree)
    }

    @Test
    fun `empty library is all zero`() = runTest {
        val snap = repo.observe(temp).first()
        assertEquals(0L, snap.libraryBytes)
        assertEquals(0, snap.itemCount)
        assertEquals(0L, snap.thumbBytes)
    }

    @Test
    fun `covers are measured and counted in the library total`() = runTest {
        paths.coversDir.mkdirs()
        paths.coverFile("p1").writeBytes(ByteArray(20_000))
        paths.coverFile("p2").writeBytes(ByteArray(5_000))
        assertEquals(25_000L, repo.measureCovers())

        val covers = MutableStateFlow(repo.measureCovers())
        val snap = repo.observe(temp, covers).first()
        assertEquals(25_000L, snap.coversBytes)
        assertEquals(25_000L, snap.libraryBytes)
    }

    @Test
    fun `temp is the sum of incoming job dirs`() = runTest {
        val a = paths.incomingDir("j1").apply { mkdirs() }
        a.resolve("a.mp4").writeBytes(ByteArray(100))
        a.resolve("a.part").writeBytes(ByteArray(50))
        val b = paths.incomingDir("j2").apply { mkdirs() }
        b.resolve("b.mp3").writeBytes(ByteArray(25))
        assertEquals(175L, repo.measureTemp())
    }

    @Test
    fun `temp of a missing incoming root is zero`() = runTest {
        assertEquals(0L, repo.measureTemp())
    }

    @Test
    fun `largest items are ranked by bytes with share`() = runTest {
        seed("a1", "audio", 1_200_000_000, thumb = 0)
        seed("a2", "audio", 400_000_000, thumb = 0)
        val rows = repo.largestItems(10).first()
        assertEquals(listOf("a1", "a2"), rows.map { it.item.id })
        assertEquals(0.75f, rows[0].share, 0.001f)
        assertEquals(0.25f, rows[1].share, 0.001f)
    }

    @Test
    fun `shares never go negative or above one`() {
        assertEquals(0f, share(10, 0), 0.0001f)
        assertEquals(1f, share(10, 5), 0.0001f)
        assertEquals(0f, share(-10, 5), 0.0001f)
        assertEquals(0.5f, share(5, 10), 0.0001f)
    }

    @Test
    fun `used fraction clamps to zero and one`() {
        assertEquals(0f, usedFraction(0, 0), 0.0001f)
        assertEquals(0f, usedFraction(100, 100), 0.0001f)
        assertEquals(0.6f, usedFraction(100, 40), 0.0001f)
        assertEquals(1f, usedFraction(100, -5), 0.0001f)
        assertTrue(usedFraction(128_000_000_000, 82_000_000_000) in 0.35f..0.37f)
    }

    @Test
    fun `deleting an item drops the totals`() = runTest {
        seed("a1", "audio", 1_000_000)
        db.itemDao().delete("a1")
        assertEquals(0L, repo.observe(temp).first().mediaBytes)
    }
}
