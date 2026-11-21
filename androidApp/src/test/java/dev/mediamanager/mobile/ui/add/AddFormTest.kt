package dev.mediamanager.mobile.ui.add

import dev.mediamanager.mobile.download.DownloadMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `a playlist link is accepted and exposes its list id`() {
        val form = AddFormState(url = "https://www.youtube.com/playlist?list=PLabc123456").validate()

        assertNull(form.error)
        assertEquals("PLabc123456", form.sourcePlaylistId)
    }

    @Test
    fun `a seeded video is not treated as a playlist`() {
        val form = AddFormState(url = "https://www.youtube.com/watch?v=4TrZAaFMpts&list=PLabc123456").validate()

        assertNull(form.error)
        assertNull(form.sourcePlaylistId)
    }
}
