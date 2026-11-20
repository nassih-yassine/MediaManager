package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchedFractionTest {

    @Test
    fun `a fresh item has no progress to draw`() {
        assertEquals(0f, watchedFraction(0, 600_000), 0.0001f)
        assertEquals(0f, watchedFraction(2_000, 600_000), 0.0001f)
    }

    @Test
    fun `a part watched item reports its fraction`() {
        assertEquals(0.5f, watchedFraction(300_000, 600_000), 0.0001f)
    }

    @Test
    fun `a finished item hides the bar`() {
        assertEquals(0f, watchedFraction(599_000, 600_000), 0.0001f)
    }

    @Test
    fun `unknown duration never draws`() {
        assertEquals(0f, watchedFraction(120_000, null), 0.0001f)
        assertEquals(0f, watchedFraction(120_000, 0), 0.0001f)
    }

    @Test
    fun `a position past the duration stays inside the bar`() {
        val fraction = watchedFraction(900_000, 600_000)
        assertTrue(fraction in 0f..1f)
    }
}
