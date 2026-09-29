package com.example.dhruvar.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhruvar.data.location.DefaultDeviceLocationControllerProvider
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.data.repository.LayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationController
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.sensors.CompassHeadingController
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.MapLayoutProjection
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
    val isLoadingSavedLayouts: Boolean = false,
    // Phase 3: GNSS geographic origin
    val locationState: DeviceLocationState = DeviceLocationState(),
    val isGeoOriginPanelExpanded: Boolean = false,
    /** Non-null when the last device stamp used accuracy worse than 15 m. */
    val geoAccuracyWarning: String? = null,
    // Phase 4: MapLibre planning surface
    val planningSurface: PlanningSurface = PlanningSurface.MAP,
    val mapInteractionMode: MapInteractionMode = MapInteractionMode.NAVIGATE,
    val mapStyleError: String? = null
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
    private val repository: LayoutRepository = DefaultLayoutRepositoryProvider.getRepository(),
    private val locationController: DeviceLocationController =
        DefaultDeviceLocationControllerProvider.getController()
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PlanningUiState(locationState = locationController.state.value)
    )
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

    init {
        try {
            viewModelScope.launch {
                locationController.state.collect { loc ->
                    _uiState.update { it.copy(locationState = loc) }
                }
            }
        } catch (_: Throwable) {
            // Main dispatcher / scope may be limited in pure JUnit environments
        }
    }

    override fun onCleared() {
        locationController.stopUpdates()
        super.onCleared()
    }

    // ==========================================
    // GEOGRAPHIC ORIGIN (Phase 3)
    // ==========================================

    fun setGeoOriginPanelExpanded(expanded: Boolean) {
        _uiState.update {
            it.copy(
                isGeoOriginPanelExpanded = expanded,
                locationState = locationController.state.value
            )
        }
        if (expanded) {
            locationController.startHighAccuracyUpdates()
        } else {
            locationController.stopUpdates()
        }
        syncLocationState()
    }

    fun onLocationPermissionResult(granted: Boolean) {
        locationController.onPermissionResult(granted)
        syncLocationState()
        if (granted && _uiState.value.isGeoOriginPanelExpanded) {
            locationController.startHighAccuracyUpdates()
            syncLocationState()
        }
        if (!granted) {
            _uiState.update {
                it.copy(
                    userFeedbackMessage = "Location permission denied — geo origin cannot use GNSS"
                )
            }
        }
    }

    fun refreshLocation() {
        locationController.refreshLocation()
        syncLocationState()
    }

    /**
     * Stamps the layout geographic anchor from the latest device GNSS fix.
     * [headingDegrees] should come from the live compass (magnetic heading).
     *
     * @return true if the anchor was updated.
     */
    fun setGeographicAnchorFromDeviceFix(headingDegrees: Float): Boolean {
        syncLocationState()
        val locState = locationController.state.value
        val fix = locState.lastFix
        if (fix == null || !fix.isFinite) {
            _uiState.update {
                it.copy(userFeedbackMessage = "No GNSS fix available — wait for a location signal")
            }
            return false
        }
        if (!locState.permissionGranted) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Location permission required to stamp origin")
            }
            return false
        }
        val heading = CompassHeadingController.normalizeDegrees(headingDegrees)
        val poorAccuracy = fix.horizontalAccuracyMeters != null &&
            fix.horizontalAccuracyMeters > DeviceLocationState.POOR_ACCURACY_METERS
        val warning = if (poorAccuracy) {
            SITE_LEVEL_ACCURACY_WARNING
        } else {
            null
        }
        applyGeographicAnchor(
            latitude = fix.latitude,
            longitude = fix.longitude,
            headingDegrees = heading,
            horizontalAccuracyMeters = fix.horizontalAccuracyMeters,
            feedback = if (poorAccuracy) {
                "Origin set (±${"%.0f".format(fix.horizontalAccuracyMeters)} m). $SITE_LEVEL_ACCURACY_WARNING"
            } else {
                "Geographic origin set from device GNSS"
            },
            accuracyWarning = warning
        )
        return true
    }

    /**
     * Manually sets geographic anchor fields after validation.
     * @return true if applied.
     */
    fun setGeographicAnchorManual(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float
    ): Boolean {
        if (!latitude.isFinite() || latitude !in -90.0..90.0) {
            _uiState.update { it.copy(userFeedbackMessage = "Latitude must be a finite value in [-90, 90]") }
            return false
        }
        if (!longitude.isFinite()) {
            _uiState.update { it.copy(userFeedbackMessage = "Longitude must be finite") }
            return false
        }
        if (!headingDegrees.isFinite()) {
            _uiState.update { it.copy(userFeedbackMessage = "Heading must be finite") }
            return false
        }
        applyGeographicAnchor(
            latitude = latitude,
            longitude = longitude,
            headingDegrees = CompassHeadingController.normalizeDegrees(headingDegrees),
            horizontalAccuracyMeters = null,
            feedback = "Geographic origin set manually",
            accuracyWarning = null
        )
        return true
    }

    /**
     * Writes compass heading onto the layout anchor (Capture heading).
     */
    fun updateGeographicHeading(headingDegrees: Float): Boolean {
        if (!headingDegrees.isFinite()) {
            _uiState.update { it.copy(userFeedbackMessage = "Heading must be finite") }
            return false
        }
        val heading = CompassHeadingController.normalizeDegrees(headingDegrees)
        _uiState.update { state ->
            val updatedAnchor = state.currentLayout.anchor.copy(headingDegrees = heading)
            state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = state.geoAccuracyWarning,
                currentLayout = state.currentLayout.copy(
                    anchor = updatedAnchor,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Heading captured: ${"%.1f".format(heading)}°"
            )
        }
        return true
    }

    fun clearGeographicAnchor() {
        _uiState.update { state ->
            val cleared = state.currentLayout.anchor.copy(
                latitude = null,
                longitude = null,
                headingDegrees = null,
                horizontalAccuracyMeters = null
            )
            state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = null,
                currentLayout = state.currentLayout.copy(
                    anchor = cleared,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Geographic origin cleared"
            )
        }
    }

    private fun applyGeographicAnchor(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float,
        horizontalAccuracyMeters: Float?,
        feedback: String,
        accuracyWarning: String?
    ) {
        _uiState.update { state ->
            val updatedAnchor = state.currentLayout.anchor.copy(
                latitude = latitude,
                longitude = longitude,
                headingDegrees = headingDegrees,
                horizontalAccuracyMeters = horizontalAccuracyMeters
            )
            state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = accuracyWarning,
                locationState = locationController.state.value,
                currentLayout = state.currentLayout.copy(
                    anchor = updatedAnchor,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = feedback
            )
        }
    }

    private fun syncLocationState() {
        _uiState.update { it.copy(locationState = locationController.state.value) }
    }

    // ==========================================
    // MAP PLANNING (Phase 4)
    // ==========================================

    fun setPlanningSurface(surface: PlanningSurface) {
        _uiState.update { it.copy(planningSurface = surface) }
    }

    fun setMapInteractionMode(mode: MapInteractionMode) {
        _uiState.update { it.copy(mapInteractionMode = mode) }
    }

    fun setMapStyleError(message: String?) {
        _uiState.update { it.copy(mapStyleError = message) }
    }

    /**
     * Sets or replaces [Layout.regionBounds]. Invalid boxes are rejected.
     */
    fun setRegionBounds(bounds: GeoBounds?): Boolean {
        if (bounds == null) {
            clearRegionBounds()
            return true
        }
        _uiState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    regionBounds = bounds,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Region bounds set"
            )
        }
        return true
    }

    fun setRegionBoundsFromViewport(
        northLatitude: Double,
        southLatitude: Double,
        eastLongitude: Double,
        westLongitude: Double
    ): Boolean {
        val bounds = GeoBounds.createOrNull(
            northLatitude = northLatitude,
            southLatitude = southLatitude,
            eastLongitude = eastLongitude,
            westLongitude = westLongitude
        )
        if (bounds == null) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Invalid region — check north≥south and west≤east")
            }
            return false
        }
        return setRegionBounds(bounds)
    }

    fun clearRegionBounds() {
        _uiState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    regionBounds = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Region bounds cleared"
            )
        }
    }

    /**
     * Sets geographic anchor from a map tap. Preserves existing heading when [headingDegrees] is null,
     * otherwise uses the provided heading (typically live compass).
     */
    fun setGeographicAnchorFromMap(
        latitude: Double,
        longitude: Double,
        headingDegrees: Float?
    ): Boolean {
        val existingHeading = _uiState.value.currentLayout.anchor.headingDegrees
        val heading = headingDegrees ?: existingHeading ?: 0f
        return setGeographicAnchorManual(latitude, longitude, heading)
    }

    /**
     * Places the selected asset type at a geographic point.
     *
     * Blocks when the layout has no geographic fix. When [GeoBounds] is set, points outside
     * the region are **rejected** (not clamped).
     */
    fun placeAssetAtGeographic(latitude: Double, longitude: Double): Boolean {
        val layout = _uiState.value.currentLayout
        val anchor = layout.anchor
        if (!anchor.hasGeographicFix) {
            _uiState.update {
                it.copy(
                    isGeoOriginPanelExpanded = true,
                    userFeedbackMessage = "Set geographic origin before placing assets on the map"
                )
            }
            return false
        }
        if (!MapLayoutProjection.isInsideRegionOrUnbounded(layout.regionBounds, latitude, longitude)) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Placement rejected — outside selected region")
            }
            return false
        }
        val meters = MapLayoutProjection.metersFromAnchor(anchor, latitude, longitude)
        if (meters == null) {
            _uiState.update { it.copy(userFeedbackMessage = "Could not convert map tap to meters") }
            return false
        }
        addAssetOfType(_uiState.value.selectedAssetType, meters.first, meters.second)
        return true
    }

    /**
     * Moves an existing object to a geographic point (EDIT mode).
     * Outside-region taps are rejected when bounds are set.
     */
    fun moveObjectToGeographic(objectId: String, latitude: Double, longitude: Double): Boolean {
        val layout = _uiState.value.currentLayout
        val anchor = layout.anchor
        if (!anchor.hasGeographicFix) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Geographic origin required to move assets on the map")
            }
            return false
        }
        if (!MapLayoutProjection.isInsideRegionOrUnbounded(layout.regionBounds, latitude, longitude)) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Move rejected — outside selected region")
            }
            return false
        }
        val meters = MapLayoutProjection.metersFromAnchor(anchor, latitude, longitude) ?: return false
        moveObject(objectId, meters.first, meters.second)
        return true
    }

    /**
     * Handles an EDIT-mode map tap: select nearest asset, or move the current selection.
     */
    fun handleMapEditTap(latitude: Double, longitude: Double): Boolean {
        val layout = _uiState.value.currentLayout
        val nearest = MapLayoutProjection.findNearestObjectId(
            layout.anchor,
            layout.objects,
            latitude,
            longitude
        )
        val selectedId = _uiState.value.selectedObjectId
        return when {
            nearest != null -> {
                selectObject(nearest)
                true
            }
            selectedId != null -> moveObjectToGeographic(selectedId, latitude, longitude)
            else -> {
                deselectObject()
                false
            }
        }
    }

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
                // Session chrome restoration: Map primary + Navigate after load
                planningSurface = PlanningSurface.MAP,
                mapInteractionMode = MapInteractionMode.NAVIGATE,
                mapStyleError = null,
                isGeoOriginPanelExpanded = false,
                geoAccuracyWarning = null,
                userFeedbackMessage = "Loaded layout '${layout.name}'"
            )
        }
        locationController.stopUpdates()
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
                zoom = CoordinateTransformer.DEFAULT_ZOOM,
                planningSurface = PlanningSurface.MAP,
                mapInteractionMode = MapInteractionMode.NAVIGATE,
                mapStyleError = null,
                isGeoOriginPanelExpanded = false,
                geoAccuracyWarning = null
            )
        }
        locationController.stopUpdates()
    }

    /** Stops GNSS updates when leaving Planning (battery). Safe to call repeatedly. */
    fun stopLocationUpdates() {
        locationController.stopUpdates()
        _uiState.update {
            it.copy(
                isGeoOriginPanelExpanded = false,
                locationState = locationController.state.value
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

    fun updateObjectDimensions(
        objectId: String,
        newWidth: Float,
        newLength: Float,
        newHeight: Float? = null
    ) {
        if (newWidth <= 0f || newLength <= 0f) return
        if (newHeight != null && newHeight <= 0f) return
        _uiState.update { state ->
            val updatedList = state.currentLayout.objects.map { obj ->
                if (obj.id == objectId) {
                    obj.withDimensions(
                        newWidth = newWidth,
                        newLength = newLength,
                        newHeight = newHeight ?: obj.heightMeters
                    )
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

    fun updateSelectedObjectDimensions(newWidth: Float, newLength: Float, newHeight: Float? = null) {
        val selectedId = _uiState.value.selectedObjectId ?: return
        updateObjectDimensions(selectedId, newWidth, newLength, newHeight)
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

    /**
     * Selects [assetType] and places a new instance at the given metric coordinates.
     * The new object becomes selected; existing objects are left in place.
     */
    fun addAssetOfType(assetType: AssetType, xMeters: Float, zMeters: Float) {
        _uiState.update { state ->
            val count = state.currentLayout.objects.count { it.assetType == assetType } + 1
            val newObj = LayoutObject(
                assetType = assetType,
                name = "${assetType.displayName} $count",
                x = xMeters,
                z = zMeters,
                rotationDegrees = 0.0f,
                scale = 1.0f,
                isSelected = true
            )
            val updatedList = state.currentLayout.objects.map { it.copy(isSelected = false) } + newObj
            state.copy(
                selectedAssetType = assetType,
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                selectedObjectId = newObj.id
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

    companion object {
        const val SITE_LEVEL_ACCURACY_WARNING =
            "Site-level only; relative meter placement remains precise"
    }
}
