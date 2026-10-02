package com.bilibili.watchlater.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Pink = Color(0xFFFB7299)
val PinkDark = Color(0xFFE56080)
val PinkLight = Color(0xFFFF9A9E)
val BiliBlue = Color(0xFF00AEEC)
val PinkBg: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextHint: Color @Composable get() = MaterialTheme.colorScheme.outline
val BgGray: Color @Composable get() = MaterialTheme.colorScheme.background
val BgWhite: Color @Composable get() = MaterialTheme.colorScheme.surface
private val isDark: Boolean @Composable get() = MaterialTheme.colorScheme.surface.luminance() < .5f
val Success: Color @Composable get() = if (isDark) Color(0xFF78D89A) else Color(0xFF2AA65A)
val SuccessBg: Color @Composable get() = if (isDark) Color(0xFF1B3526) else Color(0xFFE8F7EC)
val Warning: Color @Composable get() = if (isDark) Color(0xFFFFC26F) else Color(0xFFB96E00)
val WarningBg: Color @Composable get() = if (isDark) Color(0xFF3B301F) else Color(0xFFFFF3DD)
