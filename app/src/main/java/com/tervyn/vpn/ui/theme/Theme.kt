package com.tervyn.vpn.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.R
import com.tervyn.vpn.data.AppTheme

/**
 * Tervyn design tokens. One accent (electric blue → cyan), semantic colours for state, three neutral
 * surface levels. Everything in the UI reads from here — no ad-hoc colours in screens.
 */
@Immutable
data class TervynColors(
    val bg: Color = Color(0xFF070B16),
    val surface: Color = Color(0xFF0D1424),
    val card: Color = Color(0xFF111A2E),
    val cardHigh: Color = Color(0xFF182440),
    val border: Color = Color(0x1AFFFFFF),
    val borderStrong: Color = Color(0x33FFFFFF),
    val accent: Color = Color(0xFF3D7BFF),
    val accent2: Color = Color(0xFF22D3EE),
    val success: Color = Color(0xFF2EE59D),
    val warning: Color = Color(0xFFF5B544),
    val danger: Color = Color(0xFFFF5C7A),
    val text: Color = Color(0xFFF3F6FC),
    val text2: Color = Color(0xFFA7B4CC),
    val text3: Color = Color(0xFF66738F),
    val onAccent: Color = Color(0xFFFFFFFF),
) {
    val accentGradient get() = Brush.linearGradient(listOf(accent, accent2))
    val accentSoft get() = accent.copy(alpha = 0.14f)
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 20.dp
}

object Radius {
    val s = 10.dp
    val m = 16.dp
    val l = 22.dp
    val xl = 28.dp
}

val LocalTervynColors = staticCompositionLocalOf { TervynColors() }

object Tervyn {
    val colors: TervynColors @Composable get() = LocalTervynColors.current
}

val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
    Font(R.font.manrope_extrabold, FontWeight.ExtraBold),
)

private fun t(size: Int, weight: FontWeight, lineHeight: Int = (size * 1.35).toInt(), letterSpacing: Float = 0f) =
    TextStyle(fontFamily = Manrope, fontWeight = weight, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = letterSpacing.sp)

val TervynTypography = Typography(
    displayLarge = t(44, FontWeight.ExtraBold, 48, -1f),
    displayMedium = t(34, FontWeight.ExtraBold, 40, -0.8f),
    displaySmall = t(28, FontWeight.Bold, 34, -0.5f),
    headlineLarge = t(26, FontWeight.Bold, 32, -0.4f),
    headlineMedium = t(22, FontWeight.Bold, 28, -0.3f),
    headlineSmall = t(20, FontWeight.Bold, 26, -0.2f),
    titleLarge = t(18, FontWeight.Bold, 24),
    titleMedium = t(16, FontWeight.SemiBold, 22),
    titleSmall = t(14, FontWeight.SemiBold, 20),
    bodyLarge = t(16, FontWeight.Normal, 24),
    bodyMedium = t(14, FontWeight.Normal, 21),
    bodySmall = t(12, FontWeight.Normal, 17),
    labelLarge = t(15, FontWeight.SemiBold, 20),
    labelMedium = t(13, FontWeight.SemiBold, 18),
    labelSmall = t(11, FontWeight.Medium, 15, 0.3f),
)

val TervynShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(Radius.s),
    medium = RoundedCornerShape(Radius.m),
    large = RoundedCornerShape(Radius.l),
    extraLarge = RoundedCornerShape(Radius.xl),
)

fun colorsFor(theme: AppTheme) = when (theme) {
    AppTheme.MIDNIGHT -> TervynColors()
    AppTheme.AMOLED -> TervynColors(bg = Color(0xFF000000), surface = Color(0xFF07090F), card = Color(0xFF0E1119), cardHigh = Color(0xFF161B27))
    AppTheme.OCEAN -> TervynColors(bg = Color(0xFF051226), surface = Color(0xFF0A1B38), card = Color(0xFF0F244A), cardHigh = Color(0xFF17305E), accent = Color(0xFF4D8DFF))
}

@Composable
fun TervynTheme(theme: AppTheme = AppTheme.MIDNIGHT, content: @Composable () -> Unit) {
    val c = colorsFor(theme)
    val scheme = darkColorScheme(
        primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.text,
        secondary = c.accent2, onSecondary = Color.Black, tertiary = c.success,
        background = c.bg, onBackground = c.text, surface = c.surface, onSurface = c.text,
        surfaceVariant = c.card, onSurfaceVariant = c.text2, error = c.danger, onError = Color.White, outline = c.borderStrong, outlineVariant = c.border,
        surfaceContainer = c.card, surfaceContainerLow = c.surface, surfaceContainerHigh = c.cardHigh, surfaceContainerHighest = c.cardHigh,
        inverseSurface = c.text, inverseOnSurface = c.bg, scrim = Color.Black,
    )
    CompositionLocalProvider(LocalTervynColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = TervynTypography, shapes = TervynShapes, content = content)
    }
}
