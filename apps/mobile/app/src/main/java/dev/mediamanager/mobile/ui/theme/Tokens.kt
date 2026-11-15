package dev.mediamanager.mobile.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

enum class ThemeFamily(val key: String) {
    SLATE("slate"),
    PAPER("paper"),
    OCEAN("ocean"),
    MOSS("moss");

    companion object {
        fun fromKey(key: String?): ThemeFamily = entries.firstOrNull { it.key == key } ?: SLATE
    }
}

enum class ThemeMode(val key: String) {
    LIGHT("light"),
    DARK("dark"),
    SYSTEM("system");

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

data class AppColors(
    val canvas: Color,
    val panel: Color,
    val panel2: Color,
    val line: Color,
    val lineStrong: Color,
    val ink: Color,
    val muted: Color,
    val accent: Color,
    val accentInk: Color,
    val danger: Color,
)

fun colorsFor(family: ThemeFamily, dark: Boolean): AppColors = when (family) {
    ThemeFamily.SLATE -> if (dark) AppColors(
        canvas = Color(0xFF0A0A0B),
        panel = Color(0xFF111113),
        panel2 = Color(0xFF17171A),
        line = Color(0xFF27272D),
        lineStrong = Color(0xFF3A3A42),
        ink = Color(0xFFF1F1F3),
        muted = Color(0xFFA0A0AA),
        accent = Color(0xFF9AE6B4),
        accentInk = Color(0xFF06281A),
        danger = Color(0xFFF87171),
    ) else AppColors(
        canvas = Color(0xFFFBFBF8),
        panel = Color(0xFFFFFFFF),
        panel2 = Color(0xFFF1F1EE),
        line = Color(0xFFE2E2DC),
        lineStrong = Color(0xFFC7C7BE),
        ink = Color(0xFF16161A),
        muted = Color(0xFF6A6A72),
        accent = Color(0xFF0F7A4A),
        accentInk = Color(0xFFF2FFF8),
        danger = Color(0xFFB91C1C),
    )

    ThemeFamily.PAPER -> if (dark) AppColors(
        canvas = Color(0xFF12100E),
        panel = Color(0xFF1A1714),
        panel2 = Color(0xFF211D19),
        line = Color(0xFF2F2A24),
        lineStrong = Color(0xFF453D34),
        ink = Color(0xFFF4EFE7),
        muted = Color(0xFFA89E91),
        accent = Color(0xFFE8B06A),
        accentInk = Color(0xFF241505),
        danger = Color(0xFFF08A7A),
    ) else AppColors(
        canvas = Color(0xFFF7F3EA),
        panel = Color(0xFFFFFDF8),
        panel2 = Color(0xFFEFE9DC),
        line = Color(0xFFE0D8C8),
        lineStrong = Color(0xFFC9BDA7),
        ink = Color(0xFF211C15),
        muted = Color(0xFF6F6557),
        accent = Color(0xFF9A5B12),
        accentInk = Color(0xFFFFF8EA),
        danger = Color(0xFFA53125),
    )

    ThemeFamily.OCEAN -> if (dark) AppColors(
        canvas = Color(0xFF071018),
        panel = Color(0xFF0C1922),
        panel2 = Color(0xFF10222E),
        line = Color(0xFF1C3340),
        lineStrong = Color(0xFF2B4A5C),
        ink = Color(0xFFE6F1F7),
        muted = Color(0xFF90A6B4),
        accent = Color(0xFF5CC8F5),
        accentInk = Color(0xFF04222F),
        danger = Color(0xFFF07F7F),
    ) else AppColors(
        canvas = Color(0xFFF2F8FB),
        panel = Color(0xFFFFFFFF),
        panel2 = Color(0xFFE7F1F6),
        line = Color(0xFFD3E2EA),
        lineStrong = Color(0xFFB3CBD7),
        ink = Color(0xFF0D1B24),
        muted = Color(0xFF5B7280),
        accent = Color(0xFF0B6F9C),
        accentInk = Color(0xFFF0FBFF),
        danger = Color(0xFFB3261E),
    )

    ThemeFamily.MOSS -> if (dark) AppColors(
        canvas = Color(0xFF080C09),
        panel = Color(0xFF0E1510),
        panel2 = Color(0xFF131D16),
        line = Color(0xFF203025),
        lineStrong = Color(0xFF324A39),
        ink = Color(0xFFEAF3EC),
        muted = Color(0xFF94A89A),
        accent = Color(0xFFB8E986),
        accentInk = Color(0xFF17260A),
        danger = Color(0xFFEF8B7E),
    ) else AppColors(
        canvas = Color(0xFFF4F8F2),
        panel = Color(0xFFFFFFFF),
        panel2 = Color(0xFFE9F0E5),
        line = Color(0xFFD7E2D1),
        lineStrong = Color(0xFFB9C9B1),
        ink = Color(0xFF131A14),
        muted = Color(0xFF5F6F62),
        accent = Color(0xFF3F6F1F),
        accentInk = Color(0xFFF4FFE9),
        danger = Color(0xFFA83A2A),
    )
}

val LocalAppColors = staticCompositionLocalOf { colorsFor(ThemeFamily.SLATE, true) }
