package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistInspectTest {

    private fun entry(id: Int) = PlaylistEntry(
        url = "https://www.youtube.com/watch?v=v$id",
        title = "Video $id",
        durationMs = 60_000,
    )

    @Test
    fun `summarize keeps source order`() {
        val summary = summarizePlaylist(
            listId = "PL1",
            name = "Mix",
            uploader = "Channel",
            coverUrl = null,
            entries = listOf(entry(3), entry(1), entry(2)),
        )

        assertEquals(listOf("Video 3", "Video 1", "Video 2"), summary.entries.map { it.title })
        assertEquals(3, summary.count)
        assertFalse(summary.truncated)
    }

    @Test
    fun `duplicate urls are dropped`() {
        val summary = summarizePlaylist(
            listId = "PL1",
            name = "Mix",
            uploader = null,
            coverUrl = null,
            entries = listOf(entry(1), entry(1), entry(2)),
        )

        assertEquals(2, summary.count)
    }

    @Test
    fun `the cap marks the summary truncated`() {
        val summary = summarizePlaylist(
            listId = "PL1",
            name = "Huge",
            uploader = null,
            coverUrl = null,
            entries = (1..10).map(::entry),
            total = 500,
            cap = 4,
        )

        assertEquals(4, summary.count)
        assertTrue(summary.truncated)
        assertEquals(500, summary.total)
    }

    @Test
    fun `blank names fall back and a missing total uses the entry count`() {
        val summary = summarizePlaylist(
            listId = "PL1",
            name = "   ",
            uploader = null,
            coverUrl = null,
            entries = listOf(entry(1)),
        )

        assertEquals("Playlist", summary.name)
        assertEquals(1, summary.total)
    }
}
