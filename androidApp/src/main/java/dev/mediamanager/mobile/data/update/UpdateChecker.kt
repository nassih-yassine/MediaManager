package dev.mediamanager.mobile.data.update

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Asks GitHub for the newest published release and compares it with the running
 * build. Every failure is soft: an update check must never be visible unless it has
 * something useful to say.
 */
class UpdateChecker(
    private val currentVersion: String,
    private val endpoint: String = RELEASES_URL,
    private val fetch: suspend (String) -> String = { url -> fetchReleases(url, currentVersion) },
) {

    sealed interface Result {
        data class UpToDate(val current: AppVersion) : Result
        data class Available(val info: UpdateInfo, val version: AppVersion) : Result
        data class Failed(val reason: Reason) : Result
    }

    enum class Reason { OFFLINE, RATE_LIMITED, ERROR }

    suspend fun check(): Result {
        val body = try {
            // HttpURLConnection is blocking; callers may be on the main thread.
            withContext(Dispatchers.IO) { fetch(endpoint) }
        } catch (limited: RateLimited) {
            android.util.Log.w("UpdateChecker", "rate limited")
            return Result.Failed(Reason.RATE_LIMITED)
        } catch (offline: IOException) {
            android.util.Log.w("UpdateChecker", "offline: ${offline.message}")
            return Result.Failed(Reason.OFFLINE)
        } catch (error: Exception) {
            android.util.Log.w("UpdateChecker", "failed", error)
            return Result.Failed(Reason.ERROR)
        }

        val info = ReleaseParser.parse(body) ?: return Result.Failed(Reason.ERROR)
        val remote = AppVersion.parse(info.versionTag) ?: return Result.Failed(Reason.ERROR)
        val local = AppVersion.parse(currentVersion) ?: return Result.Failed(Reason.ERROR)
        return if (remote > local) Result.Available(info, remote) else Result.UpToDate(local)
    }

    companion object {
        const val RELEASES_URL =
            "https://api.github.com/repos/nassih-yassine/MediaManager/releases?per_page=30"
    }
}

private class RateLimited : IOException()

private fun fetchReleases(url: String, versionName: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 10_000
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "MediaManager/$versionName")
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
    }
    return try {
        when (val code = connection.responseCode) {
            in 200..299 -> connection.inputStream.bufferedReader().use { it.readText() }
            403, 429 -> throw RateLimited()
            else -> throw IOException("HTTP $code")
        }
    } finally {
        connection.disconnect()
    }
}
