package dev.mediamanager.mobile.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class YtDlpArgsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val dir: File by lazy { tmp.newFolder("job1") }

    @Test
    fun `audio extracts mp3 with cover and info json`() {
        val args = YtDlpArgs.audio("https://youtu.be/x", dir)
        assertTrue(args.containsAll(listOf("-x", "--audio-format", "mp3", "--audio-quality", "0")))
        assertTrue(args.containsAll(listOf("--write-info-json", "--write-thumbnail", "--convert-thumbnails", "jpg")))
        assertTrue(args.containsAll(listOf("--no-playlist", "--playlist-items", "1", "--js-runtimes", "quickjs")))
        assertTrue(args.containsAll(listOf("-o", File(dir, "%(id)s.%(ext)s").path)))
        assertEquals("https://youtu.be/x", args.last())
    }

    @Test
    fun `video caps height and merges to mp4`() {
        val args = YtDlpArgs.video("https://youtu.be/x", dir, maxHeight = 1080)
        assertTrue(args.containsAll(listOf("-f", "bv*[height<=1080]+ba/b[height<=1080]")))
        assertTrue(args.containsAll(listOf("--merge-output-format", "mp4")))
        assertFalse(args.contains("-x"))
        assertEquals("https://youtu.be/x", args.last())
    }

    @Test
    fun `no js runtime flags when the runtime is unknown`() {
        val args = YtDlpArgs.audio("u", dir, jsRuntime = null)
        assertFalse(args.contains("--js-runtimes"))
        assertFalse(args.contains("quickjs"))
    }
}
