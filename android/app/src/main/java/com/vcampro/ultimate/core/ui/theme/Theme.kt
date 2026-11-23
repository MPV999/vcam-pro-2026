package com.vcampro.ultimate.core.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes

/**
 * VCAM PRO premium palette. Deep near-black surfaces with a cool blue accent for
 * dark mode, warm off-white with the same accent for light mode.
 */
object VcamPalette {
    val Accent = Color(0xFF4F8CFF)
    val AccentBright = Color(0xFF7FD1FF)
    val Mint = Color(0xFF39D9A0)
    val Amber = Color(0xFFFFB347)
    val Rose = Color(0xFFFF5C7A)
    val SurfaceDark = Color(0xFF07090D)
    val SurfaceDarkElevated = Color(0xFF11151C)
    val SurfaceDarkCard = Color(0xFF161B24)
    val OutlineDark = Color(0xFF262E3B)
    val TextPrimaryDark = Color(0xFFF2F5FA)
    val TextSecondaryDark = Color(0xFF9AA6B8)
    val SurfaceLight = Color(0xFFF6F8FC)
    val SurfaceLightCard = Color(0xFFFFFFFF)
    val OutlineLight = Color(0xFFDDE3EE)
    val TextPrimaryLight = Color(0xFF0D1220)
    val TextSecondaryLight = Color(0xFF5A6577)
}

private val DarkScheme = darkColorScheme(
    primary = VcamPalette.Accent,
    onPrimary = Color(0xFF04101F),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD6E4FF),
    secondary = VcamPalette.AccentBright,
    onSecondary = Color(0xFF00131F),
    tertiary = VcamPalette.Mint,
    onTertiary = Color(0xFF00160E),
    background = VcamPalette.SurfaceDark,
    onBackground = VcamPalette.TextPrimaryDark,
    surface = VcamPalette.SurfaceDark,
    onSurface = VcamPalette.TextPrimaryDark,
    surfaceVariant = VcamPalette.SurfaceDarkCard,
    onSurfaceVariant = VcamPalette.TextSecondaryDark,
    surfaceContainer = VcamPalette.SurfaceDarkElevated,
    surfaceContainerHigh = VcamPalette.SurfaceDarkCard,
    outline = VcamPalette.OutlineDark,
    outlineVariant = Color(0xFF1B2230),
    error = VcamPalette.Rose,
    onError = Color(0xFF2A0009)
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF1F6BFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF),
    onPrimaryContainer = Color(0xFF001B45),
    secondary = Color(0xFF0F7EA8),
    onSecondary = Color.White,
    tertiary = Color(0xFF11845F),
    onTertiary = Color.White,
    background = VcamPalette.SurfaceLight,
    onBackground = VcamPalette.TextPrimaryLight,
    surface = VcamPalette.SurfaceLight,
    onSurface = VcamPalette.TextPrimaryLight,
    surfaceVariant = Color(0xFFEDF1F8),
    onSurfaceVariant = VcamPalette.TextSecondaryLight,
    surfaceContainer = VcamPalette.SurfaceLightCard,
    surfaceContainerHigh = VcamPalette.SurfaceLightCard,
    outline = VcamPalette.OutlineLight,
    outlineVariant = Color(0xFFE9EEF6),
    error = Color(0xFFC1123B),
    onError = Color.White
)

private val VcamTypography = Typography(
    displaySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 32.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.4).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 26.sp,
        lineHeight = 32.sp,
        letterSpacing = (-0.2).sp
    ),
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 28.sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 24.sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
        lineHeight = 21.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 21.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.6.sp
    )
)

private val VcamShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun VcamTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)

        darkTheme -> DarkScheme
        else -> LightScheme
    }
    MaterialTheme(
        colorScheme = colorScheme,
        typography = VcamTypography,
        shapes = VcamShapes,
        content = content
    )
}
