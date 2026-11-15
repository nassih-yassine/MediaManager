package dev.mediamanager.mobile.ui.add

import dev.mediamanager.mobile.download.DownloadMode
import dev.mediamanager.mobile.download.playlistRejection

data class AddFormState(
    val url: String = "",
    val mode: DownloadMode = DownloadMode.AUDIO,
    val playlistId: String? = null,
    val error: String? = null,
    val submitting: Boolean = false,
) {
    fun validate(): AddFormState {
        val trimmed = url.trim()
        val problem = when {
            trimmed.isEmpty() -> "Paste a link first"
            !(trimmed.startsWith("http://") || trimmed.startsWith("https://")) -> "That is not a link"
            playlistRejection(trimmed) != null -> playlistRejection(trimmed)
            else -> null
        }
        return copy(error = problem, url = trimmed)
    }
}

sealed interface SubmitResult {
    data class Queued(val jobId: String) : SubmitResult
    data class Duplicate(val jobId: String) : SubmitResult
    data class Invalid(val message: String) : SubmitResult
}
