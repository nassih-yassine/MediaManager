package dev.mediamanager.mobile.ui

import android.net.Uri

/** "YouTube · 4TrZAaFMpts" or the last path segment, never a wall of query params. */
fun jobLabel(url: String): String {
    val uri = runCatching { Uri.parse(url) }.getOrNull()
    val id = uri?.getQueryParameter("v")
        ?: uri?.lastPathSegment?.takeIf { segment ->
            uri.host?.contains("youtu", ignoreCase = true) == true && segment.length in 5..20
        }
    if (id != null && uri?.host?.contains("youtu", ignoreCase = true) == true) {
        return "YouTube · $id"
    }
    return uri?.lastPathSegment?.takeIf { it.isNotBlank() } ?: url.substringAfterLast('/').ifBlank { url }
}
