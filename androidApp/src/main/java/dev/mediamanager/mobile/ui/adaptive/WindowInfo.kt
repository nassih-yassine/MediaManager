package dev.mediamanager.mobile.ui.adaptive

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Width decides navigation; height decides density.
 *
 * A landscape phone is ~891x411 dp: "expanded" by width, but with less height than
 * any portrait phone. Classifying on width alone would hand it a tablet layout with
 * no room to draw it, so [isTwoPane] also requires a normal height.
 */
enum class WidthClass { COMPACT, MEDIUM, EXPANDED }

enum class HeightClass { SHORT, NORMAL }

data class WindowInfo(
    val widthDp: Int,
    val heightDp: Int,
) {
    val widthClass: WidthClass
        get() = when {
            widthDp < 600 -> WidthClass.COMPACT
            widthDp < 840 -> WidthClass.MEDIUM
            else -> WidthClass.EXPANDED
        }

    val heightClass: HeightClass
        get() = if (heightDp < SHORT_HEIGHT_DP) HeightClass.SHORT else HeightClass.NORMAL

    /** Anything with room for a rail instead of the bottom bar. */
    val isWide: Boolean get() = widthClass != WidthClass.COMPACT

    val useRail: Boolean get() = widthClass == WidthClass.EXPANDED

    val isTwoPane: Boolean get() = isWide && heightClass == HeightClass.NORMAL

    /** Columns that fit at [minCellDp], never fewer than two. */
    fun gridColumns(minCellDp: Int): Int =
        (widthDp / minCellDp.coerceAtLeast(1)).coerceAtLeast(2)

    companion object {
        const val SHORT_HEIGHT_DP = 480
    }
}

/** Replaced once in the shell; screens read it instead of measuring themselves. */
val LocalWindowInfo = staticCompositionLocalOf { WindowInfo(widthDp = 360, heightDp = 800) }
