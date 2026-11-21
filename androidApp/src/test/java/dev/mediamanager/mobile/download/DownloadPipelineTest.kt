package dev.mediamanager.mobile.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.JobEntity
import dev.mediamanager.mobile.media.LibraryPaths
import java.io.File
import kotlinx.coroutines.flow.first
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
class DownloadPipelineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private lateinit var db: AppDatabase
    private lateinit var paths: LibraryPaths
    private lateinit var pipeline: DownloadPipeline
    private var free = 10_000_000_000L

    private class FakeExtractor : Extractor {
        var failWith: String? = null
        var writeThumb = true
        var mediaBytes = 1_200_000
        var ext = "mp3"
        var cancelled = false
        var deleteMedia = false

        override suspend fun fetch(
            job: JobEntity,
            onProgress: (Float) -> Unit,
            onStage: (String) -> Unit,
            onMeta: (String, Long?, File?) -> Unit,
        ): FetchResult {
            if (cancelled) throw kotlinx.coroutines.CancellationException("cancelled")
            failWith?.let { throw RuntimeException(it) }
            val dir = dirFor(job).apply { mkdirs() }
            val media = File(dir, "abc.$ext").apply { writeBytes(ByteArray(mediaBytes)) }
            if (deleteMedia) media.delete()
            val thumb: File? = if (writeThumb) File(dir, "abc.jpg").apply { writeBytes(ByteArray(40_000)) } else null
            onMeta("Long mix", 222_000, thumb)
            onProgress(1f)
            return FetchResult(
                info = MediaInfo("Long mix", 222_000, ext, 1920, 1080, 1_000),
                mediaFile = media,
                thumbFile = thumb,
            )
        }

        var dirProvider: (JobEntity) -> File = { File("/tmp") }
        private fun dirFor(job: JobEntity): File = dirProvider(job)
    }

    private val fake = FakeExtractor()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        val root = tmp.newFolder("root")
        paths = LibraryPaths(root)
        fake.dirProvider = { job -> paths.incomingDir(job.id) }
        pipeline = DownloadPipeline(
            extractor = fake,
            paths = paths,
            db = db,
            thumbnailer = Thumbnailer(paths),
            freeBytes = { free },
        )
    }

    @After
    fun tearDown() = db.close()

    private fun job(id: String = "j1", mode: String = "audio") = JobEntity(
        id = id,
        dedupeKey = "k-$id",
        url = "https://youtu.be/x",
        mode = mode,
        status = "running",
        scheduledAt = 1,
        createdAt = 1,
        updatedAt = 1,
    )

    @Test
    fun `success moves media into the library, inserts the item and reports bytes`() = runTest {
        val result = pipeline.run(job())
        assertTrue(result is DownloadPipeline.Result.Done)
        val item = db.itemDao().get((result as DownloadPipeline.Result.Done).itemId)!!
        assertEquals("audio", item.kind)
        assertEquals("Long mix", item.title)
        assertEquals(1_200_000L, item.bytes)
        assertTrue(item.thumbBytes > 0)
        assertTrue(item.path.startsWith("library/audio/"))
        assertTrue(paths.resolve(item.path)!!.exists())
        assertFalse(paths.incomingDir("j1").exists())
        assertNull(db.itemDao().observeAll(null).first().firstOrNull { it.kind == "video" })
    }

    @Test
    fun `permanent extractor error leaves no item`() = runTest {
        fake.failWith = "ERROR: Video unavailable"
        val result = pipeline.run(job(id = "j2"))
        assertTrue(result is DownloadPipeline.Result.Failed)
        assertFalse((result as DownloadPipeline.Result.Failed).retryable)
        assertTrue(db.itemDao().observeAll(null).first().isEmpty())
        assertFalse(paths.libraryDir.exists() && paths.libraryDir.walkTopDown().any { it.isFile })
    }

    @Test
    fun `retryable extractor error is marked retryable`() = runTest {
        fake.failWith = "ERROR: Unable to download webpage: timed out"
        val result = pipeline.run(job(id = "j3"))
        assertTrue(result is DownloadPipeline.Result.Failed)
        assertTrue((result as DownloadPipeline.Result.Failed).retryable)
    }

    @Test
    fun `cancelled job deletes its temp dir`() = runTest {
        fake.cancelled = true
        paths.incomingDir("j4").mkdirs()
        val result = pipeline.run(job(id = "j4"))
        assertTrue(result is DownloadPipeline.Result.Cancelled)
        assertFalse(paths.incomingDir("j4").exists())
        assertTrue(db.itemDao().observeAll(null).first().isEmpty())
    }

    @Test
    fun `missing thumbnail still finalizes with null thumbPath`() = runTest {
        fake.writeThumb = false
        val result = pipeline.run(job(id = "j5")) as DownloadPipeline.Result.Done
        val item = db.itemDao().get(result.itemId)!!
        assertNull(item.thumbPath)
        assertEquals(0L, item.thumbBytes)
    }

    @Test
    fun `too little free space fails permanently before copying`() = runTest {
        free = 1_000_000L
        val result = pipeline.run(job(id = "j6"))
        assertTrue(result is DownloadPipeline.Result.Failed)
        assertFalse((result as DownloadPipeline.Result.Failed).retryable)
        assertTrue(result.message.contains("Not enough space"))
        assertTrue(db.itemDao().observeAll(null).first().isEmpty())
        assertFalse(paths.incomingDir("j6").exists())
    }

    @Test
    fun `insert failure removes the moved file`() = runTest {
        // Pre-insert a row with the same path is impossible to force with a random id,
        // so fail the insert by closing the database.
        db.close()
        val result = pipeline.run(job(id = "j7"))
        assertTrue(result is DownloadPipeline.Result.Failed)
        val leftovers = paths.libraryDir.walkTopDown().filter { it.isFile }.toList()
        assertTrue("library must not keep orphan files: $leftovers", leftovers.isEmpty())
    }
}
