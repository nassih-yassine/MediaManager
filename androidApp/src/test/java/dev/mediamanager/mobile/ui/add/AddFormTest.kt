package dev.mediamanager.mobile.ui.add

import dev.mediamanager.mobile.download.DownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddFormTest {

    @Test
    fun `blank url is invalid`() {
        assertEquals("Paste a link first", AddFormState(url = "   ").validate().error)
    }

    @Test
    fun `non-http url is rejected`() {
        assertEquals("That is not a link", AddFormState(url = "youtube.com/x").validate().error)
    }

    @Test
    fun `http and https are accepted`() {
        assertNull(AddFormState(url = "https://youtu.be/x").validate().error)
        assertNull(AddFormState(url = "http://example.com/a.mp3").validate().error)
    }

    @Test
    fun `url is trimmed on validate`() {
        assertEquals("https://youtu.be/x", AddFormState(url = "  https://youtu.be/x ").validate().url)
    }

    @Test
    fun `mode defaults to audio`() {
        assertEquals(DownloadMode.AUDIO, AddFormState().mode)
    }
}
