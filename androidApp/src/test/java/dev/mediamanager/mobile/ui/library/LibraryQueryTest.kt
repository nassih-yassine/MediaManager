package dev.mediamanager.mobile.ui.library

import dev.mediamanager.mobile.data.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LibraryQueryTest {

    private fun item(
        id: String,
        title: String,
        bytes: Long = 1_000,
        duration: Long? = 60_000,
        created: Long = 1,
        kind: String = "audio",
        available: Boolean = true,
    ) = Item(
        id = id,
        kind = kind,
        title = title,
        sizeBytes = bytes,
        path = "library/audio/aa/$id.mp3",
        thumbPath = null,
        available = available,
        positionMs = 0,
        durationMs = duration,
        mime = "audio/mpeg",
        sourceUrl = null,
        createdAt = created,
    )

    @Test
    fun `filter maps to kinds`() {
        assertNull(kindFor(LibraryFilter.ALL))
        assertEquals(listOf(LibraryFilter.AUDIO, LibraryFilter.VIDEO), LibraryFilter.shown)
        assertEquals("audio", kindFor(LibraryFilter.AUDIO))
        assertEquals("video", kindFor(LibraryFilter.VIDEO))
    }

    @Test
    fun `title sort is case-insensitive`() {
        val sorted = sortItems(
            listOf(item("1", "Banana"), item("2", "apple")),
            LibrarySort.TITLE,
        )
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun `largest sort puts the big file first`() {
        val sorted = sortItems(
            listOf(item("1", "small", bytes = 4_000_000), item("2", "big", bytes = 1_200_000_000)),
            LibrarySort.LARGEST,
        )
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun `duration sort tolerates nulls`() {
        val sorted = sortItems(
            listOf(item("1", "a", duration = null), item("2", "b", duration = 500_000)),
            LibrarySort.DURATION,
        )
        assertEquals(listOf("2", "1"), sorted.map { it.id })
    }

    @Test
    fun `unavailable flag survives`() {
        val sorted = sortItems(listOf(item("1", "a", available = false)), LibrarySort.NEWEST)
        assertEquals(false, sorted.single().available)
    }

    @Test
    fun `oldest is the reverse of newest`() {
        val items = listOf(item("1", "a", created = 1), item("2", "b", created = 3), item("3", "c", created = 2))
        assertEquals(listOf("2", "3", "1"), sortItems(items, LibrarySort.NEWEST).map { it.id })
        assertEquals(listOf("1", "3", "2"), sortItems(items, LibrarySort.OLDEST).map { it.id })
    }
}
