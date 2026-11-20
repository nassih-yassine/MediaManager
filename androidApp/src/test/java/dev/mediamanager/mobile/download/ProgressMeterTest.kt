package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression for the "1425%" progress readout: four ranged connections each report
 * cumulative bytes, and summing those raw values overshot the file length badly.
 */
class ProgressMeterTest {

    @Test
    fun `per part increments sum to one hundred percent`() {
        val length = 4_000_000L
        val meter = ProgressMeter(length)
        var last = 0f
        // Four parts of 1 MB, each reporting its cumulative position in 250 KB steps.
        repeat(4) {
            for (read in 250_000L..1_000_000L step 250_000L) {
                last = meter.add(250_000L)
                assertTrue("progress must never exceed 1.0, was $last", last <= 1f)
            }
        }
        assertEquals(1f, last, 0.0001f)
    }

    @Test
    fun `a part that exceeds the announced length cannot push past one hundred percent`() {
        val meter = ProgressMeter(1_000L)
        assertEquals(0.5f, meter.add(500L), 0.0001f)
        assertEquals(1f, meter.add(900L), 0.0001f)
        assertEquals(1f, meter.add(900L), 0.0001f)
    }

    @Test
    fun `progress never goes backwards`() {
        val meter = ProgressMeter(1_000L)
        val first = meter.add(400L)
        val second = meter.add(0L)
        assertEquals(first, second, 0.0001f)
    }
}
