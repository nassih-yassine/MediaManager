package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Resume is a long-form affordance: a clip that is a few minutes long is faster to
 * restart than to re-orient in, so only real programmes ask.
 */
class ResumeRulesTest {

    private val minute = 60_000L

    @Test
    fun `short media always restarts from zero`() {
        assertEquals(0, resumeTarget(2 * minute, 4 * minute))
        assertEquals(0, resumeTarget(9 * minute, 9 * minute + 30_000))
    }

    @Test
    fun `long media resumes at the saved frame`() {
        assertEquals(6 * minute, resumeTarget(6 * minute, 28 * minute))
    }

    @Test
    fun `the threshold itself counts as long`() {
        assertEquals(
            5 * minute,
            resumeTarget(5 * minute, RESUME_MIN_DURATION_MS + minute),
        )
    }

    @Test
    fun `an item watched to the end restarts`() {
        assertEquals(0, resumeTarget(28 * minute, 28 * minute))
    }

    @Test
    fun `unknown duration never prompts`() {
        assertEquals(0, resumeTarget(6 * minute, null))
        assertEquals(0, resumeTarget(6 * minute, 0))
    }
}
