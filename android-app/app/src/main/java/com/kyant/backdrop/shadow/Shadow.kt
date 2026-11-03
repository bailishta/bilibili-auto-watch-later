/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 * Vendored from AndroidLiquidGlass 1.0.6, commit 896a94a3ade1cc1a940b92365f942a34971fecda.
 * Local compatibility changes are documented in android-app/third-party/Backdrop.md.
 */
package com.kyant.backdrop.shadow

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

@Immutable
data class Shadow(
    val radius: Dp = 24f.dp,
    val offset: DpOffset = DpOffset(0f.dp, radius / 6f),
    val color: Color = Color.Black.copy(alpha = 0.1f),
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val blendMode: BlendMode = DrawScope.DefaultBlendMode
) {

    companion object {

        @Stable
        val Default: Shadow = Shadow()
    }
}
