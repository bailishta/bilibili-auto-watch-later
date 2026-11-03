/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 * Vendored from AndroidLiquidGlass 1.0.6, commit 896a94a3ade1cc1a940b92365f942a34971fecda.
 * Local compatibility changes are documented in android-app/third-party/Backdrop.md.
 */
package com.kyant.backdrop.highlight

import androidx.annotation.FloatRange
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Highlight(
    val width: Dp = 0.5f.dp,
    val blurRadius: Dp = width / 2f,
    @param:FloatRange(from = 0.0, to = 1.0) val alpha: Float = 1f,
    val style: HighlightStyle = HighlightStyle.Default
) {

    companion object {

        @Stable
        val Default: Highlight = Highlight()

        @Stable
        val Ambient: Highlight = Highlight(style = HighlightStyle.Ambient)

        @Stable
        val Plain: Highlight = Highlight(style = HighlightStyle.Plain)
    }
}
