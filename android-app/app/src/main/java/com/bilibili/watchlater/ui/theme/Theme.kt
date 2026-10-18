package com.bilibili.watchlater.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Color(0xFFC74660), onPrimary = Color.White, primaryContainer = Color(0xFFFFF0F2), onPrimaryContainer = Color(0xFFAC425A),
    secondary = Color(0xFF326B85), onSecondary = Color.White, secondaryContainer = Color(0xFFEAF2F5), onSecondaryContainer = Color(0xFF326B85),
    tertiary = Color(0xFF80614B), onTertiary = Color.White, tertiaryContainer = Color(0xFFF1E6DC), onTertiaryContainer = Color(0xFF594331),
    surface = Color(0xFFFFFEFD), background = Color(0xFFF8F5F1), surfaceContainer = Color(0xFFFFFEFD),
    surfaceContainerLowest = Color(0xFFFFFEFD), surfaceContainerLow = Color(0xFFFAF7F5),
    surfaceContainerHigh = Color(0xFFF5F1EF), surfaceContainerHighest = Color(0xFFF0E9E5),
    surfaceVariant = Color(0xFFF5F1EF), onSurface = Color(0xFF34302F), onBackground = Color(0xFF34302F),
    onSurfaceVariant = Color(0xFF6B615E), outline = Color(0xFF796E69), outlineVariant = Color(0xFFEBE3DF),
    surfaceTint = Color(0xFFC74660), inverseSurface = Color(0xFF34302F), inverseOnSurface = Color(0xFFF5F1EF), inversePrimary = Color(0xFFF29BAB)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFF29BAB), onPrimary = Color(0xFF391821), primaryContainer = Color(0xFF452C37), onPrimaryContainer = Color(0xFFF2A5B4),
    secondary = Color(0xFFA5CDDE), onSecondary = Color(0xFF192B33), secondaryContainer = Color(0xFF25343A), onSecondaryContainer = Color(0xFFA5CDDE),
    tertiary = Color(0xFFD7BBA1), onTertiary = Color(0xFF33271D), tertiaryContainer = Color(0xFF413328), onTertiaryContainer = Color(0xFFF1D6BB),
    surface = Color(0xFF27262C), background = Color(0xFF1C1B20), surfaceContainer = Color(0xFF27262C),
    surfaceContainerLowest = Color(0xFF1C1B20), surfaceContainerLow = Color(0xFF222127),
    surfaceContainerHigh = Color(0xFF302E35), surfaceContainerHighest = Color(0xFF39363F),
    surfaceVariant = Color(0xFF302E35), onSurface = Color(0xFFF5F1EF), onBackground = Color(0xFFF5F1EF),
    onSurfaceVariant = Color(0xFFC8C0C3), outline = Color(0xFFB4A9AF), outlineVariant = Color(0xFF413D46),
    surfaceTint = Color(0xFFF29BAB), inverseSurface = Color(0xFFF5F1EF), inverseOnSurface = Color(0xFF34302F), inversePrimary = Color(0xFFC74660)
)
private val AppShapes = Shapes(extraSmall = RoundedCornerShape(8.dp), small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp), large = RoundedCornerShape(20.dp), extraLarge = RoundedCornerShape(28.dp))
private val AppTypography = Typography().let { base -> base.copy(
    titleLarge = base.titleLarge.copy(fontFamily = FontFamily.SansSerif),
    titleMedium = base.titleMedium.copy(fontFamily = FontFamily.SansSerif),
    bodyLarge = base.bodyLarge.copy(fontFamily = FontFamily.SansSerif),
    bodyMedium = base.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
    bodySmall = base.bodySmall.copy(fontFamily = FontFamily.SansSerif),
    labelLarge = base.labelLarge.copy(fontFamily = FontFamily.SansSerif),
    labelMedium = base.labelMedium.copy(fontFamily = FontFamily.SansSerif)
) }

@Composable
fun BiliWatchLaterTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view).apply {
                    isAppearanceLightStatusBars = !darkTheme
                    isAppearanceLightNavigationBars = !darkTheme
                }
            }
        }
    }
    CompositionLocalProvider(LocalDeviceScale provides rememberDeviceScale()) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
