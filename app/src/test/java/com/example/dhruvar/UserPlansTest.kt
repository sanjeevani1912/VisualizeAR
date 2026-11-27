package com.example.dhruvar

import com.example.dhruvar.data.model.LayoutJsonSerializer
import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.data.remote.SentPlanStore
import com.example.dhruvar.data.remote.StringPrefs
import com.example.dhruvar.data.repository.UserScopedLayoutRepository
import com.example.dhruvar.domain.location.FakeDeviceLocationController
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.viewmodel.PlanningViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Per-user plans on a shared phone: each account sees only its own plans plus the phone's
 * "This device" plans, which are view-only for signed-in users.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class UserPlansTest {

    private lateinit var root: File
    private lateinit var repo: UserScopedLayoutRepository

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        root = Files.createTempDirectory("dhruvar_user_plans").toFile()
        repo = UserScopedLayoutRepository(root)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        root.deleteRecursively()
    }

    private fun plan(id: String, name: String = "Plan $id") = Layout(id = id, name = name)

    /** A plan file left in dhruvar_layouts/ by an older version of the app. */
    private fun writeLegacy(id: String, name: String): File {
        val file = File(root, "$id.json")
        file.writeText(LayoutJsonSerializer.serialize(plan(id, name)), Charsets.UTF_8)
        return file
    }

    private fun viewModel() = PlanningViewModel(
        repository = repo,
        locationController = FakeDeviceLocationController(initialPermissionGranted = true)
    )

    // ---------- storage ----------

    @Test
    fun userA_plans_never_appear_for_userB() = runBlocking {
        repo.setUser("101")
        assertTrue(repo.saveLayout(plan("a-1")))
        repo.setUser("202")
        assertTrue(repo.saveLayout(plan("b-1")))

        assertEquals(listOf("b-1"), repo.myLayouts().map { it.id })
        assertTrue(repo.getAllLayouts().none { it.id == "a-1" })
        assertNull(repo.getLayoutById("a-1"))

        repo.setUser("101")
        assertEquals(listOf("a-1"), repo.myLayouts().map { it.id })
        assertTrue(repo.getAllLayouts().none { it.id == "b-1" })
    }

    @Test
    fun plans_are_stored_under_the_server_user_id() = runBlocking {
        repo.setUser("101")
        repo.saveLayout(plan("a-1"))
        assertTrue(File(root, "users/101/a-1.json").isFile)
        assertFalse(File(root, "a-1.json").exists())
    }

    @Test
    fun signOut_empties_my_plans() = runBlocking {
        repo.setUser("101")
        repo.saveLayout(plan("a-1"))
        repo.setUser(null)
        assertTrue(repo.myLayouts().isEmpty())
        assertTrue(repo.getAllLayouts().none { it.id == "a-1" })
    }

    @Test
    fun device_plans_are_visible_to_everyone() = runBlocking {
        writeLegacy("legacy-1", "Old plan")
        repo.saveLayout(plan("dev-2")) // made while signed out

        for (user in listOf(null, "101", "202")) {
            repo.setUser(user)
            val ids = repo.deviceLayouts().map { it.id }.toSet()
            assertEquals("user $user", setOf("legacy-1", "dev-2"), ids)
        }
    }

    @Test
    fun device_plan_is_view_only_when_signed_in_and_editable_when_signed_out() = runBlocking {
        writeLegacy("legacy-1", "Old plan")
        repo.setUser("101")
        assertTrue(repo.isViewOnly("legacy-1"))
        assertFalse(repo.saveLayout(plan("legacy-1", "changed")))
        assertNull(repo.renameLayout("legacy-1", "changed"))
        assertFalse(repo.deleteLayout("legacy-1"))
        assertEquals("Old plan", repo.getLayoutById("legacy-1")?.name)

        repo.setUser(null)
        assertFalse(repo.isViewOnly("legacy-1"))
        assertEquals("Renamed", repo.renameLayout("legacy-1", "Renamed")?.name)
        assertTrue(repo.deleteLayout("legacy-1"))
    }

    @Test
    fun copy_creates_new_id_in_user_folder_and_leaves_original() = runBlocking {
        val legacy = writeLegacy("legacy-1", "Ridge camp")
        val before = legacy.readBytes()
        repo.setUser("101")

        val copy = repo.copyToMine("legacy-1")
        assertNotNull(copy)
        copy!!
        assertNotEquals("legacy-1", copy.id)
        assertEquals("Ridge camp (copy)", copy.name)
        assertTrue(File(root, "users/101/${copy.id}.json").isFile)
        assertFalse(File(root, "${copy.id}.json").exists())
        assertArrayEquals(before, legacy.readBytes())
        assertFalse(repo.isViewOnly(copy.id))
        assertEquals(listOf(copy.id), repo.myLayouts().map { it.id })
    }

    @Test
    fun copy_needs_a_signed_in_user() = runBlocking {
        writeLegacy("legacy-1", "Ridge camp")
        assertNull(repo.copyToMine("legacy-1"))
    }

    @Test
    fun legacy_files_stay_where_they_are() = runBlocking {
        val legacy = writeLegacy("legacy-1", "Old plan")
        val before = legacy.readBytes()

        repo.setUser("101")
        repo.saveLayout(plan("a-1"))
        repo.copyToMine("legacy-1")
        repo.getAllLayouts()
        repo.setUser(null)
        repo.getAllLayouts()

        assertTrue(legacy.isFile)
        assertArrayEquals(before, legacy.readBytes())
        val movedSomewhere = File(root, "users").walk().any { it.name == "legacy-1.json" }
        assertFalse(movedSomewhere)
    }

    @Test
    fun odd_user_ids_stay_inside_the_users_folder() {
        val folder = UserScopedLayoutRepository.userFolder(root, "../../evil")
        assertEquals(File(root, "users").canonicalPath, folder.parentFile!!.canonicalPath)
    }

    // ---------- sent-plan records ----------

    private class MapPrefs : StringPrefs {
        val map = HashMap<String, String>()
        override fun all(): Map<String, *> = map
        override fun put(key: String, value: String) {
            map[key] = value
        }
    }

    @Test
    fun sent_plan_records_are_per_user() {
        assertNotEquals(SentPlanStore.prefsNameFor("101"), SentPlanStore.prefsNameFor("202"))
        assertNotEquals("dhruvar_sent_plans", SentPlanStore.prefsNameFor("101"))

        // One backing file per user, as the Android constructor does.
        val files = HashMap<String, MapPrefs>()
        fun storeFor(user: String) = SentPlanStore(files.getOrPut(SentPlanStore.prefsNameFor(user)) { MapPrefs() })

        storeFor("101").put("a-1", SentPlan("p1", "PLN-1", 1, "SUBMITTED", 5L))
        assertEquals("PLN-1", storeFor("101").all()["a-1"]?.code)
        assertTrue(storeFor("202").all().isEmpty())
    }

    @Test
    fun signed_out_has_no_sent_records() {
        val signedOut = SentPlanStore(prefs = null)
        signedOut.put("a-1", SentPlan("p1", "PLN-1", 1, "SUBMITTED", 5L))
        assertTrue(signedOut.all().isEmpty())
    }

    // ---------- view model ----------

    @Test
    fun viewModel_sections_and_signOut_clear_the_list() = runBlocking {
        writeLegacy("legacy-1", "Old plan")
        val vm = viewModel()
        vm.onAccountChanged("101")
        vm.createNewLayout("Mine")
        assertTrue(vm.saveCurrentLayoutDirect())
        vm.loadSavedLayoutsDirect()

        val state = vm.uiState.value
        assertEquals(listOf("Mine"), state.myPlans.map { it.name })
        assertEquals(listOf("legacy-1"), state.devicePlans.map { it.id })

        vm.onAccountChanged(null)
        val after = vm.uiState.value
        assertNull(after.signedInUserId)
        assertTrue(after.myPlans.isEmpty())
        assertFalse(after.isPersisted)
        assertNotEquals("Mine", after.currentLayout.name)
        vm.loadSavedLayoutsDirect()
        assertEquals(listOf("legacy-1"), vm.uiState.value.savedLayouts.map { it.id })
    }

    @Test
    fun viewModel_device_plan_is_view_only_for_signed_in_user() = runBlocking {
        writeLegacy("legacy-1", "Old plan")
        val vm = viewModel()
        vm.onAccountChanged("101")
        vm.loadSavedLayoutsDirect()
        vm.loadLayout(vm.uiState.value.devicePlans.single())

        assertTrue(vm.uiState.value.isViewOnly)
        vm.addAssetAt(1f, 1f)
        assertTrue(vm.uiState.value.currentLayout.objects.isEmpty())
        assertFalse(vm.saveCurrentLayoutDirect("changed"))
        assertNull(vm.renameLayoutDirect("legacy-1", "changed"))
        assertFalse(vm.deleteLayoutDirect("legacy-1"))

        // Make a copy: the editable copy opens, the original stays a device plan.
        val copy = vm.copyToMyPlansDirect("legacy-1")
        assertNotNull(copy)
        assertFalse(vm.uiState.value.isViewOnly)
        assertEquals(copy!!.id, vm.uiState.value.currentLayout.id)
        assertEquals(listOf(copy.id), vm.uiState.value.myPlans.map { it.id })
        assertEquals(listOf("legacy-1"), vm.uiState.value.devicePlans.map { it.id })
        vm.addAssetAt(1f, 1f)
        assertEquals(1, vm.uiState.value.currentLayout.objects.size)
        assertTrue(vm.saveCurrentLayoutDirect())
    }

    @Test
    fun viewModel_device_plan_is_editable_when_signed_out() = runBlocking {
        writeLegacy("legacy-1", "Old plan")
        val vm = viewModel()
        vm.onAccountChanged(null)
        vm.loadSavedLayoutsDirect()
        vm.loadLayout(vm.uiState.value.devicePlans.single())

        assertFalse(vm.uiState.value.isViewOnly)
        vm.addAssetAt(1f, 1f)
        assertTrue(vm.saveCurrentLayoutDirect())
        assertTrue(File(root, "legacy-1.json").isFile)
        assertEquals(1, repo.getLayoutById("legacy-1")?.objects?.size)
    }
}
