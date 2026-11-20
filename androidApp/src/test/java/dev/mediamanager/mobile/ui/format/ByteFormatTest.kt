package dev.mediamanager.mobile.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

class ByteFormatTest {

    @Test
    fun `bytes under a megabyte stay in KB`() {
        assertEquals("412 KB", formatBytes(421_888))
    }

    @Test
    fun `megabytes below ten keep one decimal`() {
        assertEquals("4.2 MB", formatBytes(4_404_019))
    }

    @Test
    fun `megabytes above ten are whole`() {
        assertEquals("412 MB", formatBytes(432_013_312))
    }

    @Test
    fun `gigabytes keep two decimals`() {
        assertEquals("4.21 GB", formatBytes(4_520_710_144))
    }

    @Test
    fun `zero is zero`() {
        assertEquals("0 B", formatBytes(0))
    }

    @Test
    fun `duration hides zero and formats hours`() {
        assertEquals("", formatDuration(0))
        assertEquals("3:42", formatDuration(222_000))
        assertEquals("1:02:03", formatDuration(3_723_000))
    }

    @Test
    fun `share is one decimal percent and clamps`() {
        assertEquals("28.3%", formatShare(0.2834f))
        assertEquals("100.0%", formatShare(4f))
        assertEquals("0.0%", formatShare(-1f))
    }
}
