package dev.mediamanager.mobile.ui

/** "YouTube · 4TrZAaFMpts" instead of a wall of query parameters. */
fun jobLabel(url: String): String {
    val marker = "v="
    val start = url.indexOf(marker)
    if (start >= 0) {
        val id = url.substring(start + marker.length)
            .takeWhile { it.isLetterOrDigit() || it == '_' || it == '-' }
        if (id.length >= 5) return "YouTube · $id"
    }
    return url.substringAfterLast('/').ifBlank { url }
}
