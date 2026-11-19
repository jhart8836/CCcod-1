package com.anchorone.app.ui.theme

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.anchorone.app.data.MotionLevel
import com.anchorone.app.data.ThemeMode
import com.anchorone.app.engine.Density

/** ANCHOR ONE colour tokens. Midnight/graphite foundations, restrained cyan/teal/silver light. */
@Immutable
data class AnchorColors(
    val isDark: Boolean,
    val bgTop: Color,
    val bgBottom: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val outline: Color,
    val text: Color,
    val textDim: Color,
    val textFaint: Color,
    val accent: Color,
    val accentSoft: Color,
    val teal: Color,
    val silver: Color,
    val glow: Color,
    val onAccent: Color,
    val alert: Color,
    val tones: List<Color>,
)

val MidnightColors = AnchorColors(
    isDark = true,
    bgTop = Color(0xFF0A1120),
    bgBottom = Color(0xFF05080F),
    surface = Color(0xFF111926),
    surfaceHigh = Color(0xFF18222F),
    outline = Color(0xFF263244),
    text = Color(0xFFE6EDF5),
    textDim = Color(0xFF9AA7B8),
    textFaint = Color(0xFF5E6B7E),
    accent = Color(0xFF6FD8E8),
    accentSoft = Color(0xFF1D3F4A),
    teal = Color(0xFF3FBFAE),
    silver = Color(0xFFB9C5D3),
    glow = Color(0xFF58C9DD),
    onAccent = Color(0xFF04131A),
    alert = Color(0xFFE59A8C),
    tones = listOf(Color(0xFF6FD8E8), Color(0xFF3FBFAE), Color(0xFFB9C5D3)),
)

val DaylightColors = AnchorColors(
    isDark = false,
    bgTop = Color(0xFFF4F7FB),
    bgBottom = Color(0xFFE7EDF4),
    surface = Color(0xFFFFFFFF),
    surfaceHigh = Color(0xFFF0F4F8),
    outline = Color(0xFFCCD6E2),
    text = Color(0xFF0D1626),
    textDim = Color(0xFF4A5869),
    textFaint = Color(0xFF7D8A9B),
    accent = Color(0xFF0B7F8E),
    accentSoft = Color(0xFFD5EEF1),
    teal = Color(0xFF138A7A),
    silver = Color(0xFF5D6B7C),
    glow = Color(0xFF2BA5B8),
    onAccent = Color(0xFFFFFFFF),
    alert = Color(0xFFB4513F),
    tones = listOf(Color(0xFF0B7F8E), Color(0xFF138A7A), Color(0xFF5D6B7C)),
)

/** Quiet Mode: discreet, low-contrast graphite with no luminous accents. */
fun AnchorColors.quiet(): AnchorColors = if (isDark) copy(
    bgTop = Color(0xFF0B0D10), bgBottom = Color(0xFF07080A), surface = Color(0xFF121418),
    surfaceHigh = Color(0xFF181B20), outline = Color(0xFF23272E),
    accent = Color(0xFF9AA4B1), accentSoft = Color(0xFF1C2026), glow = Color(0x00000000),
    teal = Color(0xFF8C969F), onAccent = Color(0xFF0B0D10),
    tones = listOf(Color(0xFF9AA4B1), Color(0xFF6E7884), Color(0xFFC2C9D1)),
) else copy(accent = Color(0xFF4A5563), glow = Color(0x00000000), accentSoft = Color(0xFFE3E7EC), onAccent = Color.White)

/** Everything the UI needs to adapt: density, motion, haptics, quiet. */
@Immutable
data class UiEnv(
    val density: Density = Density.NORMAL,
    val motion: MotionLevel = MotionLevel.FULL,
    val haptics: Boolean = true,
    val quiet: Boolean = false,
) {
    val reducedMotion: Boolean get() = motion == MotionLevel.REDUCED
    /** Multiplier for animation speed; 0 means static. */
    val motionScale: Float get() = when (motion) {
        MotionLevel.FULL -> 1f
        MotionLevel.GENTLE -> 0.55f
        MotionLevel.REDUCED -> 0f
    }
    val simplified: Boolean get() = density != Density.NORMAL
    val minimal: Boolean get() = density == Density.MINIMAL
}

val LocalAnchorColors = staticCompositionLocalOf { MidnightColors }
val LocalUiEnv = staticCompositionLocalOf { UiEnv() }

object Anchor {
    val colors: AnchorColors @Composable get() = LocalAnchorColors.current
    val env: UiEnv @Composable get() = LocalUiEnv.current
}

private val Sans = FontFamily.SansSerif

val AnchorTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 44.sp, lineHeight = 50.sp, letterSpacing = (-0.5).sp),
    displayMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Light, fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 20.sp, lineHeight = 26.sp),
    titleMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 17.sp, lineHeight = 24.sp, letterSpacing = 0.1.sp),
    titleSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.1.sp),
    bodyLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 17.sp, lineHeight = 26.sp),
    bodyMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 22.sp),
    bodySmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = 0.4.sp),
    labelMedium = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.4.sp),
)

@Composable
fun AnchorTheme(themeMode: ThemeMode, env: UiEnv, content: @Composable () -> Unit) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val base = if (dark) MidnightColors else DaylightColors
    val colors = if (env.quiet) base.quiet() else base
    val scheme = if (dark) darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, secondary = colors.teal,
        background = colors.bgBottom, onBackground = colors.text, surface = colors.surface, onSurface = colors.text,
        surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textDim, outline = colors.outline, error = colors.alert,
    ) else lightColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent, secondary = colors.teal,
        background = colors.bgBottom, onBackground = colors.text, surface = colors.surface, onSurface = colors.text,
        surfaceVariant = colors.surfaceHigh, onSurfaceVariant = colors.textDim, outline = colors.outline, error = colors.alert,
    )
    CompositionLocalProvider(LocalAnchorColors provides colors, LocalUiEnv provides env) {
        MaterialTheme(colorScheme = scheme, typography = AnchorTypography, content = content)
    }
}

/** Subtle, optional haptics. Uses View haptics so no VIBRATE permission is needed. */
class Haptics(private val view: View, private val enabled: Boolean) {
    fun tick() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    fun confirm() {
        if (enabled) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val view = LocalView.current
    val enabled = LocalUiEnv.current.haptics
    return remember(view, enabled) { Haptics(view, enabled) }
}
