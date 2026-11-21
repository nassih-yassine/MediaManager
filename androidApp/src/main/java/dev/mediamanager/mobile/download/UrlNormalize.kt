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
 * The list id of a real YouTube playlist, or null when the link is a single video.
 *
 * `watch?v=…&list=…` deliberately returns null in v1: the seed video wins, and the
 * whole-playlist affordance can come later. Radio (`RD…`), liked (`LL…`) and Watch
 * Later (`WL…`) are not importable without an account, so they are not playlists here.
 */
fun playlistListId(raw: String): String? {
    val uri = runCatching { Uri.parse(raw.trim()) }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host != "youtube.com" && !host.endsWith(".youtube.com")) return null
    if (!uri.getQueryParameter("v").isNullOrBlank()) return null
    if (uri.pathSegments.firstOrNull() in setOf("shorts", "embed", "live", "watch")) return null
    val list = uri.getQueryParameter("list") ?: return null
    val importable = list.startsWith("PL") ||
        list.startsWith("UU") ||
        list.startsWith("OLAK5uy_") ||
        list.startsWith("FL")
    return list.takeIf { importable && it.matches(Regex("[A-Za-z0-9_-]{10,}")) }
}
