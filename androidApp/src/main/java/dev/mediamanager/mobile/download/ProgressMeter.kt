package dev.mediamanager.mobile.download

/**
 * Turns per-connection byte counters into one clamped 0..1 fraction.
 *
 * The parallel downloader reports *cumulative* bytes per part, not deltas. Summing
 * those directly made the percentage climb past 100 and keep going, which the
 * notification then rendered as "1425%". Each part now reports an increment that is
 * measured against its own last value before it reaches this meter.
 */
internal class ProgressMeter(private val length: Long) {

    private val seen = java.util.concurrent.atomic.AtomicLong(0)

    fun add(bytes: Long): Float {
        val capped = seen.addAndGet(bytes.coerceAtLeast(0)).coerceAtMost(length)
        return (capped.toDouble() / length.coerceAtLeast(1)).toFloat().coerceIn(0f, 1f)
    }

    fun reset() = seen.set(0)
}
