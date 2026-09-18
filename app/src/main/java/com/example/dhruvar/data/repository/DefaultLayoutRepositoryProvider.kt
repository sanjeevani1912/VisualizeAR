package com.example.dhruvar.data.repository

import java.io.File

/**
 * Global provider for the active [LayoutRepository].
 * Initialized during [com.example.dhruvar.MainActivity.onCreate] with the application files directory.
 * Provides an in-memory fallback for headless or test environments where [init] was not called.
 */
object DefaultLayoutRepositoryProvider {

    @Volatile
    private var instance: LayoutRepository? = null

    /**
     * Initializes the provider with a file-backed repository in [storageDir].
     */
    fun init(storageDir: File) {
        instance = FileLayoutRepository(storageDir)
    }

    /**
     * Explicitly sets a custom repository (e.g., mock or test repository).
     */
    fun setRepository(repository: LayoutRepository) {
        instance = repository
    }

    /**
     * Retrieves the active repository. If not yet initialized, returns an [InMemoryLayoutRepository].
     */
    fun getRepository(): LayoutRepository {
        return instance ?: synchronized(this) {
            instance ?: InMemoryLayoutRepository().also { instance = it }
        }
    }

    /**
     * Resets the repository instance (primarily for test teardown).
     */
    fun reset() {
        instance = null
    }
}
