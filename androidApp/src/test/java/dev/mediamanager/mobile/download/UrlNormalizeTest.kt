package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class UrlNormalizeTest {

    @Test
    fun `radio and playlist parameters are dropped`() {
        assertEquals(
            "https://www.youtube.com/watch?v=4TrZAaFMpts",
            normalizeUrl("https://www.youtube.com/watch?v=4TrZAaFMpts&list=RD4TrZAaFMpts&start_radio=1"),
        )
        assertEquals(
            "https://www.youtube.com/watch?v=abc123",
            normalizeUrl("https://m.youtube.com/watch?list=PL1&v=abc123&index=4"),
        )
    }

    @Test
    fun `short and shorts links become canonical watch links`() {
        assertEquals("https://www.youtube.com/watch?v=xyz", normalizeUrl("https://youtu.be/xyz?t=10"))
        assertEquals("https://www.youtube.com/watch?v=sh1", normalizeUrl("https://www.youtube.com/shorts/sh1"))
    }

    @Test
    fun `radio playlist seeds become the seed video`() {
        assertEquals(
            "https://www.youtube.com/watch?v=4TrZAaFMpts",
            normalizeUrl("https://youtube.com/playlist?list=RD4TrZAaFMpts&playnext=1&start_radio=1"),
        )
    }

    @Test
    fun `a real playlist link exposes its list id`() {
        assertEquals(
            "PLabc123456",
            playlistListId("https://www.youtube.com/playlist?list=PLabc123456"),
        )
        assertEquals(
            "OLAK5uy_abc123",
            playlistListId("https://music.youtube.com/playlist?list=OLAK5uy_abc123"),
        )
        assertEquals(
            "UUabc123456",
            playlistListId("https://www.youtube.com/playlist?list=UUabc123456"),
        )
    }

    @Test
    fun `a seeded video stays a single video`() {
        assertNull(playlistListId("https://www.youtube.com/watch?v=4TrZAaFMpts&list=PLabc123456"))
        assertNull(playlistListId("https://youtu.be/4TrZAaFMpts"))
    }

    @Test
    fun `radio, liked and watch later lists are not importable`() {
        assertNull(playlistListId("https://www.youtube.com/playlist?list=RD4TrZAaFMpts"))
        assertNull(playlistListId("https://www.youtube.com/playlist?list=LLabc123456"))
        assertNull(playlistListId("https://www.youtube.com/playlist?list=WL"))
        assertNull(playlistListId("https://vimeo.com/playlist?list=PLabc123456"))
    }

    @Test
    fun `youtube hosts route to the newpipe extractor`() {
        assertTrue(isYouTubeUrl("https://youtu.be/xyz"))
        assertTrue(isYouTubeUrl("https://m.youtube.com/watch?v=abc"))
        assertFalse(isYouTubeUrl("https://vimeo.com/1"))
        assertFalse(isYouTubeUrl("not a url"))
    }

    @Test
    fun `other hosts are untouched`() {
        val url = "https://download.samplelib.com/mp3/sample-3s.mp3"
        assertEquals(url, normalizeUrl(" $url "))
    }

    @Test
    fun `a youtube link without a video id is left alone`() {
        assertEquals("https://www.youtube.com/feed/subscriptions", normalizeUrl("https://www.youtube.com/feed/subscriptions"))
    }
}
