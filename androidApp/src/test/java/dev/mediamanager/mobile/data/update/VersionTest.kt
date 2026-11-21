package dev.mediamanager.mobile.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionTest {

    @Test
    fun `release tags parse with or without the android prefix`() {
        assertEquals(listOf(0, 1, 1), AppVersion.parse("android-v0.1.1")?.parts)
        assertEquals(listOf(0, 1, 1), AppVersion.parse("v0.1.1")?.parts)
        assertEquals(listOf(0, 2), AppVersion.parse("0.2")?.parts)
    }

    @Test
    fun `garbage is not a version`() {
        assertNull(AppVersion.parse(null))
        assertNull(AppVersion.parse(""))
        assertNull(AppVersion.parse("latest"))
    }

    @Test
    fun `numeric comparison, not string comparison`() {
        assertTrue(AppVersion.parse("0.1.10")!! > AppVersion.parse("0.1.9")!!)
        assertTrue(AppVersion.parse("0.10.0")!! > AppVersion.parse("0.9.9")!!)
    }

    @Test
    fun `a build made after the tag is not older than the tag`() {
        // VERSION_NAME is "<tag>.<commitCount>", so the local build must not look older.
        assertTrue(AppVersion.parse("0.1.1.1803")!! >= AppVersion.parse("android-v0.1.1")!!)
    }

    @Test
    fun `a newer tag wins`() {
        assertTrue(AppVersion.parse("0.2.0")!! > AppVersion.parse("0.1.1.1803")!!)
    }
}
