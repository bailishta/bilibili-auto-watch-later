package com.bilibili.watchlater.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColorScheme = lightColorScheme(
    primary = Pink,
    onPrimary = BgWhite,
    primaryContainer = PinkBg,
    secondary = PinkLight,
    surface = BgWhite,
    background = BgGray,
    onBackground = TextPrimary,
    onSurface = TextPrimary,
    onSurfaceVariant = TextSecondary
)

@Composable
fun BiliWatchLaterTheme(content: @Composable () -> Unit) {
    val scale = rememberDeviceScale()
    CompositionLocalProvider(LocalDeviceScale provides scale) {
        MaterialTheme(
            colorScheme = LightColorScheme,
            content = content
        )
    }
}
