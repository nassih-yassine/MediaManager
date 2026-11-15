package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRulesTest {

    @Test
    fun `dedupe key ignores surrounding whitespace`() {
        assertEquals(
            dedupeKey("https://youtu.be/x", DownloadMode.AUDIO),
            dedupeKey(" https://youtu.be/x ", DownloadMode.AUDIO),
        )
    }

    @Test
    fun `dedupe key separates modes`() {
        assertNotEquals(
            dedupeKey("https://youtu.be/x", DownloadMode.AUDIO),
            dedupeKey("https://youtu.be/x", DownloadMode.VIDEO),
        )
    }

    @Test
    fun `unavailable and private are permanent`() {
        assertTrue(classify("ERROR: [youtube] abc: Video unavailable") is Failure.Permanent)
        assertTrue(classify("ERROR: Private video. Sign in if you've been granted access") is Failure.Permanent)
        assertTrue(classify("ERROR: Unsupported URL: https://example.com/x") is Failure.Permanent)
        assertTrue(classify("ERROR: HTTP Error 404: Not Found") is Failure.Permanent)
        assertTrue(classify("ERROR: HTTP Error 403: Forbidden") is Failure.Permanent)
    }

    @Test
    fun `network trouble is retryable`() {
        assertTrue(classify("ERROR: Unable to download webpage: <urlopen error timed out>") is Failure.Retryable)
        assertTrue(classify("ERROR: HTTP Error 503: Service Unavailable") is Failure.Retryable)
        assertTrue(classify("ERROR: Read timed out") is Failure.Retryable)
        assertTrue(classify("ERROR: something nobody has seen before") is Failure.Retryable)
    }

    @Test
    fun `no space left on device is permanent`() {
        assertTrue(classify("OSError: [Errno 28] No space left on device") is Failure.Permanent)
    }

    @Test
    fun `classified message drops the ERROR prefix`() {
        val f = classify("ERROR: Video unavailable")
        assertEquals("Video unavailable", (f as Failure.Permanent).message)
    }

    @Test
    fun `progress template parses percent`() {
        assertEquals(0.423f, parseProgress("[download]  42.3% of 10.00MiB at 1.2MiB/s ETA 00:05")!!, 0.001f)
        assertEquals(1f, parseProgress("[download] 100% of 10.00MiB")!!, 0.001f)
        assertNull(parseProgress("[download] Destination: /x/a.mp4"))
        assertNull(parseProgress("random stderr noise"))
    }

    @Test
    fun `space check keeps the headroom`() {
        assertTrue(checkSpace(needed = 1_000, free = 1_000_000_000) is SpaceVerdict.Fits)
        assertTrue(checkSpace(needed = 5_000_000_000, free = 4_000_000_000) is SpaceVerdict.TooLarge)
        assertTrue(checkSpace(needed = null, free = 1_000) is SpaceVerdict.Fits)
        assertTrue(checkSpace(needed = 900_000_000, free = 1_000_000_000) is SpaceVerdict.TooLarge)
        assertTrue(checkSpace(needed = 900_000_000, free = 1_200_000_000) is SpaceVerdict.Fits)
    }

    @Test
    fun `resume seeks only past three seconds and away from the end`() {
        assertEquals(0L, resumeTarget(1_000, 100_000))
        assertEquals(30_000L, resumeTarget(30_000, 100_000))
        assertEquals(0L, resumeTarget(98_000, 100_000))
        assertEquals(30_000L, resumeTarget(30_000, null))
        assertEquals(0L, resumeTarget(0, null))
    }

    @Test
    fun `mode mapping round-trips db values`() {
        assertEquals(DownloadMode.VIDEO, DownloadMode.from("video"))
        assertEquals(DownloadMode.AUDIO, DownloadMode.from("AUDIO"))
        assertTrue(dedupeKey("u", DownloadMode.AUDIO).length == 64)
    }
}
