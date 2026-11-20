package dev.mediamanager.mobile.download

import android.app.Application
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class)
class MediaInfoTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = """
        {"id":"dQw4w9WgXcQ","title":"A long mix","duration":222.5,"ext":"mp3",
         "width":1920,"height":1080,"filesize_approx":410000000}
    """.trimIndent()

    @Test
    fun `parses title, duration, dimensions and estimate`() {
        val info = parseInfo(json)!!
        assertEquals("A long mix", info.title)
        assertEquals(222_500L, info.durationMs)
        assertEquals("mp3", info.ext)
        assertEquals(1920, info.width)
        assertEquals(1080, info.height)
        assertEquals(410_000_000L, info.approxBytes)
    }

    @Test
    fun `falls back from filesize_approx to filesize`() {
        val info = parseInfo("""{"title":"x","ext":"mp4","filesize":1234}""")!!
        assertEquals(1234L, info.approxBytes)
        assertNull(info.durationMs)
        assertNull(info.height)
    }

    @Test
    fun `returns null for junk`() {
        assertNull(parseInfo("not json"))
    }

    @Test
    fun `picks the media file and ignores sidecars`() {
        val dir = tmp.newFolder("job")
        dir.resolve("abc.info.json").writeText("{}")
        dir.resolve("abc.jpg").writeText("jpg")
        dir.resolve("abc.mp3").writeText("audio")
        assertEquals("abc.mp3", findMediaFile(dir, "mp3")!!.name)
        assertEquals("abc.jpg", findThumbFile(dir)!!.name)
        assertTrue(findThumbFile(tmp.newFolder("empty")) == null)
    }
}
