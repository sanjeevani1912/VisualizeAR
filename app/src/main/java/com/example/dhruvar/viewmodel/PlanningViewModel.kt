package com.example.dhruvar.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.data.repository.LayoutRepository
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * UI State for the 2D Top-Down Planning Canvas and persistence lifecycle.
 */
data class PlanningUiState(
    val currentLayout: Layout = Layout(),
    val selectedAssetType: AssetType = AssetType.TENT,
    val selectedObjectId: String? = null,
    val gridScaleMeters: Float = 1.0f,
    // Part 2: Canvas Viewport Interactive State
    val panOffset: Offset = Offset.Zero,
    val zoom: Float = CoordinateTransformer.DEFAULT_ZOOM,
    val cursorCoordinatesMeters: Pair<Float, Float>? = null,
    val basePixelsPerMeter: Float = 28.0f,
    // Part 6: Spatial Distance Measurement State
    val showDistances: Boolean = true,
    // Part 8: Local Layout Persistence State
    val hasUnsavedChanges: Boolean = false,
    val isPersisted: Boolean = false,
    val savedLayouts: List<Layout> = emptyList(),
    val lastSavedTimestamp: Long? = null,
    val userFeedbackMessage: String? = null,
    val isLoadingSavedLayouts: Boolean = false
) {
    /**
     * Currently selected [LayoutObject], or null if no object is selected.
     */
    val selectedObject: LayoutObject?
        get() = currentLayout.objects.firstOrNull { it.id == selectedObjectId }
}

/**
 * ViewModel managing the planning workspace state.
 * Maintains the source of truth for the active [Layout], viewport transformations,
 * selected tools, object modifications, and local file persistence.
 */
class PlanningViewModel(
    private val repository: LayoutRepository = DefaultLayoutRepositoryProvider.getRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PlanningUiState())
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

    // ==========================================
    // PERSISTENCE OPERATIONS (Part 8)
    // ==========================================

    /**
     * Directly loads all saved layouts synchronously or from test coroutines.
     */
    suspend fun loadSavedLayoutsDirect(): List<Layout> {
        val layouts = repository.getAllLayouts()
        _uiState.update { it.copy(savedLayouts = layouts) }
        return layouts
    }

    /**
     * Asynchronously refreshes the list of saved layouts from disk.
     */
    fun loadSavedLayouts() {
        try {
            viewModelScope.launch {
                _uiState.update { it.copy(isLoadingSavedLayouts = true) }
                try {
                    loadSavedLayoutsDirect()
                } finally {
                    _uiState.update { it.copy(isLoadingSavedLayouts = false) }
                }
            }
        } catch (_: Throwable) {
            // Main dispatcher may be absent in pure JUnit environments
        }
    }

    /**
     * Loads a chosen [layout] into the workspace, resetting unsaved flags and viewport cursor.
     */
    fun loadLayout(layout: Layout) {
        _uiState.update { state ->
            val unselectedObjects = layout.objects.map { it.copy(isSelected = false) }
            state.copy(
                currentLayout = layout.copy(objects = unselectedObjects),
                selectedObjectId = null,
                isPersisted = true,
                hasUnsavedChanges = false,
                cursorCoordinatesMeters = null,
                panOffset = Offset.Zero,
                zoom = CoordinateTransformer.DEFAULT_ZOOM,
                userFeedbackMessage = "Loaded layout '${layout.name}'"
            )
        }
    }

    /**
     * Creates a brand-new blank layout with an empty canvas and reset status.
     */
    fun createNewLayout(name: String = "Tactical Field Alpha") {
        val freshLayout = Layout(
            id = UUID.randomUUID().toString(),
            name = name,
            anchor = com.example.dhruvar.domain.model.Anchor(),
            objects = emptyList(),
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _uiState.update { state ->
            state.copy(
                currentLayout = freshLayout,
                selectedObjectId = null,
                isPersisted = false,
                hasUnsavedChanges = false,
                cursorCoordinatesMeters = null,
                panOffset = Offset.Zero,
                zoom = CoordinateTransformer.DEFAULT_ZOOM
            )
        }
    }

    /**
     * Saves the current layout to local persistence.
     * Can be called directly within coroutines (e.g. unit tests).
     */
    suspend fun saveCurrentLayoutDirect(customName: String? = null): Boolean {
        val targetName = customName?.trim()?.ifEmpty { null } ?: _uiState.value.currentLayout.name
        val updated = _uiState.value.currentLayout.copy(
            name = targetName,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val success = repository.saveLayout(updated)
        if (success) {
            val allLayouts = repository.getAllLayouts()
            _uiState.update { state ->
                state.copy(
                    currentLayout = updated,
                    isPersisted = true,
                    hasUnsavedChanges = false,
                    lastSavedTimestamp = updated.updatedAtEpochMs,
                    savedLayouts = allLayouts,
                    userFeedbackMessage = "Saved '${updated.name}'"
                )
            }
        } else {
            _uiState.update { state ->
                state.copy(userFeedbackMessage = "Failed to save '${updated.name}'")
            }
        }
        return success
    }

    /**
     * Saves the current layout asynchronously, optionally updating its name.
     */
    fun saveCurrentLayout(customName: String? = null, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = saveCurrentLayoutDirect(customName)
            onComplete?.invoke(success)
        }
    }

    /**
     * Directly renames a layout by ID.
     */
    suspend fun renameLayoutDirect(id: String, newName: String): Layout? {
        val updated = repository.renameLayout(id, newName)
        if (updated != null) {
            val all = repository.getAllLayouts()
            _uiState.update { state ->
                val isCurrent = state.currentLayout.id == id
                val newCurrent = if (isCurrent) state.currentLayout.copy(name = updated.name) else state.currentLayout
                state.copy(
                    currentLayout = newCurrent,
                    savedLayouts = all,
                    userFeedbackMessage = "Renamed to '${updated.name}'"
                )
            }
        }
        return updated
    }

    /**
     * Asynchronously renames a layout by ID.
     */
    fun renameLayout(id: String, newName: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val res = renameLayoutDirect(id, newName)
            onComplete?.invoke(res != null)
        }
    }

    /**
     * Directly deletes a layout by ID.
     */
    suspend fun deleteLayoutDirect(id: String): Boolean {
        val success = repository.deleteLayout(id)
        if (success) {
            val all = repository.getAllLayouts()
            _uiState.update { state ->
                val isCurrent = state.currentLayout.id == id
                state.copy(
                    isPersisted = if (isCurrent) false else state.isPersisted,
                    hasUnsavedChanges = if (isCurrent) true else state.hasUnsavedChanges,
                    savedLayouts = all,
                    userFeedbackMessage = "Layout deleted"
                )
            }
        }
        return success
    }

    /**
     * Asynchronously deletes a layout by ID.
     */
    fun deleteLayout(id: String, onComplete: ((Boolean) -> Unit)? = null) {
        viewModelScope.launch {
            val success = deleteLayoutDirect(id)
            onComplete?.invoke(success)
        }
    }

    fun clearUserFeedback() {
        _uiState.update { it.copy(userFeedbackMessage = null) }
    }

    // ==========================================
    // VIEWPORT & DISPLAY CONTROLS
    // ==========================================

    fun toggleShowDistances() {
        _uiState.update { it.copy(showDistances = !it.showDistances) }
    }

    fun setShowDistances(show: Boolean) {
        _uiState.update { it.copy(showDistances = show) }
    }

    fun onPan(delta: Offset) {
        _uiState.update { state ->
            state.copy(panOffset = state.panOffset + delta)
        }
    }

    fun onZoom(zoomMultiplier: Float, centroid: Offset = Offset.Zero) {
        _uiState.update { state ->
            val oldZoom = state.zoom
            val newZoom = (oldZoom * zoomMultiplier).coerceIn(
                CoordinateTransformer.MIN_ZOOM,
                CoordinateTransformer.MAX_ZOOM
            )
            if (newZoom == oldZoom) return@update state

            val scaleFactor = newZoom / oldZoom
            val newPan = if (centroid != Offset.Zero) {
                centroid - (centroid - state.panOffset) * scaleFactor
            } else {
                state.panOffset * scaleFactor
            }

            state.copy(
                zoom = newZoom,
                panOffset = newPan
            )
        }
    }

    fun zoomIn() {
        onZoom(1.25f)
    }

    fun zoomOut() {
        onZoom(0.8f)
    }

    fun resetView() {
        _uiState.update { state ->
            state.copy(
                panOffset = Offset.Zero,
                zoom = CoordinateTransformer.DEFAULT_ZOOM,
                cursorCoordinatesMeters = null
            )
        }
    }

    fun updateCursorCoordinates(xMeters: Float, zMeters: Float) {
        _uiState.update { it.copy(cursorCoordinatesMeters = Pair(xMeters, zMeters)) }
    }

    fun clearCursorCoordinates() {
        _uiState.update { it.copy(cursorCoordinatesMeters = null) }
    }

    // ==========================================
    // OBJECT SELECTION & MANIPULATION
    // ==========================================

    fun selectAssetType(assetType: AssetType) {
        _uiState.update { it.copy(selectedAssetType = assetType) }
    }

    fun selectObject(objectId: String?) {
        _uiState.update { state ->
            val updatedObjects = state.currentLayout.objects.map { obj ->
                obj.copy(isSelected = (obj.id == objectId))
            }
            state.copy(
                selectedObjectId = objectId,
                currentLayout = state.currentLayout.copy(objects = updatedObjects)
            )
        }
    }

    fun deselectObject() {
        selectObject(null)
    }

    fun moveObject(objectId: String, newX: Float, newZ: Float) {
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { obj ->
                if (obj.id == objectId) {
                    obj.copy(x = newX, z = newZ)
                } else {
                    obj
                }
            }
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateObjectCoordinates(objectId: String, newX: Float, newZ: Float) {
        moveObject(objectId, newX, newZ)
    }

    fun updateSelectedObjectPosition(newX: Float, newZ: Float) {
        val selectedId = _uiState.value.selectedObjectId ?: return
        moveObject(selectedId, newX, newZ)
    }

    fun updateObjectDimensions(objectId: String, newWidth: Float, newLength: Float) {
        if (newWidth <= 0f || newLength <= 0f) return
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { obj ->
                if (obj.id == objectId) {
                    obj.withDimensions(newWidth = newWidth, newLength = newLength)
                } else {
                    obj
                }
            }
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun updateSelectedObjectDimensions(newWidth: Float, newLength: Float) {
        val selectedId = _uiState.value.selectedObjectId ?: return
        updateObjectDimensions(selectedId, newWidth, newLength)
    }

    fun rotateObject(objectId: String, degreesDelta: Float) {
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { obj ->
                if (obj.id == objectId) {
                    obj.withNormalizedRotation(obj.rotationDegrees + degreesDelta)
                } else {
                    obj
                }
            }
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun setObjectRotation(objectId: String, absoluteDegrees: Float) {
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { obj ->
                if (obj.id == objectId) {
                    obj.withNormalizedRotation(absoluteDegrees)
                } else {
                    obj
                }
            }
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun rotateSelectedObject(degreesDelta: Float) {
        val selectedId = _uiState.value.selectedObjectId ?: return
        rotateObject(selectedId, degreesDelta)
    }

    fun setSelectedObjectRotation(absoluteDegrees: Float) {
        val selectedId = _uiState.value.selectedObjectId ?: return
        setObjectRotation(selectedId, absoluteDegrees)
    }

    fun duplicateSelectedObject(offsetXMeters: Float = 1.5f, offsetZMeters: Float = 1.5f) {
        _uiState.update { state ->
            val original = state.selectedObject ?: return@update state
            val count = state.currentLayout.objects.count { it.assetType == original.assetType } + 1
            val duplicate = LayoutObject(
                id = UUID.randomUUID().toString(),
                assetType = original.assetType,
                name = "${original.assetType.displayName} $count",
                x = original.x + offsetXMeters,
                z = original.z + offsetZMeters,
                rotationDegrees = original.rotationDegrees,
                scale = original.scale,
                widthMeters = original.widthMeters,
                lengthMeters = original.lengthMeters,
                heightMeters = original.heightMeters,
                isSelected = true
            )
            val updatedList = state.currentLayout.objects.map { it.copy(isSelected = false) } + duplicate
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = duplicate.id
            )
        }
    }

    fun addAssetAt(xMeters: Float, zMeters: Float) {
        val assetType = _uiState.value.selectedAssetType
        val count = _uiState.value.currentLayout.objects.count { it.assetType == assetType } + 1
        val newObj = LayoutObject(
            assetType = assetType,
            name = "${assetType.displayName} $count",
            x = xMeters,
            z = zMeters,
            rotationDegrees = 0.0f,
            scale = 1.0f,
            isSelected = true
        )
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { it.copy(isSelected = false) } + newObj
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = newObj.id
            )
        }
    }

    fun deleteSelectedObject() {
        _uiState.update { state ->
            val targetId = state.selectedObjectId ?: return@update state
            val updatedList = state.currentLayout.objects.filterNot { it.id == targetId }
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = null
            )
        }
    }

    fun deleteObject(objectId: String) {
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.filterNot { it.id == objectId }
            val newSelectedId = if (state.selectedObjectId == objectId) null else state.selectedObjectId
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = newSelectedId
            )
        }
    }

    fun updateLayoutName(newName: String) {
        _uiState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    name = newName,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
    }

    fun clearLayout() {
        _uiState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = emptyList(),
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = null
            )
        }
    }
}
