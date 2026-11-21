package dev.mediamanager.mobile.download

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class JobQueueTest {

    private lateinit var db: AppDatabase
    private var now = 1_000_000L
    private lateinit var queue: JobQueue

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        queue = JobQueue(db.jobDao(), clock = { now })
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun `a playlist url is never queued as one job`() = runTest {
        val result = queue.enqueue(
            "https://www.youtube.com/playlist?list=PLabc123456",
            DownloadMode.AUDIO,
        )

        assertNull(result.job)
        assertFalse(result.duplicate)
        assertTrue(db.jobDao().activeJobs().isEmpty())
    }

    @Test
    fun `a playlist import stores the playlist and position on the job`() = runTest {
        val result = queue.enqueue(
            "https://youtu.be/a",
            DownloadMode.VIDEO,
            playlistId = "p1",
            playlistPosition = 7,
        )

        val job = db.jobDao().get(result.job!!.id)!!
        assertEquals("p1", job.playlistId)
        assertEquals(7, job.playlistPosition)
        assertFalse(result.duplicate)
    }

    @Test
    fun `an item already in the library is reported with its id instead of queued`() = runTest {
        db.itemDao().insert(
            dev.mediamanager.mobile.data.ItemEntity(
                id = "existing1",
                kind = "audio",
                title = "Already here",
                sourceUrl = "https://www.youtube.com/watch?v=abc",
                path = "library/audio/ex/existing1.mp3",
                mime = "audio/mpeg",
                bytes = 1,
                thumbBytes = 0,
                available = true,
                positionMs = 0,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
        val withLibrary = JobQueue(
            dao = db.jobDao(),
            clock = { now },
            existsInLibrary = { url -> db.itemDao().existsBySourceUrl(url) },
            libraryItemId = { url -> db.itemDao().idBySourceUrl(url) },
        )

        val result = withLibrary.enqueue("https://www.youtube.com/watch?v=abc", DownloadMode.AUDIO)

        assertTrue(result.inLibrary)
        assertEquals("existing1", result.libraryItemId)
        assertNull(result.job)
    }

    @Test
    fun `claim takes the oldest queued job and increments attempts`() = runTest {
        val first = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        now += 1
        val second = queue.enqueue("https://youtu.be/b", DownloadMode.AUDIO).job!!
        val claimed = queue.claim("w1")!!
        assertEquals(first.id, claimed.id)
        assertEquals(1, claimed.attempts)
        assertEquals("running", db.jobDao().get(first.id)!!.status)
        assertEquals("queued", db.jobDao().get(second.id)!!.status)
    }

    @Test
    fun `claim skips jobs scheduled in the future`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        db.jobDao().requeue(job.id, now + 60_000, 1, null, now)
        assertNull(queue.claim("w1"))
        now += 60_000
        assertEquals(job.id, queue.claim("w1")!!.id)
    }

    @Test
    fun `duplicate active url returns the existing job flagged`() = runTest {
        val first = queue.enqueue("https://youtu.be/x", DownloadMode.AUDIO)
        val second = queue.enqueue("  https://youtu.be/x  ", DownloadMode.AUDIO)
        assertFalse(first.duplicate)
        assertTrue(second.duplicate)
        assertEquals(first.job!!.id, second.job!!.id)
    }

    @Test
    fun `same url different mode is a new job`() = runTest {
        queue.enqueue("https://youtu.be/x", DownloadMode.AUDIO)
        val video = queue.enqueue("https://youtu.be/x", DownloadMode.VIDEO)
        assertFalse(video.duplicate)
    }

    @Test
    fun `retryable failure backs off and stays queued`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        val willRetry = queue.fail(job.id, "ERROR: something nobody has seen before", permanent = false)
        assertTrue(willRetry)
        val stored = db.jobDao().get(job.id)!!
        assertEquals("queued", stored.status)
        assertEquals(now + 60_000, stored.scheduledAt)
        assertEquals(1, stored.attempts)
    }

    @Test
    fun `network failure gives the attempt back and retries soon`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        val willRetry = queue.fail(job.id, "unable to download video data: connection reset", permanent = false)
        assertTrue(willRetry)
        val stored = db.jobDao().get(job.id)!!
        assertEquals("queued", stored.status)
        assertEquals(0, stored.attempts)
        assertEquals(now + 15_000, stored.scheduledAt)
    }

    @Test
    fun `repeated network drops never fail the job`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        repeat(6) {
            queue.claim("w1")
            assertTrue(queue.fail(job.id, "timed out", permanent = false))
            now += 20_000
        }
        assertEquals("queued", db.jobDao().get(job.id)!!.status)
    }

    @Test
    fun `claiming again clears the previous error`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        queue.fail(job.id, "ERROR: something nobody has seen before", permanent = false)
        assertEquals("ERROR: something nobody has seen before", db.jobDao().get(job.id)!!.error)

        now += 120_000
        queue.claim("w1")
        assertNull(db.jobDao().get(job.id)!!.error)
    }

    @Test
    fun `permanent failure fails immediately`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        assertFalse(queue.fail(job.id, "Video unavailable", permanent = true))
        val stored = db.jobDao().get(job.id)!!
        assertEquals("failed", stored.status)
        assertEquals("Video unavailable", stored.error)
    }

    @Test
    fun `fifth retryable failure becomes failed`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        repeat(4) {
            queue.claim("w1")
            assertTrue(queue.fail(job.id, "ERROR: something nobody has seen before", permanent = false))
            now += 600_000
        }
        queue.claim("w1")
        assertFalse(queue.fail(job.id, "ERROR: something nobody has seen before", permanent = false))
        assertEquals("failed", db.jobDao().get(job.id)!!.status)
        assertEquals(5, db.jobDao().get(job.id)!!.attempts)
    }

    @Test
    fun `reap only requeues stale running jobs`() = runTest {
        val stale = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        now += 61_000
        val fresh = queue.enqueue("https://youtu.be/b", DownloadMode.AUDIO).job!!
        queue.claim("w2")

        val reaped = queue.reapExpired()

        assertEquals(1, reaped)
        assertEquals("queued", db.jobDao().get(stale.id)!!.status)
        // The attempt was cut short by a killed process, so it is given back.
        assertEquals(0, db.jobDao().get(stale.id)!!.attempts)
        assertEquals("running", db.jobDao().get(fresh.id)!!.status)
    }

    @Test
    fun `cancel marks cancelled and a cancelled job is never claimed`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.cancel(job.id)
        assertNull(queue.claim("w1"))
        assertEquals("cancelled", db.jobDao().get(job.id)!!.status)
    }

    @Test
    fun `requeue keeps the attempt count`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        queue.claim("w1")
        queue.requeue(job.id, stage = "waiting for Wi-Fi")
        val stored = db.jobDao().get(job.id)!!
        assertEquals("queued", stored.status)
        assertEquals("waiting for Wi-Fi", stored.stage)
        assertEquals(1, stored.attempts)
    }

    @Test
    fun `absurd progress left by the old downloader is clamped`() = runTest {
        val job = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        db.jobDao().insert(job.copy(progress = 14.25f))
        queue.reapExpired()
        assertEquals(1f, db.jobDao().get(job.id)!!.progress, 0.0001f)
    }

    @Test
    fun `two workers never claim the same job`() = runTest {
        val first = queue.enqueue("https://youtu.be/a", DownloadMode.AUDIO).job!!
        val second = queue.enqueue("https://youtu.be/b", DownloadMode.AUDIO).job!!
        val a = queue.claim("w1")!!
        val b = queue.claim("w2")!!
        assertEquals(setOf(first.id, second.id), setOf(a.id, b.id))
        assertTrue(a.claimedBy != b.claimedBy)
        assertNull(queue.claim("w3"))
    }

    @Test
    fun `a url already in the library is not queued again`() = runTest {
        val guarded = JobQueue(
            db.jobDao(),
            clock = { now },
            newId = { java.util.UUID.randomUUID().toString() },
            existsInLibrary = { it.contains("kept") },
        )
        val outcome = guarded.enqueue("https://youtu.be/kept", DownloadMode.AUDIO)
        assertTrue(outcome.inLibrary)
        assertNull(outcome.job)
        assertEquals(0, db.jobDao().queuedCount())
    }
}
