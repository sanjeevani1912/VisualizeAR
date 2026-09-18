package com.example.dhruvar.data.repository

import com.example.dhruvar.domain.model.Layout
import java.util.concurrent.ConcurrentHashMap

/**
 * In-memory fallback implementation of [LayoutRepository] for tests and zero-arg ViewModel setups.
 */
class InMemoryLayoutRepository : LayoutRepository {
    private val layouts = ConcurrentHashMap<String, Layout>()

    override suspend fun getAllLayouts(): List<Layout> {
        return layouts.values.sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun getLayoutById(id: String): Layout? {
        return layouts[id]
    }

    override suspend fun saveLayout(layout: Layout): Boolean {
        layouts[layout.id] = layout
        return true
    }

    override suspend fun deleteLayout(id: String): Boolean {
        return layouts.remove(id) != null
    }

    override suspend fun renameLayout(id: String, newName: String): Layout? {
        val current = layouts[id] ?: return null
        val updated = current.copy(
            name = newName.trim().ifEmpty { current.name },
            updatedAtEpochMs = System.currentTimeMillis()
        )
        layouts[id] = updated
        return updated
    }
}
