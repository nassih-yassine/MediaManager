package dev.mediamanager.mobile.ui.format

import java.util.Locale

private const val KB = 1024.0
private const val MB = KB * 1024
private const val GB = MB * 1024
private const val TB = GB * 1024

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    return when {
        bytes < KB -> "$bytes B"
        bytes < MB -> "${(bytes / KB).toLong()} KB"
        bytes < 10 * MB -> String.format(Locale.US, "%.1f MB", bytes / MB)
        bytes < GB -> "${(bytes / MB).toLong()} MB"
        bytes < TB -> String.format(Locale.US, "%.2f GB", bytes / GB)
        else -> String.format(Locale.US, "%.2f TB", bytes / TB)
    }
}

fun formatShare(fraction: Float): String = String.format(Locale.US, "%.1f%%", (fraction.coerceIn(0f, 1f) * 100f).toDouble())

fun formatDuration(ms: Long?): String {
    if (ms == null || ms <= 0) return ""
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}
