package dev.mediamanager.mobile.ui.adaptive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WindowInfoTest {

    @Test
    fun `a landscape phone is wide but not a tablet`() {
        val window = WindowInfo(widthDp = 891, heightDp = 411)

        assertEquals(WidthClass.EXPANDED, window.widthClass)
        assertEquals(HeightClass.SHORT, window.heightClass)
        assertTrue(window.useRail)
        assertFalse("411 dp of height cannot hold two panes", window.isTwoPane)
    }

    @Test
    fun `a portrait phone keeps the bottom bar`() {
        val window = WindowInfo(widthDp = 411, heightDp = 891)

        assertEquals(WidthClass.COMPACT, window.widthClass)
        assertFalse(window.useRail)
        assertFalse(window.isTwoPane)
    }

    @Test
    fun `a tablet gets the rail and two panes`() {
        val window = WindowInfo(widthDp = 900, heightDp = 700)

        assertEquals(WidthClass.EXPANDED, window.widthClass)
        assertTrue(window.isTwoPane)
    }

    @Test
    fun `width class boundaries`() {
        assertEquals(WidthClass.COMPACT, WindowInfo(599, 800).widthClass)
        assertEquals(WidthClass.MEDIUM, WindowInfo(600, 800).widthClass)
        assertEquals(WidthClass.MEDIUM, WindowInfo(839, 800).widthClass)
        assertEquals(WidthClass.EXPANDED, WindowInfo(840, 800).widthClass)
    }

    @Test
    fun `grid columns never go below two`() {
        assertEquals(2, WindowInfo(360, 800).gridColumns(minCellDp = 168))
        assertEquals(5, WindowInfo(891, 411).gridColumns(minCellDp = 168))
    }
}
