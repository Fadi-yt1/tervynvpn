package com.tervyn.vpn.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tervyn.vpn.data.AppTheme

/** Centralised design tokens for Tervyn. */
@Immutable
data class TervynColors(
    val bg: Color = Color(0xFF030817),
    val bg2: Color = Color(0xFF07132D),
    val card: Color = Color(0xFF0B1735),
    val cardBorder: Color = Color(0x3329A3FF),
    val blue: Color = Color(0xFF086BFF),
    val cyan: Color = Color(0xFF00E5FF),
    val purple: Color = Color(0xFFA100FF),
    val green: Color = Color(0xFF00E58A),
    val amber: Color = Color(0xFFFFC23D),
    val red: Color = Color(0xFFFF4D6A),
    val text: Color = Color(0xFFFFFFFF),
    val text2: Color = Color(0xFFAFC5E8),
    val text3: Color = Color(0xFF6F86AD),
) {
    val primaryGradient get() = Brush.horizontalGradient(listOf(blue, Color(0xFF3B5BFF), purple))
    val neonGradient get() = Brush.linearGradient(listOf(cyan, blue, purple))
    val backgroundGradient get() = Brush.verticalGradient(listOf(bg2, bg, bg))
}

object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val gutter = 16.dp
    val radius = 20.dp
    val radiusSmall = 14.dp
}

val LocalTervynColors = staticCompositionLocalOf { TervynColors() }

object Tervyn {
    val colors: TervynColors @Composable get() = LocalTervynColors.current
}

private val typography = Typography(
    displaySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 30.sp, letterSpacing = (-0.5).sp),
    headlineSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 16.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = FontFamily.SansSerif, fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.SemiBold, fontSize = 15.sp),
    labelMedium = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 13.sp),
    labelSmall = TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Medium, fontSize = 11.sp),
)

fun colorsFor(theme: AppTheme) = when (theme) {
    AppTheme.MIDNIGHT -> TervynColors()
    AppTheme.AMOLED -> TervynColors(bg = Color(0xFF000000), bg2 = Color(0xFF02060F), card = Color(0xFF070E20))
    AppTheme.DEEP_BLUE -> TervynColors(bg = Color(0xFF041029), bg2 = Color(0xFF0A1F4A), card = Color(0xFF0E2350))
}

@Composable
fun TervynTheme(theme: AppTheme = AppTheme.MIDNIGHT, content: @Composable () -> Unit) {
    val c = colorsFor(theme)
    val scheme = darkColorScheme(
        primary = c.blue, onPrimary = Color.White, secondary = c.cyan, tertiary = c.purple,
        background = c.bg, onBackground = c.text, surface = c.card, onSurface = c.text,
        surfaceVariant = c.bg2, onSurfaceVariant = c.text2, error = c.red, outline = c.cardBorder,
        surfaceContainer = c.card, surfaceContainerHigh = c.bg2, surfaceContainerHighest = c.bg2,
    )
    CompositionLocalProvider(LocalTervynColors provides c) {
        MaterialTheme(colorScheme = scheme, typography = typography, content = content)
    }
}
