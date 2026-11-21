package dev.mediamanager.mobile.data.update

import org.json.JSONArray

/**
 * Reads the GitHub `/releases` payload. Drafts and prereleases are skipped: the API
 * only returns them to authenticated owners, and a user must never be offered a build
 * that was not published on purpose.
 */
object ReleaseParser {

    private const val TAG_PREFIX = "android-v"

    fun parse(json: String): UpdateInfo? = runCatching {
        val releases = JSONArray(json)
        (0 until releases.length())
            .asSequence()
            .map { releases.getJSONObject(it) }
            .filterNot { it.optBoolean("draft") || it.optBoolean("prerelease") }
            .mapNotNull { release ->
                val tag = release.optString("tag_name")
                if (!tag.startsWith(TAG_PREFIX)) return@mapNotNull null
                val version = AppVersion.parse(tag) ?: return@mapNotNull null
                version to release
            }
            .maxByOrNull { it.first }
            ?.let { (_, release) -> toInfo(release) }
    }.getOrNull()

    private fun toInfo(release: org.json.JSONObject): UpdateInfo {
        val tag = release.optString("tag_name")
        val assets = release.optJSONArray("assets") ?: JSONArray()
        val apk = (0 until assets.length())
            .map { assets.getJSONObject(it) }
            .firstOrNull { asset ->
                val name = asset.optString("name")
                name.endsWith(".apk", ignoreCase = true) && name.contains("arm64", ignoreCase = true)
            }
            ?.optString("browser_download_url")
            ?.takeIf { it.isNotBlank() }

        return UpdateInfo(
            versionTag = tag,
            versionName = AppVersion.parse(tag)?.display() ?: tag,
            notes = release.optString("body").trim(),
            apkUrl = apk,
            releaseUrl = release.optString("html_url"),
            publishedAt = release.optString("published_at").takeIf { it.isNotBlank() },
        )
    }
}
