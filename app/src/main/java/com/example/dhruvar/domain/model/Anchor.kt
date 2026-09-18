package com.example.dhruvar.domain.model

import java.util.UUID

/**
 * Represents the central spatial reference point for all objects within a Layout.
 *
 * In 2D planning, the anchor corresponds to the origin (x=0, z=0) of the field coordinate system.
 * In AR visualization, this anchor maps directly to a detected real-world ARCore Anchor,
 * allowing all layout objects to be projected into the physical world with exact offsets.
 */
data class Anchor(
    val id: String = UUID.randomUUID().toString(),
    val label: String = "Origin Anchor",
    val x: Float = 0.0f, // meters
    val z: Float = 0.0f, // meters
    val description: String = "Primary deployment reference datum"
)
