package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `flat yt-dlp json maps webpage urls, ids and durations`() {
        val json = """
            {
              "title": "Popular Music Videos",
              "uploader": "YouTube Music",
              "playlist_count": 120,
              "thumbnails": [{"url": "https://i.ytimg.com/vi/a/hq.jpg"}],
              "entries": [
                {"id": "a", "title": "One", "duration": 210.5, "webpage_url": "https://www.youtube.com/watch?v=a"},
                {"id": "b", "title": "Two", "duration": 60}
              ]
            }
        """.trimIndent()

        val summary = parseFlatPlaylist(json, listId = "PL1")!!

        assertEquals("Popular Music Videos", summary.name)
        assertEquals("YouTube Music", summary.uploader)
        assertEquals(2, summary.count)
        assertEquals("https://www.youtube.com/watch?v=a", summary.entries[0].url)
        assertEquals("https://www.youtube.com/watch?v=b", summary.entries[1].url)
        assertEquals(210_500L, summary.entries[0].durationMs)
        assertEquals(120, summary.total)
        assertEquals("https://i.ytimg.com/vi/a/hq.jpg", summary.coverUrl)
    }

    @Test
    fun `flat json caps a huge playlist`() {
        val entries = (1..10).joinToString(",") { """{"id":"v$it","title":"V$it"}""" }
        val json = """{"title":"Big","entries":[$entries]}"""

        val summary = parseFlatPlaylist(json, listId = "PL1", cap = 3)!!

        assertEquals(3, summary.count)
        assertTrue(summary.truncated)
    }

    @Test
    fun `flat json without entries or with garbage is null`() {
        assertNull(parseFlatPlaylist("{}", "PL1"))
        assertNull(parseFlatPlaylist("not json", "PL1"))
        assertNull(parseFlatPlaylist("""{"title":"Empty","entries":[]}""", "PL1"))
    }

    @Test
    fun `the listing cache expires and evicts the oldest`() {
        var now = 1_000L
        val cache = PlaylistCache(ttlMs = 500, clock = { now }, maxEntries = 2)
        val a = summarizePlaylist("A", "A", null, null, listOf(entry(1)))
        val b = summarizePlaylist("B", "B", null, null, listOf(entry(2)))
        val c = summarizePlaylist("C", "C", null, null, listOf(entry(3)))

        cache.put("a", a)
        assertTrue(cache.get("a") === a)
        now += 501
        assertNull(cache.get("a"))

        now = 2_000L
        cache.put("a", a)
        cache.put("b", b)
        cache.get("a")
        cache.put("c", c)
        assertNull("least recently used entry is evicted", cache.get("b"))
        assertTrue(cache.get("a") === a)
    }
}
