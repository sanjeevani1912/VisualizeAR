package com.example.dhruvar.domain.model

import java.util.UUID

/**
 * Represents a complete field planning document containing an [Anchor] and an arbitrary
 * collection of positioned [LayoutObject] entities.
 *
 * This data structure serves as the canonical contract shared between:
 * 1. The 2D Top-Down Planning Screen (Canvas editor)
 * 2. The 3D ARCore Visualization Screen (Physical world renderer)
 */
data class Layout(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Tactical Field Alpha",
    val anchor: Anchor = Anchor(),
    val objects: List<LayoutObject> = emptyList(),
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)
