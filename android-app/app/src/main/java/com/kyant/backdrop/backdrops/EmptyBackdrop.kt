/*
 * Copyright 2025 Kyant
 * SPDX-License-Identifier: Apache-2.0
 * Vendored from AndroidLiquidGlass 1.0.6, commit 896a94a3ade1cc1a940b92365f942a34971fecda.
 * Local compatibility changes are documented in android-app/third-party/Backdrop.md.
 */
package com.kyant.backdrop.backdrops

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.Density
import com.kyant.backdrop.Backdrop

@Stable
fun emptyBackdrop(): Backdrop = EmptyBackdrop

@Immutable
private object EmptyBackdrop : Backdrop {

    override val isCoordinatesDependent: Boolean = false

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?
    ) {
    }
}
