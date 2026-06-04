package com.bilibili.watchlater.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration

@Immutable
data class DeviceScale(
    val factor: Float
) {
    val fontScale: Float get() = factor
    val spacingScale: Float get() = factor
}

val LocalDeviceScale = compositionLocalOf { DeviceScale(1f) }

@Composable
fun rememberDeviceScale(): DeviceScale {
    val config = LocalConfiguration.current
    val widthDp = config.screenWidthDp
    val factor = when {
        widthDp <= 320 -> 0.85f
        widthDp <= 360 -> 0.92f
        widthDp <= 410 -> 1.0f
        widthDp <= 500 -> 1.06f
        widthDp <= 600 -> 1.12f
        else -> 1.2f
    }
    return remember(widthDp) { DeviceScale(factor) }
}
