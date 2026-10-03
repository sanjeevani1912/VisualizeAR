package com.example.dhruvar.ui.theme

import androidx.compose.ui.graphics.Color

// Field Kit palette — locked by stakeholder for military day-ops use.
// See docs/prompts/ux-field-kit/00-UX-SHARED.md. Do not reintroduce blue/slate dark HUD glass.
val Cacao = Color(0xFF603620) // deep brown — secondary surfaces / borders
val Coal = Color(0xFF231F20) // near-black — primary text / icons on light
val GoldenHour = Color(0xFFF3E4BD) // warm sand — elevated panels / chips
val Rust = Color(0xFFB56B37) // terracotta — warnings / attention / secondary CTA
val Mist = Color(0xFFFCF9F2) // off-white — app backgrounds
val Moss = Color(0xFF63703D) // olive — primary actions / success / selected
val Meadow = Color(0xFFB5C37C) // sage — secondary accents / region fill / soft highlights

// Semantic aliases. Prefer MaterialTheme.colorScheme in composables; these exist for the places
// that cannot reach it — map layers, canvas drawing, and GL renderers.
val FieldBackground = Mist
val FieldSurface = GoldenHour
val FieldOnSurface = Coal
val FieldPrimary = Moss
val FieldWarning = Rust
val FieldSuccess = Moss
val FieldAccent = Meadow
val FieldBorder = Cacao

/**
 * Origin / anchor emphasis. Rust reads as "this is the one fixed reference point" against both
 * Meadow region fills and OSM basemap greens, which Cacao does not at small marker sizes.
 */
val FieldAnchor = Rust

// Day-ops overlay recipe. Panels sit on vector tiles and on the AR camera feed, so they need
// near-opaque fill plus a thin border rather than the translucent dark glass they replace.
// Spelled out as constants because Canvas draw scopes, MapLibre layers and GL renderers cannot
// reach MaterialTheme.colorScheme — composables should still prefer the scheme.
val OverlayFill = GoldenHour.copy(alpha = 0.95f)
val OverlayFillStrong = GoldenHour.copy(alpha = 0.97f)
val OverlayBorder = Cacao.copy(alpha = 0.45f)
val OverlayHairline = Cacao.copy(alpha = 0.20f)

/** Secondary text and icons on an overlay panel. */
val OverlayMuted = Cacao
val OverlayFaint = Cacao.copy(alpha = 0.40f)

/** Dim the camera feed behind a control without going dark-mode. */
val OverlayScrim = Coal.copy(alpha = 0.30f)

// Metric grid canvas
val GridLine = Cacao.copy(alpha = 0.16f)
val GridAxis = Cacao.copy(alpha = 0.38f)
