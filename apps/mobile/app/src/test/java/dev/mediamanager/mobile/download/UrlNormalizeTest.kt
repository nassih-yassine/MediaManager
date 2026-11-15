package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
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
    fun `other hosts are untouched`() {
        val url = "https://download.samplelib.com/mp3/sample-3s.mp3"
        assertEquals(url, normalizeUrl(" $url "))
    }

    @Test
    fun `a youtube link without a video id is left alone`() {
        assertEquals("https://www.youtube.com/feed/subscriptions", normalizeUrl("https://www.youtube.com/feed/subscriptions"))
    }
}
