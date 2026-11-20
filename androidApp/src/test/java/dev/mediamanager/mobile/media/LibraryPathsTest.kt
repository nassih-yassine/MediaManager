package dev.mediamanager.mobile.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPathsTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun paths() = LibraryPaths(tmp.newFolder("mm"))

    @Test
    fun `items are sharded by id prefix`() {
        val p = paths()
        assertEquals("library/audio/ab/ab12cd34.mp3", p.itemRelativePath("audio", "ab12cd34", "mp3"))
        assertTrue(p.itemFile("audio", "ab12cd34", "mp3").path.endsWith("library/audio/ab/ab12cd34.mp3"))
    }

    @Test
    fun `thumbs use the same shard`() {
        assertTrue(paths().thumbFile("ab12").path.endsWith("thumbs/ab/ab12.jpg"))
    }

    @Test
    fun `resolve rejects traversal and absolute paths`() {
        val p = paths()
        assertNull(p.resolve("../../etc/passwd"))
        assertNull(p.resolve("/etc/passwd"))
        assertNull(p.resolve("audiox/../../../x.mp3"))
        assertNull(p.resolve(""))
        assertNotNull(p.resolve("library/audio/ab/ab12.mp3"))
    }

    @Test
    fun `resolve does not accept a sibling directory with a shared prefix`() {
        val root = tmp.newFolder("x")
        val sibling = tmp.newFolder("xy")
        sibling.resolve("a.mp3").writeText("x")
        val p = LibraryPaths(root)
        assertNull(p.resolve("../xy/a.mp3"))
    }

    @Test
    fun `incoming dir is per job`() {
        assertTrue(paths().incomingDir("job-1").path.endsWith(".incoming/job-1"))
    }

    @Test
    fun `rejects short ids and odd extensions`() {
        val p = paths()
        assertFails { p.itemRelativePath("audio", "a", "mp3") }
        assertFails { p.itemRelativePath("audio", "ab12", "mp3/../x") }
        assertFails { p.itemRelativePath("Audio", "ab12", "mp3") }
    }

    @Test
    fun `deleteRecursively removes the tree and reports missing dirs`() {
        val p = paths()
        val dir = p.incomingDir("j1")
        dir.mkdirs()
        dir.resolve("part.mp4").writeText("abc")
        assertTrue(p.deleteRecursively(dir))
        assertFalse(dir.exists())
        assertFalse(p.deleteRecursively(dir))
    }

    private fun assertFails(block: () -> Unit) {
        try {
            block()
            throw AssertionError("expected IllegalArgumentException")
        } catch (_: IllegalArgumentException) {
        }
    }
}
