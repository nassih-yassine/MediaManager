package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Test

/** The relaunched app used to always park on queue[0], losing the last thing played. */
class RestoreIndexTest {

    @Test
    fun `restores the item that was last playing`() {
        val ids = listOf("a", "b", "c")
        assertEquals(1, restoreIndex(ids, "b"))
    }

    @Test
    fun `falls back to the first item when nothing was saved`() {
        assertEquals(0, restoreIndex(listOf("a", "b"), null))
    }

    @Test
    fun `falls back when the saved item left the queue`() {
        assertEquals(0, restoreIndex(listOf("a", "b"), "gone"))
    }

    @Test
    fun `an empty queue still yields zero`() {
        assertEquals(0, restoreIndex(emptyList(), "a"))
    }
}
