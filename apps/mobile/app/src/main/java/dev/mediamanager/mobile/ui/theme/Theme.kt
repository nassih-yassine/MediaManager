package dev.mediamanager.mobile.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.unit.sp
import dev.mediamanager.mobile.R

/**
 * Space Grotesk ships as one variable font; each weight is pinned with a variation
 * setting so 400/500/600/700 are real instances, not synthesized.
 */
@OptIn(ExperimentalTextApi::class)
private fun spaceGrotesk(weight: Int, fontWeight: FontWeight) = Font(
    resId = R.font.space_grotesk,
    weight = fontWeight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val SpaceGrotesk = FontFamily(
    spaceGrotesk(400, FontWeight.Normal),
    spaceGrotesk(500, FontWeight.Medium),
    spaceGrotesk(600, FontWeight.SemiBold),
    spaceGrotesk(700, FontWeight.Bold),
)

/** Numbers and durations only, exactly like the web's numeric face. */
val JetBrainsMono = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

private val MmTypography = Typography(
    displaySmall = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 30.sp),
    headlineMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = SpaceGrotesk, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = SpaceGrotesk, fontSize = 14.sp),
    labelLarge = TextStyle(fontFamily = SpaceGrotesk, fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontFamily = JetBrainsMono, fontSize = 12.sp),
    labelSmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 11.sp),
    bodySmall = TextStyle(fontFamily = SpaceGrotesk, fontSize = 12.sp),
)

fun isDark(colors: AppColors): Boolean = colors.ink.luminance() > 0.5f

@Composable
fun MmTheme(colors: AppColors, content: @Composable () -> Unit) {
    val dark = isDark(colors)
    val scheme = if (dark) {
        darkColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            outlineVariant = colors.lineStrong,
            error = colors.danger,
        )
    } else {
        lightColorScheme(
            primary = colors.accent,
            onPrimary = colors.accentInk,
            background = colors.canvas,
            onBackground = colors.ink,
            surface = colors.panel,
            onSurface = colors.ink,
            surfaceVariant = colors.panel2,
            onSurfaceVariant = colors.muted,
            outline = colors.line,
            outlineVariant = colors.lineStrong,
            error = colors.danger,
        )
    }
    CompositionLocalProvider(LocalAppColors provides colors) {
        MaterialTheme(colorScheme = scheme, typography = MmTypography, content = content)
    }
}

@Composable
fun MmTheme(family: ThemeFamily, mode: ThemeMode, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MmTheme(colorsFor(family, dark), content)
}

val Transparent: Color = Color.Transparent
