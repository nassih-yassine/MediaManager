package dev.mediamanager.mobile.download

import android.net.Uri

/**
 * YouTube links arrive with playlist and radio parameters (`list=RD…`,
 * `start_radio=1`, `index=…`). `--no-playlist` does not always win against a radio
 * URL, so the app rewrites the link to the single video before it reaches yt-dlp.
 * Everything else is left alone.
 */
fun isYouTubeUrl(raw: String): Boolean {
    val host = runCatching { Uri.parse(raw.trim()).host?.lowercase() }.getOrNull() ?: return false
    return host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
}

fun normalizeUrl(raw: String): String {
    val trimmed = raw.trim()
    val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
    val host = uri.host?.lowercase() ?: return trimmed
    val isYouTube = host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com")
    if (!isYouTube) return trimmed

    val list = uri.getQueryParameter("list")
    val id = when {
        host == "youtu.be" -> uri.pathSegments.firstOrNull()
        uri.getQueryParameter("v") != null -> uri.getQueryParameter("v")
        uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live", "v") ->
            uri.pathSegments.getOrNull(1)
        // A radio playlist is seeded by a video: list=RD<videoId>.
        list != null && list.startsWith("RD") ->
            list.removePrefix("RD").takeIf { it.matches(Regex("[A-Za-z0-9_-]{11}")) }
        else -> null
    }
    return if (id.isNullOrBlank()) trimmed else "https://www.youtube.com/watch?v=$id"
}

/**
 * A real playlist (PL…, UU…, or a /playlist URL with no seed video) cannot be
 * turned into one download. Reject it at the form instead of letting yt-dlp walk it.
 */
fun playlistRejection(raw: String): String? {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null
    val hasVideo = !uri.getQueryParameter("v").isNullOrBlank() ||
        uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live")
    val isPlaylistPath = uri.pathSegments.firstOrNull() == "playlist"
    val list = uri.getQueryParameter("list")
    if (isPlaylistPath && !hasVideo && list != null && !list.startsWith("RD")) {
        return "That is a playlist. Paste a video link instead."
    }
    return null
}
