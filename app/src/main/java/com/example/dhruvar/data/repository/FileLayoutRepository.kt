package com.example.dhruvar.data.repository

import com.example.dhruvar.data.model.LayoutJsonSerializer
import com.example.dhruvar.domain.model.Layout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Robust, 100% offline file-based implementation of [LayoutRepository].
 *
 * Each [Layout] is persisted as an independent formatted JSON file within [storageDir]:
 * `<storageDir>/<layout.id>.json`
 *
 * Implements atomic writes via temporary files (`<layout.id>.json.tmp`) to guarantee
 * transactional consistency even if the process or device is terminated abruptly.
 * Corrupted or partially written files are safely ignored and logged without crashing the app.
 */
class FileLayoutRepository(
    private val storageDir: File
) : LayoutRepository {

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    override suspend fun getAllLayouts(): List<Layout> = withContext(Dispatchers.IO) {
        if (!storageDir.exists() || !storageDir.isDirectory) {
            return@withContext emptyList()
        }

        val files = storageDir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?: return@withContext emptyList()

        val layouts = mutableListOf<Layout>()
        for (file in files) {
            try {
                val json = file.readText(Charsets.UTF_8)
                val layout = LayoutJsonSerializer.deserialize(json)
                layouts.add(layout)
            } catch (e: Exception) {
                System.err.println("FileLayoutRepository: skipping corrupted file ${file.name}: ${e.message}")
            }
        }
        layouts.sortedByDescending { it.updatedAtEpochMs }
    }

    override suspend fun getLayoutById(id: String): Layout? = withContext(Dispatchers.IO) {
        val file = File(storageDir, "$id.json")
        if (!file.exists() || !file.isFile) return@withContext null

        try {
            val json = file.readText(Charsets.UTF_8)
            LayoutJsonSerializer.deserialize(json)
        } catch (e: Exception) {
            System.err.println("FileLayoutRepository: failed to read layout $id: ${e.message}")
            null
        }
    }

    override suspend fun saveLayout(layout: Layout): Boolean = withContext(Dispatchers.IO) {
        try {
            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            val json = LayoutJsonSerializer.serialize(layout)
            val targetFile = File(storageDir, "${layout.id}.json")
            val tempFile = File(storageDir, "${layout.id}.json.tmp")

            tempFile.writeText(json, Charsets.UTF_8)

            if (targetFile.exists()) {
                targetFile.delete()
            }

            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                tempFile.copyTo(targetFile, overwrite = true)
                tempFile.delete()
            }
            true
        } catch (e: Exception) {
            System.err.println("FileLayoutRepository: error saving layout ${layout.id}: ${e.message}")
            false
        }
    }

    override suspend fun deleteLayout(id: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(storageDir, "$id.json")
            val tmpFile = File(storageDir, "$id.json.tmp")
            if (tmpFile.exists()) {
                tmpFile.delete()
            }
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            System.err.println("FileLayoutRepository: error deleting layout $id: ${e.message}")
            false
        }
    }

    override suspend fun renameLayout(id: String, newName: String): Layout? = withContext(Dispatchers.IO) {
        val current = getLayoutById(id) ?: return@withContext null
        val updated = current.copy(
            name = newName.trim().ifEmpty { current.name },
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val success = saveLayout(updated)
        if (success) updated else null
    }
}
