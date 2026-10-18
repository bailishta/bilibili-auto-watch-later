package com.bilibili.watchlater.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

val Pink: Color @Composable get() = MaterialTheme.colorScheme.primary
val Coral: Color @Composable get() = if (isDark) Color(0xFFF29BAB) else Color(0xFFED7F8E)
val ActionCoral = Color(0xFFD85C70)
val OnCoral = Color.White
val PinkDark = Color(0xFFAB455E)
val PinkLight = Color(0xFFF29BAB)
val BiliBlue: Color @Composable get() = MaterialTheme.colorScheme.secondary
val PinkBg: Color @Composable get() = MaterialTheme.colorScheme.primaryContainer
val TextPrimary: Color @Composable get() = MaterialTheme.colorScheme.onSurface
val TextSecondary: Color @Composable get() = MaterialTheme.colorScheme.onSurfaceVariant
val TextHint: Color @Composable get() = MaterialTheme.colorScheme.outline
val BgGray: Color @Composable get() = MaterialTheme.colorScheme.background
val BgWhite: Color @Composable get() = MaterialTheme.colorScheme.surface
private val isDark: Boolean @Composable get() = MaterialTheme.colorScheme.surface.luminance() < .5f
val Success: Color @Composable get() = if (isDark) Color(0xFF8CD9A0) else Color(0xFF237736)
val SuccessBg: Color @Composable get() = if (isDark) Color(0xFF1B3526) else Color(0xFFE8F7EC)
val Warning: Color @Composable get() = if (isDark) Color(0xFFFFC26F) else Color(0xFF905400)
val WarningBg: Color @Composable get() = if (isDark) Color(0xFF3B301F) else Color(0xFFFFF3DD)
