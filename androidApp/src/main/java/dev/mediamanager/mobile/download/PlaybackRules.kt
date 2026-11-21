package dev.mediamanager.mobile.download

private val progressRegex = Regex("""(\d{1,3}(?:\.\d+)?)%""")

/** Reads yt-dlp's `--newline` progress output. Returns 0..1, or null for non-progress lines. */
fun parseProgress(line: String): Float? {
    if (!line.contains("[download]") && !line.contains("%")) return null
    val match = progressRegex.find(line) ?: return null
    val value = match.groupValues[1].toFloatOrNull() ?: return null
    return (value / 100f).coerceIn(0f, 1f)
}

sealed interface SpaceVerdict {
    data object Fits : SpaceVerdict
    data class TooLarge(val neededBytes: Long, val freeBytes: Long) : SpaceVerdict
}

const val SPACE_HEADROOM_BYTES: Long = 200L * 1024 * 1024

/** An unknown size never blocks the download; the ENOSPC path covers it. */
fun checkSpace(needed: Long?, free: Long, headroom: Long = SPACE_HEADROOM_BYTES): SpaceVerdict {
    if (needed == null || needed <= 0) return SpaceVerdict.Fits
    return if (needed + headroom > free) SpaceVerdict.TooLarge(needed, free) else SpaceVerdict.Fits
}

const val RESUME_MIN_MS: Long = 3_000
const val RESUME_END_GUARD_MS: Long = 5_000

/**
 * Only programming asks to continue. A four-minute clip is quicker to restart than
 * to re-orient in, so anything under ten minutes always begins at zero.
 */
const val RESUME_MIN_DURATION_MS: Long = 10 * 60_000

/** Where playback should start: 0 for a fresh or short item, the saved point otherwise. */
fun resumeTarget(positionMs: Long, durationMs: Long?): Long {
    if (positionMs < RESUME_MIN_MS) return 0
    if (durationMs == null || durationMs < RESUME_MIN_DURATION_MS) return 0
    if (positionMs > durationMs - RESUME_END_GUARD_MS) return 0
    return positionMs
}

/**
 * Where the restored queue should sit. The last played item wins; anything else
 * (nothing saved, item deleted, empty queue) starts at the top.
 */
fun restoreIndex(ids: List<String>, lastItemId: String?): Int {
    if (ids.isEmpty() || lastItemId == null) return 0
    return ids.indexOf(lastItemId).coerceAtLeast(0)
}

/** How much of an item is already behind you, or 0 when a chip would be noise. */
fun watchedFraction(positionMs: Long, durationMs: Long?): Float {
    if (durationMs == null || durationMs <= 0) return 0f
    if (positionMs < RESUME_MIN_MS) return 0f
    val fraction = positionMs.toFloat() / durationMs
    return if (fraction >= 0.995f) 0f else fraction.coerceIn(0f, 1f)
}
