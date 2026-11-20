package dev.mediamanager.mobile.ui.library

import dev.mediamanager.mobile.data.Item

enum class LibraryFilter(val label: String) {
    ALL("All"),
    AUDIO("Audio"),
    VIDEO("Video"),
    ;

    companion object {
        /** The chips start at Audio: there is no combined view on purpose. */
        val shown: List<LibraryFilter> = entries.filter { it != ALL }

        fun from(value: String): LibraryFilter = shown.firstOrNull { it.name == value } ?: AUDIO
    }
}

enum class LibrarySort(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    TITLE("Title"),
    LARGEST("Largest"),
    DURATION("Duration"),
}

fun kindFor(filter: LibraryFilter): String? = when (filter) {
    LibraryFilter.ALL -> null
    LibraryFilter.AUDIO -> "audio"
    LibraryFilter.VIDEO -> "video"
}

fun sortItems(items: List<Item>, sort: LibrarySort): List<Item> = when (sort) {
    LibrarySort.NEWEST -> items.sortedByDescending { it.createdAt }
    LibrarySort.OLDEST -> items.sortedBy { it.createdAt }
    LibrarySort.TITLE -> items.sortedBy { it.title.lowercase() }
    LibrarySort.LARGEST -> items.sortedByDescending { it.sizeBytes }
    LibrarySort.DURATION -> items.sortedByDescending { it.durationMs ?: -1 }
}
