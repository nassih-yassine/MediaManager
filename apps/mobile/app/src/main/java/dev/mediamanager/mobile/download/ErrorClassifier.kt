package dev.mediamanager.mobile.download

sealed interface Failure {
    data class Permanent(val message: String) : Failure
    data class Retryable(val message: String) : Failure
}

private val permanentMarkers = listOf(
    "no space left on device",
    "video unavailable",
    "private video",
    "members-only",
    "member-only",
    "sign in to confirm",
    "unsupported url",
    "http error 404",
    "http error 403",
    "http error 410",
    "this video is not available",
    "removed by the uploader",
    "age-restricted",
    "account has been terminated",
)

private val retryableMarkers = listOf(
    "timed out",
    "timeout",
    "temporary failure",
    "connection reset",
    "connection refused",
    "unable to resolve host",
    "unable to download webpage",
    "http error 429",
    "http error 500",
    "http error 502",
    "http error 503",
    "http error 504",
)

private val errorLine = Regex("""ERROR:\s*(.+)""")

/**
 * Maps yt-dlp output to a decision. Anything unrecognised retries, because a transient
 * platform bug should not throw away a job; the retry budget stops the loop.
 */
fun classify(output: String): Failure {
    val text = output.lowercase()
    val message = errorLine.find(output)?.groupValues?.get(1)?.trim()
        ?: output.lineSequence().lastOrNull { it.isNotBlank() }?.trim()
        ?: "Download failed"

    permanentMarkers.firstOrNull { text.contains(it) }?.let {
        return Failure.Permanent(readable(message))
    }
    retryableMarkers.firstOrNull { text.contains(it) }?.let {
        return Failure.Retryable(readable(message))
    }
    return Failure.Retryable(readable(message))
}

/**
 * Connectivity trouble is not the job's fault: the attempt is given back and the
 * job retries as soon as the network is usable again.
 */
fun isNetworkTrouble(message: String): Boolean {
    val text = message.lowercase()
    return listOf(
        "timed out",
        "timeout",
        "connection reset",
        "connection refused",
        "connection abort",
        "broken pipe",
        "unexpected end of stream",
        "network is unreachable",
        "unable to resolve host",
        "temporary failure",
        "unable to download webpage",
        "no address associated",
        "stream was reset",
        "software caused connection abort",
        "eof",
    ).any { text.contains(it) }
}

private fun readable(message: String): String = message
    .removePrefix("ERROR:")
    .trim()
    .ifBlank { "Download failed" }
