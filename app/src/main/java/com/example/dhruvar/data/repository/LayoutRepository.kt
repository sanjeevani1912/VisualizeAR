package com.example.dhruvar.data.repository

import com.example.dhruvar.domain.model.Layout

/**
 * Interface defining local persistence operations for tactical planning [Layout] documents.
 * Ensures 100% offline persistence with zero cloud dependencies.
 */
interface LayoutRepository {

    /**
     * Retrieves all saved layouts, sorted by last updated timestamp descending.
     */
    suspend fun getAllLayouts(): List<Layout>

    /**
     * Retrieves a single layout by its unique [id], or null if not found.
     */
    suspend fun getLayoutById(id: String): Layout?

    /**
     * Saves or updates a layout locally.
     * If the layout already exists (same id), it updates the record atomically.
     * If it is a new layout, it persists a new record.
     * Returns true if successful, false otherwise.
     */
    suspend fun saveLayout(layout: Layout): Boolean

    /**
     * Deletes the layout with the given [id].
     * Returns true if deleted, false if not found or deletion failed.
     */
    suspend fun deleteLayout(id: String): Boolean

    /**
     * Renames an existing layout by [id], preserving all objects, anchors, and dimensions.
     * Returns the updated [Layout], or null if not found or rename failed.
     */
    suspend fun renameLayout(id: String, newName: String): Layout?
}
