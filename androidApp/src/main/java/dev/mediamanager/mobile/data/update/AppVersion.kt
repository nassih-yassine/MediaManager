package dev.mediamanager.mobile.data.update

/**
 * A dot-separated numeric version. Missing segments count as zero, so the local
 * `VERSION_NAME` (`<tag>.<commitCount>`, e.g. `0.1.1.1803`) compares as "same or
 * newer" against the tag it was built from (`0.1.1`).
 */
data class AppVersion(val parts: List<Int>) : Comparable<AppVersion> {

    override fun compareTo(other: AppVersion): Int {
        val size = maxOf(parts.size, other.parts.size)
        for (index in 0 until size) {
            val mine = parts.getOrElse(index) { 0 }
            val theirs = other.parts.getOrElse(index) { 0 }
            if (mine != theirs) return mine.compareTo(theirs)
        }
        return 0
    }

    fun display(): String = parts.joinToString(".")

    companion object {
        private val releasePrefix = Regex("^(android-)?v", RegexOption.IGNORE_CASE)

        fun parse(text: String?): AppVersion? {
            val raw = text?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val numbers = raw
                .replace(releasePrefix, "")
                .split('.')
                .mapNotNull { segment -> segment.takeWhile(Char::isDigit).toIntOrNull() }
            return numbers.takeIf { it.isNotEmpty() }?.let(::AppVersion)
        }
    }
}
