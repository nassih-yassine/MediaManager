package dev.mediamanager.mobile.ui.add

import android.app.Application
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import dev.mediamanager.mobile.data.AppDatabase
import dev.mediamanager.mobile.data.ItemEntity
import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.JobQueue
import dev.mediamanager.mobile.download.PlaylistEntry
import dev.mediamanager.mobile.download.PlaylistImportRunner
import dev.mediamanager.mobile.download.summarizePlaylist
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class PlaylistImportRunnerTest {

    private lateinit var db: AppDatabase
    private lateinit var queue: JobQueue
    private var serviceStarts = 0

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        queue = JobQueue(
            dao = db.jobDao(),
            existsInLibrary = { url -> db.itemDao().existsBySourceUrl(url) },
            libraryItemId = { url -> db.itemDao().idBySourceUrl(url) },
        )
        serviceStarts = 0
    }

    @After
    fun tearDown() = db.close()

    private fun runner() = PlaylistImportRunner(db, queue) { serviceStarts++ }

    private fun entry(id: String) = PlaylistEntry(
        url = "https://www.youtube.com/watch?v=$id",
        title = "Video $id",
        durationMs = 1_000,
    )

    private fun summary(vararg ids: String) = summarizePlaylist(
        listId = "PL1",
        name = "Imported mix",
        uploader = "Channel",
        coverUrl = null,
        entries = ids.map(::entry),
    )

    private suspend fun existingItem(id: String, url: String) {
        db.itemDao().insert(
            ItemEntity(
                id = id,
                kind = "audio",
                title = "Existing $id",
                sourceUrl = url,
                path = "library/audio/ex/$id.mp3",
                mime = "audio/mpeg",
                bytes = 1,
                thumbBytes = 0,
                available = true,
                positionMs = 0,
                createdAt = 1,
                updatedAt = 1,
            ),
        )
    }

    @Test
    fun `existing library items are linked at their source positions`() = runTest {
        existingItem("lib1", "https://www.youtube.com/watch?v=a")
        val summary = summary("a", "b")

        val outcome = runner().import(summary, DownloadMode.AUDIO, summary.entries)

        assertEquals(1, outcome.queued)
        assertEquals(1, outcome.existing)
        val playlist = db.playlistDao().bySourceUrl("https://www.youtube.com/playlist?list=PL1")!!
        val rows = db.playlistDao().items(playlist.id)
        assertEquals(listOf("lib1"), rows.map { it.itemId })
        assertEquals(0, rows.single().position)

        val job = db.jobDao().activeJobs().single()
        assertEquals(playlist.id, job.playlistId)
        assertEquals(1, job.playlistPosition)
        assertEquals(1, serviceStarts)
    }

    @Test
    fun `a playlist made only of existing items still gets created`() = runTest {
        existingItem("lib1", "https://www.youtube.com/watch?v=a")
        existingItem("lib2", "https://www.youtube.com/watch?v=b")
        val summary = summary("a", "b")

        val outcome = runner().import(summary, DownloadMode.VIDEO, summary.entries)

        assertEquals(0, outcome.queued)
        assertEquals(2, outcome.existing)
        val playlist = db.playlistDao().bySourceUrl("https://www.youtube.com/playlist?list=PL1")
        assertNotNull(playlist)
        assertEquals(listOf("lib1", "lib2"), db.playlistDao().items(playlist!!.id).map { it.itemId })
        assertEquals(0, serviceStarts)
    }

    @Test
    fun `importing the same source twice reuses the playlist`() = runTest {
        val first = summary("a")
        runner().import(first, DownloadMode.AUDIO, first.entries)
        val second = summary("b")

        runner().import(second, DownloadMode.AUDIO, second.entries)

        val playlists = db.playlistDao().playlists()
        assertEquals(1, playlists.size)
        assertEquals(2, db.jobDao().activeJobs().count { it.playlistId == playlists.single().id })
    }

    @Test
    fun `an empty selection creates nothing`() = runTest {
        val outcome = runner().import(summary("a"), DownloadMode.AUDIO, emptyList())

        assertTrue(outcome.nothing)
        assertTrue(db.playlistDao().playlists().isEmpty())
        assertTrue(db.jobDao().activeJobs().isEmpty())
    }

    @Test
    fun `a playlist whose videos are already queued is not left empty behind`() = runTest {
        // Same URL and mode already queued: the new import is all duplicates.
        queue.enqueue("https://www.youtube.com/watch?v=a", DownloadMode.AUDIO)
        val summary = summary("a")

        val outcome = runner().import(summary, DownloadMode.AUDIO, summary.entries)

        assertEquals(1, outcome.skipped)
        assertTrue(outcome.nothing)
        assertNull(db.playlistDao().bySourceUrl("https://www.youtube.com/playlist?list=PL1"))
    }
}
