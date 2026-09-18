package com.example.dhruvar

import com.example.dhruvar.data.model.LayoutJsonSerializer
import com.example.dhruvar.data.repository.FileLayoutRepository
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.viewmodel.PlanningViewModel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.UUID

/**
 * Comprehensive test suite validating Part 8: Local Layout Persistence.
 * Covers JSON serialization, atomic file operations, corruption resilience, and ViewModel state tracking.
 */
class LayoutPersistenceTest {

    private lateinit var tempDir: File
    private lateinit var repository: FileLayoutRepository

    @Before
    fun setUp() {
        tempDir = Files.createTempDirectory("dhruvar_test_layouts").toFile()
        repository = FileLayoutRepository(tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun saveLayout_and_getLayoutById_roundTripMatches() = runBlocking {
        val originalLayout = Layout(
            id = UUID.randomUUID().toString(),
            name = "Forward Operating Base Echo",
            anchor = Anchor(
                id = "anchor-1",
                label = "Datum Alpha",
                x = 0f,
                z = 0f,
                description = "Primary center"
            ),
            objects = listOf(
                LayoutObject(
                    id = "obj-1",
                    assetType = AssetType.TENT,
                    name = "HQ Tent",
                    x = 4.5f,
                    z = -3.2f,
                    rotationDegrees = 45f,
                    widthMeters = 5.0f,
                    lengthMeters = 7.0f,
                    heightMeters = 3.0f,
                    scale = 1.0f
                ),
                LayoutObject(
                    id = "obj-2",
                    assetType = AssetType.ANTENNA,
                    name = "Comms Mast",
                    x = -8.0f,
                    z = 6.0f,
                    rotationDegrees = 270f,
                    widthMeters = 2.0f,
                    lengthMeters = 2.0f,
                    heightMeters = 12.0f,
                    scale = 1.0f
                )
            ),
            createdAtEpochMs = 1000L,
            updatedAtEpochMs = 2000L
        )

        val saveResult = repository.saveLayout(originalLayout)
        assertTrue(saveResult)

        val loaded = repository.getLayoutById(originalLayout.id)
        assertNotNull(loaded)
        assertEquals(originalLayout.id, loaded!!.id)
        assertEquals("Forward Operating Base Echo", loaded.name)
        assertEquals(originalLayout.createdAtEpochMs, loaded.createdAtEpochMs)
        assertEquals(originalLayout.updatedAtEpochMs, loaded.updatedAtEpochMs)

        // Validate Anchor
        assertEquals("Datum Alpha", loaded.anchor.label)
        assertEquals(0f, loaded.anchor.x, 0.001f)
        assertEquals(0f, loaded.anchor.z, 0.001f)

        // Validate Objects
        assertEquals(2, loaded.objects.size)
        val hqTent = loaded.objects.first { it.id == "obj-1" }
        assertEquals(AssetType.TENT, hqTent.assetType)
        assertEquals("HQ Tent", hqTent.name)
        assertEquals(4.5f, hqTent.x, 0.001f)
        assertEquals(-3.2f, hqTent.z, 0.001f)
        assertEquals(45f, hqTent.rotationDegrees, 0.001f)
        assertEquals(5.0f, hqTent.widthMeters, 0.001f)
        assertEquals(7.0f, hqTent.lengthMeters, 0.001f)
        assertEquals(3.0f, hqTent.heightMeters, 0.001f)

        val commsMast = loaded.objects.first { it.id == "obj-2" }
        assertEquals(AssetType.ANTENNA, commsMast.assetType)
        assertEquals(270f, commsMast.rotationDegrees, 0.001f)
    }

    @Test
    fun getAllLayouts_returnsPersistedLayoutsSortedByUpdatedAt() = runBlocking {
        val layoutOld = Layout(
            id = "layout-old",
            name = "Old Plan",
            updatedAtEpochMs = 1000L
        )
        val layoutNew = Layout(
            id = "layout-new",
            name = "New Plan",
            updatedAtEpochMs = 5000L
        )
        val layoutMid = Layout(
            id = "layout-mid",
            name = "Mid Plan",
            updatedAtEpochMs = 3000L
        )

        repository.saveLayout(layoutOld)
        repository.saveLayout(layoutNew)
        repository.saveLayout(layoutMid)

        val all = repository.getAllLayouts()
        assertEquals(3, all.size)
        assertEquals("layout-new", all[0].id)
        assertEquals("layout-mid", all[1].id)
        assertEquals("layout-old", all[2].id)
    }

    @Test
    fun updateExistingLayout_preservesIdAndOverwritesFile() = runBlocking {
        val layout = Layout(
            id = "fixed-id",
            name = "Initial Draft",
            objects = listOf(
                LayoutObject(
                    id = "obj-initial",
                    assetType = AssetType.CAR,
                    x = 1f,
                    z = 1f
                )
            ),
            updatedAtEpochMs = 1000L
        )
        repository.saveLayout(layout)

        val updatedLayout = layout.copy(
            name = "Revised Deployment",
            objects = layout.objects + LayoutObject(
                id = "obj-added",
                assetType = AssetType.TRUCK,
                x = 5f,
                z = 5f
            ),
            updatedAtEpochMs = 2000L
        )
        repository.saveLayout(updatedLayout)

        val all = repository.getAllLayouts()
        assertEquals(1, all.size) // No duplicate files

        val loaded = repository.getLayoutById("fixed-id")
        assertNotNull(loaded)
        assertEquals("Revised Deployment", loaded!!.name)
        assertEquals(2, loaded.objects.size)
        assertEquals(2000L, loaded.updatedAtEpochMs)
    }

    @Test
    fun deleteLayout_removesFileAndReturnsTrue() = runBlocking {
        val layout = Layout(id = "delete-me", name = "Temporary Plan")
        repository.saveLayout(layout)
        assertNotNull(repository.getLayoutById("delete-me"))

        val deleteSuccess = repository.deleteLayout("delete-me")
        assertTrue(deleteSuccess)
        assertNull(repository.getLayoutById("delete-me"))
        assertTrue(repository.getAllLayouts().isEmpty())

        // Deleting non-existent returns false without crashing
        assertFalse(repository.deleteLayout("non-existent"))
    }

    @Test
    fun renameLayout_preservesObjectsDimensionsAndId() = runBlocking {
        val layout = Layout(
            id = "rename-target",
            name = "Old Designation",
            objects = listOf(
                LayoutObject(
                    id = "o1",
                    assetType = AssetType.TRENCH,
                    widthMeters = 1.2f,
                    lengthMeters = 8.0f
                )
            )
        )
        repository.saveLayout(layout)

        val renamed = repository.renameLayout("rename-target", "Tactical Trench Bravo")
        assertNotNull(renamed)
        assertEquals("rename-target", renamed!!.id)
        assertEquals("Tactical Trench Bravo", renamed.name)
        assertEquals(1, renamed.objects.size)
        assertEquals(1.2f, renamed.objects[0].widthMeters, 0.001f)

        val retrieved = repository.getLayoutById("rename-target")
        assertEquals("Tactical Trench Bravo", retrieved!!.name)
    }

    @Test
    fun serializer_gracefullyRecoversCorruptedOrMissingFields() {
        // Corrupted JSON missing objects array, anchor x/z as strings or NaN, unrecognized assetType
        val malformedJson = """
            {
                "version": 1,
                "id": "corrupted-test",
                "name": "Fallback Test",
                "anchor": {
                    "label": "Custom Anchor"
                },
                "objects": [
                    {
                        "id": "bad-obj",
                        "assetType": "FUTURE_DRONE_UNKNOWN",
                        "x": 3.5,
                        "widthMeters": -10.0,
                        "lengthMeters": 0.0
                    }
                ]
            }
        """.trimIndent()

        val parsed = LayoutJsonSerializer.deserialize(malformedJson)
        assertEquals("corrupted-test", parsed.id)
        assertEquals("Fallback Test", parsed.name)
        assertEquals("Custom Anchor", parsed.anchor.label)
        assertEquals(0f, parsed.anchor.x, 0.001f)
        assertEquals(0f, parsed.anchor.z, 0.001f)

        assertEquals(1, parsed.objects.size)
        val recoveredObj = parsed.objects[0]
        // Falls back safely to TENT when assetType is unknown
        assertEquals(AssetType.TENT, recoveredObj.assetType)
        assertEquals(3.5f, recoveredObj.x, 0.001f)
        assertEquals(0f, recoveredObj.z, 0.001f) // Missing z default
        // Non-positive dimensions fall back to default specification
        assertEquals(AssetType.TENT.defaultWidthMeters, recoveredObj.widthMeters, 0.001f)
        assertEquals(AssetType.TENT.defaultLengthMeters, recoveredObj.lengthMeters, 0.001f)
    }

    @Test
    fun repository_skipsCorruptedFiles_withoutCrashingGetAll() = runBlocking {
        // Save 1 valid layout
        val validLayout = Layout(id = "valid-plan", name = "Valid Plan")
        repository.saveLayout(validLayout)

        // Manually place a completely corrupted text file with .json extension
        val badFile = File(tempDir, "corrupted.json")
        badFile.writeText("THIS IS NOT VALID JSON {{{{", Charsets.UTF_8)

        val all = repository.getAllLayouts()
        assertEquals(1, all.size)
        assertEquals("valid-plan", all[0].id)
    }

    @Test
    fun planningViewModel_tracksUnsavedChangesAndPersistenceState() = runBlocking {
        val viewModel = PlanningViewModel(repository)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertFalse(viewModel.uiState.value.isPersisted)

        // Adding an asset marks unsaved changes
        viewModel.addAssetAt(2.0f, 2.0f)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Saving layout clears unsaved changes and marks persisted
        val saved = viewModel.saveCurrentLayoutDirect("Field Deployment 1")
        assertTrue(saved)
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)
        assertTrue(viewModel.uiState.value.isPersisted)
        assertEquals("Field Deployment 1", viewModel.uiState.value.currentLayout.name)

        // Moving an object marks unsaved changes again
        val objId = viewModel.uiState.value.currentLayout.objects.first().id
        viewModel.moveObject(objId, 10f, 10f)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Saving again clears unsaved changes
        viewModel.saveCurrentLayoutDirect()
        assertFalse(viewModel.uiState.value.hasUnsavedChanges)

        // Rotating marks unsaved changes
        viewModel.rotateObject(objId, 90f)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Updating dimensions marks unsaved changes
        viewModel.updateObjectDimensions(objId, 5f, 5f)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)

        // Deleting marks unsaved changes
        viewModel.deleteObject(objId)
        assertTrue(viewModel.uiState.value.hasUnsavedChanges)
    }

    @Test
    fun planningViewModel_loadLayout_setsPersistedAndClearsUnsaved() = runBlocking {
        val savedLayout = Layout(
            id = "load-test-id",
            name = "Persisted Tactical Plan",
            objects = listOf(
                LayoutObject(
                    id = "t1",
                    assetType = AssetType.TENT,
                    x = 3f,
                    z = 3f,
                    isSelected = true // was selected when saved
                )
            )
        )
        repository.saveLayout(savedLayout)

        val viewModel = PlanningViewModel(repository)
        viewModel.loadLayout(savedLayout)

        val state = viewModel.uiState.value
        assertEquals("load-test-id", state.currentLayout.id)
        assertEquals("Persisted Tactical Plan", state.currentLayout.name)
        assertTrue(state.isPersisted)
        assertFalse(state.hasUnsavedChanges)
        assertNull(state.selectedObjectId)
        assertFalse(state.currentLayout.objects.first().isSelected) // Selection reset on load
    }

    @Test
    fun emptyLayout_persistsAndLoadsCleanly() = runBlocking {
        val emptyLayout = Layout(
            id = "empty-plan",
            name = "Empty Canvas Layout",
            objects = emptyList()
        )
        val success = repository.saveLayout(emptyLayout)
        assertTrue(success)

        val loaded = repository.getLayoutById("empty-plan")
        assertNotNull(loaded)
        assertTrue(loaded!!.objects.isEmpty())
        assertEquals("Empty Canvas Layout", loaded.name)
    }
}
