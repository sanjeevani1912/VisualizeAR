package com.example.dhruvar.data.repository

import com.example.dhruvar.domain.model.Layout
import java.io.File
import java.util.UUID

/**
 * Keeps each signed-in user's plans apart on a shared phone.
 *
 * Folder layout under [root] (the app's private `dhruvar_layouts` folder):
 * - `<root>/<id>.json`                  "This device" plans: every plan saved before accounts were
 *                                      split, plus plans made while nobody is signed in. Shown to
 *                                      everyone. These files are never moved or deleted here.
 * - `<root>/users/<user id>/<id>.json` "My plans" of one account (server user id, not username, so a
 *                                      renamed user keeps their plans).
 *
 * Rules:
 * - Signed out: saves, renames and deletes act on device plans, exactly as before.
 * - Signed in: saves, renames and deletes act only on the user's own folder. A device plan is
 *   view-only; [copyToMine] makes an editable copy in the user's folder.
 */
class UserScopedLayoutRepository(private val root: File) : LayoutRepository {

    private val device = FileLayoutRepository(root)

    @Volatile
    private var userRepo: FileLayoutRepository? = null

    @Volatile
    var currentUserId: String? = null
        private set

    val isSignedIn: Boolean get() = currentUserId != null

    /** Switches whose "My plans" folder is used. Null = nobody signed in. */
    @Synchronized
    fun setUser(userId: String?) {
        val id = userId?.takeIf { it.isNotBlank() }
        if (id == currentUserId) return
        currentUserId = id
        userRepo = id?.let { FileLayoutRepository(userFolder(root, it)) }
    }

    suspend fun myLayouts(): List<Layout> = userRepo?.getAllLayouts() ?: emptyList()

    suspend fun deviceLayouts(): List<Layout> = device.getAllLayouts()

    /** True when [id] is a "This device" plan (a file directly in the root folder). */
    fun isDevicePlan(id: String): Boolean = File(root, "$id.json").isFile

    /** True when [id] may not be changed by whoever is using the app right now. */
    fun isViewOnly(id: String): Boolean = isSignedIn && isDevicePlan(id) && !isMine(id)

    private fun isMine(id: String): Boolean {
        val uid = currentUserId ?: return false
        return File(userFolder(root, uid), "$id.json").isFile
    }

    override suspend fun getAllLayouts(): List<Layout> =
        (myLayouts() + deviceLayouts()).sortedByDescending { it.updatedAtEpochMs }

    override suspend fun getLayoutById(id: String): Layout? =
        userRepo?.getLayoutById(id) ?: device.getLayoutById(id)

    override suspend fun saveLayout(layout: Layout): Boolean {
        val mine = userRepo ?: return device.saveLayout(layout)
        if (isViewOnly(layout.id)) return false
        return mine.saveLayout(layout)
    }

    override suspend fun deleteLayout(id: String): Boolean {
        val mine = userRepo ?: return device.deleteLayout(id)
        return mine.deleteLayout(id)
    }

    override suspend fun renameLayout(id: String, newName: String): Layout? {
        val mine = userRepo ?: return device.renameLayout(id, newName)
        return mine.renameLayout(id, newName)
    }

    /**
     * Copies a plan (normally a device plan) into the signed-in user's folder with a new id and the
     * name "<name> (copy)". The original file is not touched. Null when signed out or not found.
     */
    suspend fun copyToMine(id: String): Layout? {
        val mine = userRepo ?: return null
        val original = getLayoutById(id) ?: return null
        val now = System.currentTimeMillis()
        val copy = original.copy(
            id = UUID.randomUUID().toString(),
            name = "${original.name} (copy)",
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        return if (mine.saveLayout(copy)) copy else null
    }

    companion object {
        const val USERS_DIR = "users"

        /** `<root>/users/<id>`; characters that are unsafe in a folder name are replaced. */
        fun userFolder(root: File, userId: String): File {
            val safe = userId.replace(Regex("[^A-Za-z0-9_.-]"), "_")
                .let { if (it == "." || it == "..") "_$it" else it }
            return File(File(root, USERS_DIR), safe)
        }
    }
}
