# AndroidLiquidGlass / Backdrop source integration

Upstream: https://github.com/Kyant0/AndroidLiquidGlass/tree/1.0.6
Pinned commit: 896a94a3ade1cc1a940b92365f942a34971fecda
Copyright 2025 Kyant. Apache-2.0. License is packaged in app/src/main/assets/licenses/AndroidLiquidGlass-Apache-2.0.txt.

This app vendors the complete 26-file Backdrop rendering source under app/src/main/java/com/kyant/backdrop, plus the LiquidBottomTabs renderer and DampedDragAnimation, DragGestureInspector and InteractiveHighlight under the app's ui/theme/glass package. It does not depend on a published AAR. The old local approximations are replaced by this implementation.

Compatibility changes for the existing Kotlin 2.0 / Compose 1.7 build:
- LayerRecorder's named context parameter becomes an explicit DelegatableNode argument; rendering and stable Density handling are retained.
- InverseLayerScope omits newer GraphicsLayerScope colorFilter/blendMode members that this app does not use.
- DrawBackdropNode defers effects until a positive measured size exists (Compose 1.7 throws on Size.Unspecified.minDimension); the first sized draw also registers effect observation so animated uniforms continue updating.
- Lens supports Compose CornerBasedShape (including CircleShape); the optional Kyant Shapes branch is omitted. Navigation uses CircleShape rather than an additional Shapes dependency.
- Int fastCoerceIn becomes standard coerceIn.
- The hidden navigation label replica receives the app accent through a CompositionLocal instead of the newer graphicsLayer colorFilter API. It has no clicks or accessibility nodes.
- Navigation follows the app's explicit dark/light preference and accent color. Selected Tab semantics remain on the visible row. Height expands for large system fonts.
- Selection callbacks use current state instead of a captured initial callback. Drag geometry is recreated after width/layout direction changes; pointer input keys follow each new animation/highlight instance.
- API 26–30 retains an opaque readability tint; upstream effects already gate blur at API 31 and AGSL at API 33.

The upstream shader mathematics, blur/lens/vibrancy chain, highlight/shadow rendering, combined page + magnified label input, pressure scaling and damped drag animation are retained. Do not regenerate this directory from a moving branch without reviewing these adaptations.
