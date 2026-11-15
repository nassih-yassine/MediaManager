package dev.mediamanager.mobile.download

import android.net.Uri

/**
 * YouTube links arrive with playlist and radio parameters (`list=RD…`,
 * `start_radio=1`, `index=…`). `--no-playlist` does not always win against a radio
 * URL, so the app rewrites the link to the single video before it reaches yt-dlp.
 * Everything else is left alone.
 */
fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val host = uri.host?.lowercase() ?: return trimmed
    val isYouTube = host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    if (!isYouTube) return trimmed

    val id = when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
        uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live", "v") ->
            uri.pathSegments.getOrNull(1)
        else -> null
    }
    return if (id.isNullOrBlank()) trimmed else "https://www.youtube.com/watch?v=$id"
}
