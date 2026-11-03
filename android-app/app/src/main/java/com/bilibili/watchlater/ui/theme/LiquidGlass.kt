package com.bilibili.watchlater.ui.theme

import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.backdrops.LayerBackdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy

@Immutable
data class GlassStyle(val enabled: Boolean = false, val dark: Boolean = false)
val LocalGlassStyle = staticCompositionLocalOf { GlassStyle() }
val LocalPageInsets = staticCompositionLocalOf<PaddingValues> { PaddingValues(0.dp) }
internal val LocalPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

val AppBackgroundColor: Color
    @Composable get() = MaterialTheme.colorScheme.background

/** Record only the page. The navigation and its magnified label copy stay outside this layer. */
@Composable
fun AppBackdrop(content: @Composable () -> Unit) {
    val base = MaterialTheme.colorScheme.background
    val page = rememberLayerBackdrop(onDraw = { drawRect(base); drawContent() })
    Box(Modifier.fillMaxSize().background(base)) {
        CompositionLocalProvider(LocalPageBackdrop provides page, content = content)
    }
}

@Composable
fun Modifier.captureGlassBackdrop(): Modifier {
    val page = LocalPageBackdrop.current ?: return this
    return if (LocalGlassStyle.current.enabled) layerBackdrop(page) else this
}

/** Optical diagnostic only; production glass is confined to GlassNavigationBar. */
@Composable
fun Modifier.liquidGlass(shape: Shape = RoundedCornerShape(32.dp)): Modifier {
    val page = LocalPageBackdrop.current ?: return this
    if (!LocalGlassStyle.current.enabled) return this
    val color = MaterialTheme.colorScheme.surface.copy(alpha = if (Build.VERSION.SDK_INT >= 31) .4f else .94f)
    return drawBackdrop(page, { shape }, effects = {
        vibrancy(); blur(8.dp.toPx()); lens(24.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
    }, onDrawSurface = { drawRect(color) })
}

@Composable
fun AppSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(18.dp),
    color: Color = MaterialTheme.colorScheme.surface,
    border: BorderStroke? = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    shadowElevation: Dp = 0.dp,
    content: @Composable () -> Unit
) {
    Surface(modifier, shape = shape, color = color, contentColor = contentColorFor(color),
        border = border, shadowElevation = shadowElevation, content = content)
}
