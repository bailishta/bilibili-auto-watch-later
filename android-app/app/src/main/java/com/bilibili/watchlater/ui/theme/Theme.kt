package com.bilibili.watchlater.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
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
private val GlassLightColors = LightColors.copy(
    primary = Color(0xFF8C3153), onPrimary = Color.White,
    primaryContainer = Color(0xFFF8DCE9), onPrimaryContainer = Color(0xFF70213F),
    secondary = Color(0xFF285F80), secondaryContainer = Color(0xFFD5EAF8), onSecondaryContainer = Color(0xFF173D56),
    background = Color(0xFFECEEF4), surface = Color.White, surfaceContainer = Color(0xFFF8F9FC),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color.White,
    surfaceContainerHigh = Color(0xFFFEFEFF), surfaceContainerHighest = Color(0xFFE6E9F0),
    surfaceVariant = Color(0xFFECECF1), onSurface = Color(0xFF1C1C1E), onBackground = Color(0xFF1C1C1E),
    onSurfaceVariant = Color(0xFF57575F), outline = Color(0xFF6C6C75), outlineVariant = Color(0xFFE4E4EA),
    surfaceTint = Color(0xFFAA375D)
)
private val GlassDarkColors = DarkColors.copy(
    primary = Color(0xFFFFAFD0), onPrimary = Color(0xFF481D32),
    primaryContainer = Color(0xFF54334F), onPrimaryContainer = Color(0xFFFFD5E7),
    secondary = Color(0xFFA9D8F5), secondaryContainer = Color(0xFF29445E), onSecondaryContainer = Color(0xFFD2EDFF),
    background = Color(0xFF101115), surface = Color(0xFF25272D), surfaceContainer = Color(0xFF25272D),
    surfaceContainerLowest = Color(0xFF101115), surfaceContainerLow = Color(0xFF30333B),
    surfaceContainerHigh = Color(0xFF2D3038), surfaceContainerHighest = Color(0xFF3A3D46),
    surfaceVariant = Color(0xFF303035), onSurface = Color(0xFFF5F5F7), onBackground = Color(0xFFF5F5F7),
    onSurfaceVariant = Color(0xFFCBCBD2), outline = Color(0xFFAAAAB4), outlineVariant = Color(0xFF39393F),
    surfaceTint = Color(0xFFFFAFD0)
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
fun BiliWatchLaterTheme(darkTheme: Boolean = isSystemInDarkTheme(), liquidGlass: Boolean = false, content: @Composable () -> Unit) {
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
    val colors = when {
        liquidGlass && darkTheme -> GlassDarkColors
        liquidGlass -> GlassLightColors
        darkTheme -> DarkColors
        else -> LightColors
    }
    CompositionLocalProvider(LocalDeviceScale provides rememberDeviceScale(), LocalGlassStyle provides GlassStyle(liquidGlass, darkTheme),
        LocalContentColor provides colors.onBackground) {
        MaterialTheme(colorScheme = colors, typography = AppTypography, shapes = AppShapes) {
            AppBackdrop(content)
        }
    }
}
