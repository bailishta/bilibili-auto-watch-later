package com.bilibili.watchlater.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontFamily
import androidx.core.view.WindowCompat

private val LightColors = lightColorScheme(
    primary = Pink, onPrimary = Color.White, primaryContainer = Color(0xFFFFEDF3), onPrimaryContainer = Color(0xFFAE315A),
    secondary = BiliBlue, onSecondary = Color.White, secondaryContainer = Color(0xFFE3F5FC), onSecondaryContainer = Color(0xFF007DA8),
    surface = Color.White, background = Color(0xFFF6F7F8), surfaceContainer = Color.White,
    surfaceVariant = Color(0xFFF1F2F3), onSurface = Color(0xFF18191C), onBackground = Color(0xFF18191C),
    onSurfaceVariant = Color(0xFF61666D), outline = Color(0xFF858B93), outlineVariant = Color(0xFFE3E5E7)
)
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF86A8), onPrimary = Color(0xFF331822), primaryContainer = Color(0xFF39222B), onPrimaryContainer = Color(0xFFFFABC3),
    secondary = Color(0xFF43C6F0), onSecondary = Color(0xFF11252D), secondaryContainer = Color(0xFF1D3540), onSecondaryContainer = Color(0xFF7DDAF9),
    surface = Color(0xFF1E2023), background = Color(0xFF141517), surfaceContainer = Color(0xFF1E2023),
    surfaceVariant = Color(0xFF292B2F), onSurface = Color(0xFFF1F2F3), onBackground = Color(0xFFF1F2F3),
    onSurfaceVariant = Color(0xFFB5BAC3), outline = Color(0xFF8F959E), outlineVariant = Color(0xFF3C3F45)
)
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
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
