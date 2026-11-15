package dev.mediamanager.mobile.download

import java.security.MessageDigest

enum class DownloadMode(val dbValue: String) {
    AUDIO("audio"),
    VIDEO("video");

    companion object {
        fun from(value: String): DownloadMode =
            entries.firstOrNull { it.dbValue == value || it.name.equals(value, true) } ?: AUDIO
    }
}

fun dedupeKey(url: String, mode: DownloadMode): String {
    val normalized = url.trim()
    val digest = MessageDigest.getInstance("SHA-256")
        .digest("$normalized|${mode.dbValue}".toByteArray())
    return digest.joinToString("") { "%02x".format(it) }
}
