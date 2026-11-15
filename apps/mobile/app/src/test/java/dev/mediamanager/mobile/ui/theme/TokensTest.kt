package dev.mediamanager.mobile.ui.theme

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TokensTest {

    @Test
    fun `slate dark accent matches the web tokens`() {
        assertEquals(0xFF9AE6B4.toInt(), colorsFor(ThemeFamily.SLATE, true).accent.toArgb())
    }

    @Test
    fun `slate light accent matches the web tokens`() {
        assertEquals(0xFF0F7A4A.toInt(), colorsFor(ThemeFamily.SLATE, false).accent.toArgb())
    }

    @Test
    fun `every family has a distinct dark canvas`() {
        val canvases = ThemeFamily.entries.map { colorsFor(it, true).canvas.toArgb() }.toSet()
        assertEquals(4, canvases.size)
    }

    @Test
    fun `ink contrasts with canvas in all eight combinations`() {
        for (family in ThemeFamily.entries) {
            for (dark in listOf(true, false)) {
                val colors = colorsFor(family, dark)
                val delta = kotlin.math.abs(colors.ink.luminance() - colors.canvas.luminance())
                assertTrue("$family dark=$dark has low contrast: $delta", delta > 0.3f)
            }
        }
    }

    @Test
    fun `unknown keys fall back to slate and system`() {
        assertEquals(ThemeFamily.SLATE, ThemeFamily.fromKey("nope"))
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromKey(null))
        assertEquals(ThemeFamily.MOSS, ThemeFamily.fromKey("moss"))
    }
}
