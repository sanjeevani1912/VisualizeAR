package com.example.dhruvar.viewmodel

import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dhruvar.data.location.DefaultDeviceLocationControllerProvider
import com.example.dhruvar.data.map.DefaultPlaceSearchGateway
import com.example.dhruvar.data.map.MapBasemapConfig
import com.example.dhruvar.data.map.PlaceSearchGateway
import com.example.dhruvar.data.repository.DefaultLayoutRepositoryProvider
import com.example.dhruvar.data.repository.LayoutRepository
import com.example.dhruvar.data.repository.UserScopedLayoutRepository
import com.example.dhruvar.domain.location.DeviceLocationController
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.ChosenMapLocation
import com.example.dhruvar.domain.model.AssetSizeConfig
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.domain.model.PlanningSetupSession
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.model.coerceMapInteractionMode
import com.example.dhruvar.domain.model.resolvePlanningSetupStep
import com.example.dhruvar.domain.model.toChosenMapLocation
import com.example.dhruvar.domain.sensors.CompassHeadingController
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.MapLayoutProjection
import com.example.dhruvar.domain.spatial.RegionDraftGeometry
import com.example.dhruvar.domain.spatial.withOriginAtRegionCenter
import com.example.dhruvar.ui.map.MapCameraBridge
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Expandable place search over the planning map.
 *
 * [results] is only ever written by the newest query, because each keystroke cancels the previous
 * request — a slow early response cannot overwrite a fast later one.
 */
data class PlaceSearchUiState(
    val isExpanded: Boolean = false,
    val query: String = "",
    val results: List<PlaceSearchHit> = emptyList(),
    val isSearching: Boolean = false,
    val error: String? = null
) {
    /** True when a finished search genuinely found nothing, as opposed to not having run yet. */
    val isEmptyResult: Boolean
        get() = query.isNotBlank() && !isSearching && error == null && results.isEmpty()
}

/**
 * UI State for the 2D Top-Down Planning Canvas and persistence lifecycle.
 */
data class PlanningUiState(
    val currentLayout: Layout = Layout(),
    val selectedAssetType: AssetType = AssetType.MILITARY_VEHICLE,
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
    val mapStyleError: String? = null,
    // Phase 5: map server place search
    val placeSearch: PlaceSearchUiState = PlaceSearchUiState(),
    // Guided setup (GS-01). Default matches a blank in-memory layout: New Plan, not a workspace.
    val setupStep: PlanningSetupStep = PlanningSetupStep.FIND_LOCATION,
    val setupSession: PlanningSetupSession = PlanningSetupSession(),
    val isBrandNewLayout: Boolean = true,
    /** Side of the square region built around the operator; picked in the area chips. */
    val regionSideMeters: Double = AssetSizeConfig.DEFAULT_REGION_SIDE_METERS,
    // Per-user plans on a shared phone
    /** Server user id of whoever is signed in; null = nobody. */
    val signedInUserId: String? = null,
    /** Ids in [savedLayouts] that are "This device" plans (saved without an account). */
    val devicePlanIds: Set<String> = emptySet(),
    /** True while a signed-in user looks at a device plan: map and AR only, no changes. */
    val isViewOnly: Boolean = false
) {
    /** The signed-in user's own plans (empty when signed out). */
    val myPlans: List<Layout>
        get() = if (signedInUserId == null) emptyList() else savedLayouts.filter { it.id !in devicePlanIds }

    /** Plans saved on this phone without an account; shown to everyone. */
    val devicePlans: List<Layout>
        get() = savedLayouts.filter { it.id in devicePlanIds }


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
        DefaultDeviceLocationControllerProvider.getController(),
    private val placeSearch: PlaceSearchGateway = DefaultPlaceSearchGateway,
    private val searchDebounceMillis: Long = SEARCH_DEBOUNCE_MILLIS
) : ViewModel() {

    private var searchJob: Job? = null
    private var awaitingAutoLocate = false
    private var autoLocateReplacesChoice = false
    private var autoRegionApplied = false

    /** Accuracy of the rough position already shown while still waiting for a good fix. */
    private var autoLocateShownAccuracy: Float? = null

    private val _uiState = MutableStateFlow(
        PlanningUiState(locationState = locationController.state.value)
    )
    val uiState: StateFlow<PlanningUiState> = _uiState.asStateFlow()

    init {
        try {
            viewModelScope.launch {
                locationController.state.collect { loc ->
                    _uiState.update { it.copy(locationState = loc) }
                    applyAutoLocateIfReady()
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
        if (granted && awaitingAutoLocate) {
            locationController.startHighAccuracyUpdates()
        }
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
        if (viewOnlyBlocked()) return false
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
        if (rejectOriginOutsideRegion(fix.latitude, fix.longitude, GPS_OUTSIDE_REGION)) {
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
                "Origin stamped (±${"%.0f".format(fix.horizontalAccuracyMeters)} m). $SITE_LEVEL_ACCURACY_WARNING"
            } else {
                "Origin stamped"
            },
            accuracyWarning = warning
        )
        maybeAdvanceAfterOriginStamp()
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
        if (viewOnlyBlocked()) return false
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
        if (rejectOriginOutsideRegion(latitude, longitude, ORIGIN_MUST_BE_INSIDE_REGION)) {
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
        maybeAdvanceAfterOriginStamp()
        return true
    }

    /**
     * Writes compass heading onto the layout anchor (Capture heading).
     */
    fun updateGeographicHeading(headingDegrees: Float): Boolean {
        if (viewOnlyBlocked()) return false
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
        if (viewOnlyBlocked()) return
        _uiState.update { state ->
            val cleared = state.currentLayout.anchor.copy(
                latitude = null,
                longitude = null,
                headingDegrees = null,
                horizontalAccuracyMeters = null
            )
            val updated = state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = null,
                currentLayout = state.currentLayout.copy(
                    anchor = cleared,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Geographic origin cleared"
            )
            resyncSetupStep(updated)
        }
    }

    /**
     * When a region exists, origin must sit inside it (SET_ORIGIN and PLACE_ASSETS).
     * @return true if the caller should abort without writing the anchor.
     */
    private fun rejectOriginOutsideRegion(
        latitude: Double,
        longitude: Double,
        message: String
    ): Boolean {
        val region = _uiState.value.currentLayout.regionBounds ?: return false
        if (MapLayoutProjection.isUsableOrigin(region, latitude, longitude)) return false
        _uiState.update { it.copy(userFeedbackMessage = message) }
        return true
    }

    private fun maybeAdvanceAfterOriginStamp() {
        val state = _uiState.value
        if (state.setupStep != PlanningSetupStep.SET_ORIGIN) return
        if (!state.currentLayout.anchor.hasGeographicFix) return
        _uiState.update {
            it.copy(
                isGeoOriginPanelExpanded = false,
                mapInteractionMode = MapInteractionMode.NAVIGATE
            )
        }
        locationController.stopUpdates()
        advanceSetupStep()
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
        applyAutoLocateIfReady()
    }

    // ==========================================
    // AUTO-LOCATE ON NEW PLAN (platform GNSS)
    // ==========================================

    /**
     * Starts satellite positioning for the Find location step and, on the first fix, centers the
     * map on it. Called when a new plan opens (never replaces a place the user already chose) and
     * by the Detect location button (which does replace it).
     */
    fun beginAutoLocate(replaceChosenLocation: Boolean = false) {
        if (viewOnlyBlocked()) return
        if (_uiState.value.setupStep != PlanningSetupStep.FIND_LOCATION) return
        autoLocateReplacesChoice = replaceChosenLocation
        autoLocateShownAccuracy = null
        if (_uiState.value.currentLayout.regionBounds == null) autoRegionApplied = false
        awaitingAutoLocate = true
        val permitted = locationController.state.value.permissionGranted
        if (permitted) {
            locationController.startHighAccuracyUpdates()
        }
        syncLocationState()
    }

    private fun applyAutoLocateIfReady() {
        if (!awaitingAutoLocate) return
        val state = _uiState.value
        // After the first fix the region already exists; keep refining it until the plan has assets.
        val refining = autoRegionApplied &&
            state.setupStep == PlanningSetupStep.PLACE_ASSETS &&
            state.currentLayout.objects.isEmpty()
        if (state.setupStep != PlanningSetupStep.FIND_LOCATION && !refining) {
            awaitingAutoLocate = false
            return
        }
        val fix = state.locationState.lastFix?.takeIf { it.isFinite } ?: return
        val shownAccuracy = autoLocateShownAccuracy
        if (shownAccuracy == null && state.setupSession.chosenLocation != null && !autoLocateReplacesChoice) {
            awaitingAutoLocate = false
            locationController.stopUpdates()
            return
        }
        val accuracy = fix.horizontalAccuracyMeters
        val accurate = fix.satelliteDerived &&
            accuracy != null &&
            accuracy <= AUTO_LOCATE_GOOD_ACCURACY_METERS
        // A rough first fix (network, or a satellite fix still converging) is used straight
        // away, but the search keeps going and re-centers only when a fix is genuinely better.
        if (!accurate && shownAccuracy != null && (accuracy == null || accuracy >= shownAccuracy)) return
        if (accurate) {
            awaitingAutoLocate = false
            autoLocateShownAccuracy = null
            locationController.stopUpdates()
        } else {
            autoLocateShownAccuracy = accuracy ?: Float.MAX_VALUE
        }
        // Frame the 250 m region instead of a city-wide view.
        val zoom = AUTO_REGION_ZOOM
        MapCameraBridge.moveTo(
            latitude = fix.latitude,
            longitude = fix.longitude,
            zoom = zoom
        )
        _uiState.update {
            val located = it.copy(
                planningSurface = PlanningSurface.MAP,
                setupSession = it.setupSession.copy(
                    chosenLocation = ChosenMapLocation(
                        latitude = fix.latitude,
                        longitude = fix.longitude,
                        displayName = CURRENT_LOCATION_NAME,
                        zoom = zoom
                    )
                ),
                locationState = locationController.state.value
            )
            val feedback = when {
                accurate -> "Region set around your GNSS position (±${"%.0f".format(accuracy)} m)"
                accuracy != null ->
                    "Region set around you (±${"%.0f".format(accuracy)} m) — refining with satellites…"
                else -> "Region set around you — refining with satellites…"
            }
            // The operator is the center of a 250 m region and the origin, accurate fix or not.
            withAutoRegionAround(located, fix.latitude, fix.longitude)
                .copy(userFeedbackMessage = feedback)
        }
    }

    /**
     * Drops a [RegionDraftGeometry.AUTO_REGION_SIDE_METERS] square on [latitude]/[longitude] and
     * jumps to asset placement. A region the operator drew, or one that already holds assets,
     * is never replaced; only the auto-created one is re-centered as the fix improves.
     */
    private fun withAutoRegionAround(
        state: PlanningUiState,
        latitude: Double,
        longitude: Double
    ): PlanningUiState {
        val layout = state.currentLayout
        val replaceable = layout.regionBounds == null || (autoRegionApplied && layout.objects.isEmpty())
        if (!replaceable) return state
        val bounds = RegionDraftGeometry.squareAround(latitude, longitude, state.regionSideMeters)
            ?: return state
        autoRegionApplied = true
        val withRegion = state.copy(
            isBrandNewLayout = false,
            hasUnsavedChanges = true,
            placeSearch = PlaceSearchUiState(),
            currentLayout = layout.withOriginAtRegionCenter(bounds, System.currentTimeMillis())
        )
        return applySetupStep(withRegion, PlanningSetupStep.PLACE_ASSETS)
    }

    // ==========================================
    // MAP PLANNING (Phase 4)
    // ==========================================

    fun setPlanningSurface(surface: PlanningSurface) {
        _uiState.update { state ->
            val next = if (state.setupStep != PlanningSetupStep.PLACE_ASSETS) {
                PlanningSurface.MAP
            } else {
                surface
            }
            state.copy(planningSurface = next)
        }
    }

    fun setMapInteractionMode(mode: MapInteractionMode) {
        _uiState.update { state ->
            state.copy(mapInteractionMode = coerceMapInteractionMode(state.setupStep, mode))
        }
    }

    fun setMapStyleError(message: String?) {
        _uiState.update { it.copy(mapStyleError = message) }
    }

    // ==========================================
    // PLACE SEARCH (Phase 5)
    // ==========================================

    /** Collapsing discards the query and any in-flight request, so reopening starts clean. */
    fun setSearchExpanded(expanded: Boolean) {
        searchJob?.cancel()
        searchJob = null
        // Search and the origin sheet both claim the top of the map, and leaving the sheet open
        // behind the field would also leave GNSS running out of sight.
        if (expanded && _uiState.value.isGeoOriginPanelExpanded) {
            setGeoOriginPanelExpanded(false)
        }
        _uiState.update {
            it.copy(
                placeSearch = if (expanded) {
                    PlaceSearchUiState(
                        isExpanded = true,
                        error = if (placeSearch.isConfigured) null else SEARCH_NOT_CONFIGURED
                    )
                } else {
                    PlaceSearchUiState()
                }
            )
        }
    }

    /**
     * Debounces by [searchDebounceMillis] and cancels the previous request, so holding down a key
     * issues one call rather than one per character.
     */
    fun onSearchQueryChange(query: String) {
        searchJob?.cancel()
        val isBlank = query.isBlank()
        _uiState.update { state ->
            state.copy(
                placeSearch = state.placeSearch.copy(
                    query = query,
                    results = if (isBlank) emptyList() else state.placeSearch.results,
                    isSearching = !isBlank && placeSearch.isConfigured,
                    error = when {
                        !placeSearch.isConfigured -> SEARCH_NOT_CONFIGURED
                        else -> null
                    }
                )
            )
        }
        if (isBlank || !placeSearch.isConfigured) {
            searchJob = null
            return
        }

        searchJob = viewModelScope.launch {
            delay(searchDebounceMillis)
            val fix = _uiState.value.locationState.lastFix
            val near = fix
                ?.takeIf { it.isFinite }
                ?.let { it.latitude to it.longitude }

            placeSearch.search(query.trim(), near).fold(
                onSuccess = { hits ->
                    _uiState.update {
                        it.copy(
                            placeSearch = it.placeSearch.copy(
                                results = hits,
                                isSearching = false,
                                error = null
                            )
                        )
                    }
                },
                onFailure = {
                    _uiState.update { state ->
                        state.copy(
                            placeSearch = state.placeSearch.copy(
                                results = emptyList(),
                                isSearching = false,
                                error = SEARCH_UNAVAILABLE
                            )
                        )
                    }
                }
            )
        }
    }

    /**
     * Flies the map to [hit]. Does not write origin or region JSON.
     *
     * On [PlanningSetupStep.FIND_LOCATION] the hit is stored as [PlanningSetupSession.chosenLocation]
     * and the field stays open with results collapsed so Continue can light up. Other steps still
     * collapse search entirely.
     */
    fun onSearchResultSelected(hit: PlaceSearchHit) {
        MapCameraBridge.moveTo(
            latitude = hit.latitude,
            longitude = hit.longitude,
            zoom = MapBasemapConfig.SEARCH_RESULT_ZOOM
        )
        searchJob?.cancel()
        searchJob = null
        val chosen = hit.toChosenMapLocation(MapBasemapConfig.SEARCH_RESULT_ZOOM)
        _uiState.update { state ->
            val keepField = state.setupStep == PlanningSetupStep.FIND_LOCATION
            state.copy(
                planningSurface = PlanningSurface.MAP,
                setupSession = state.setupSession.copy(chosenLocation = chosen),
                placeSearch = if (keepField) {
                    state.placeSearch.copy(
                        isExpanded = true,
                        query = hit.displayName,
                        results = emptyList(),
                        isSearching = false,
                        error = null
                    )
                } else {
                    PlaceSearchUiState()
                },
                userFeedbackMessage = "Centered on ${hit.displayName}"
            )
        }
    }

    /**
     * Sets or replaces [Layout.regionBounds]. Does **not** advance guided setup — use
     * [confirmRegionAndAdvance] on SET_REGION so Confirm / Use visible area move to planning.
     * A chosen origin that still sits inside the new box is kept. Otherwise the origin
     * becomes the region center and assets stay on the same ground.
     */
    fun setRegionBounds(bounds: GeoBounds?): Boolean {
        if (viewOnlyBlocked()) return false
        if (bounds == null) {
            clearRegionBounds()
            return true
        }
        _uiState.update { state ->
            val layout = state.currentLayout
            val origin = layout.anchor
            val keepChosenOrigin = origin.hasGeographicFix &&
                MapLayoutProjection.isUsableOrigin(bounds, origin.latitude!!, origin.longitude!!)
            val updatedLayout = if (keepChosenOrigin) {
                layout.copy(
                    regionBounds = bounds,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            } else {
                layout.withOriginAtRegionCenter(
                    bounds = bounds,
                    updatedAtEpochMs = System.currentTimeMillis()
                )
            }
            state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = if (keepChosenOrigin) state.geoAccuracyWarning else null,
                currentLayout = updatedLayout,
                userFeedbackMessage = if (keepChosenOrigin) {
                    "Region updated"
                } else {
                    "Origin set at the center of the region"
                }
            )
        }
        return true
    }

    /**
     * Commits [bounds] and, when on [PlanningSetupStep.SET_REGION], advances to planning.
     * The origin is already the region center, or a point the user chose inside the box.
     * Rejected boxes stay on the current step.
     */
    fun confirmRegionAndAdvance(bounds: GeoBounds): Boolean {
        if (viewOnlyBlocked()) return false
        val reason = RegionDraftGeometry.rejectionReason(bounds)
        if (reason != null) {
            _uiState.update { it.copy(userFeedbackMessage = reason) }
            return false
        }
        if (!setRegionBounds(bounds)) return false
        if (_uiState.value.setupStep == PlanningSetupStep.SET_REGION) {
            _uiState.update { applySetupStep(it, PlanningSetupStep.PLACE_ASSETS) }
        }
        return true
    }

    /**
     * Puts the origin on the center of the current region and keeps assets on the same ground.
     * Used when a saved plan has a region but no origin yet.
     */
    fun setOriginToRegionCenter(): Boolean {
        if (viewOnlyBlocked()) return false
        val bounds = _uiState.value.currentLayout.regionBounds ?: run {
            _uiState.update { it.copy(userFeedbackMessage = "Select a region before setting the origin") }
            return false
        }
        _uiState.update { state ->
            state.copy(
                hasUnsavedChanges = true,
                geoAccuracyWarning = null,
                currentLayout = state.currentLayout.withOriginAtRegionCenter(
                    bounds = bounds,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Origin set at the center of the region"
            )
        }
        maybeAdvanceAfterOriginStamp()
        return true
    }

    fun setRegionBoundsFromViewport(
        northLatitude: Double,
        southLatitude: Double,
        eastLongitude: Double,
        westLongitude: Double
    ): Boolean {
        if (viewOnlyBlocked()) return false
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
        return if (_uiState.value.setupStep == PlanningSetupStep.SET_REGION) {
            confirmRegionAndAdvance(bounds)
        } else {
            setRegionBounds(bounds)
        }
    }

    /**
     * Leaves the asset workspace to redraw the region. Objects and the current origin stay
     * until the new box is confirmed.
     */
    fun requestChangeRegion() {
        if (viewOnlyBlocked()) return
        _uiState.update { state ->
            if (state.setupStep != PlanningSetupStep.PLACE_ASSETS) return@update state
            applySetupStep(state, PlanningSetupStep.SET_REGION)
        }
    }

    /**
     * Returns to origin stamping inside the current region. Meter offsets on assets are not rewritten.
     */
    fun requestChangeOrigin() {
        if (viewOnlyBlocked()) return
        _uiState.update { state ->
            if (state.setupStep != PlanningSetupStep.PLACE_ASSETS) return@update state
            if (state.currentLayout.regionBounds == null) return@update state
            applySetupStep(
                state.copy(isGeoOriginPanelExpanded = true),
                PlanningSetupStep.SET_ORIGIN
            )
        }
    }

    fun clearRegionBounds() {
        if (viewOnlyBlocked()) return
        _uiState.update { state ->
            val updated = state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    regionBounds = null,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                userFeedbackMessage = "Region bounds cleared"
            )
            resyncSetupStep(updated)
        }
    }

    /**
     * Stores a search-hit location for guided setup. Does not write JSON and does not
     * auto-advance — Phase 02 owns Continue / auto-advance from Find location.
     */
    fun setChosenLocation(location: ChosenMapLocation?) {
        _uiState.update { state ->
            state.copy(setupSession = state.setupSession.copy(chosenLocation = location))
        }
    }

    /**
     * Moves FIND_LOCATION → SET_REGION → SET_ORIGIN → PLACE_ASSETS when the current step's
     * prerequisite is met. Region commit itself does **not** call this (Phase 03).
     */
    fun advanceSetupStep() {
        _uiState.update { state ->
            when (state.setupStep) {
                PlanningSetupStep.FIND_LOCATION -> {
                    // No place is required: without a search hit or GNSS fix the region step
                    // simply starts from wherever the map already is.
                    awaitingAutoLocate = false
                    locationController.stopUpdates()
                    applySetupStep(
                        state = state.copy(
                            isBrandNewLayout = false,
                            placeSearch = PlaceSearchUiState()
                        ),
                        step = PlanningSetupStep.SET_REGION
                    )
                }
                PlanningSetupStep.SET_REGION -> {
                    if (state.currentLayout.regionBounds == null) {
                        state.copy(userFeedbackMessage = ADVANCE_NEEDS_REGION)
                    } else {
                        applySetupStep(state, PlanningSetupStep.SET_ORIGIN)
                    }
                }
                PlanningSetupStep.SET_ORIGIN -> {
                    if (!state.currentLayout.anchor.hasGeographicFix) {
                        state.copy(userFeedbackMessage = ADVANCE_NEEDS_ORIGIN)
                    } else {
                        applySetupStep(state, PlanningSetupStep.PLACE_ASSETS)
                    }
                }
                PlanningSetupStep.PLACE_ASSETS -> state
            }
        }
    }

    /**
     * SET_ORIGIN → SET_REGION → FIND_LOCATION. PLACE_ASSETS and FIND_LOCATION are no-ops
     * (workspace / search back leave Planning via the UI).
     */
    fun goToPreviousSetupStep() {
        _uiState.update { state ->
            val previous = when (state.setupStep) {
                PlanningSetupStep.SET_ORIGIN -> PlanningSetupStep.SET_REGION
                PlanningSetupStep.SET_REGION -> PlanningSetupStep.FIND_LOCATION
                PlanningSetupStep.FIND_LOCATION,
                PlanningSetupStep.PLACE_ASSETS -> return@update state
            }
            applySetupStep(state, previous)
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
        if (viewOnlyBlocked()) return false
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
        if (viewOnlyBlocked()) return false
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
    fun moveObjectToGeographic(
        objectId: String,
        latitude: Double,
        longitude: Double,
        announce: Boolean = true
    ): Boolean {
        if (viewOnlyBlocked()) return false
        val layout = _uiState.value.currentLayout
        val anchor = layout.anchor
        if (!anchor.hasGeographicFix) {
            if (announce) {
                _uiState.update {
                    it.copy(userFeedbackMessage = "Geographic origin required to move assets on the map")
                }
            }
            return false
        }
        if (!MapLayoutProjection.isInsideRegionOrUnbounded(layout.regionBounds, latitude, longitude)) {
            if (announce) {
                _uiState.update {
                    it.copy(userFeedbackMessage = "Move rejected — outside selected region")
                }
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
        if (viewOnlyBlocked()) return false
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
        val userAtStart = scoped?.currentUserId
        val (layouts, deviceIds) = listSavedLayouts()
        // Someone signed in or out while the files were read: that newer reload wins.
        if (scoped != null && scoped?.currentUserId != userAtStart) return layouts
        _uiState.update { it.copy(savedLayouts = layouts, devicePlanIds = deviceIds) }
        return layouts
    }

    private val scoped: UserScopedLayoutRepository?
        get() = repository as? UserScopedLayoutRepository

    /** "My plans" first, then "This device" plans, plus the ids of the device plans. */
    private suspend fun listSavedLayouts(): Pair<List<Layout>, Set<String>> {
        val repo = scoped
        if (repo == null) {
            // Plain repository (tests, headless): everything behaves like a device plan.
            val all = repository.getAllLayouts()
            return all to all.map { it.id }.toSet()
        }
        val mine = repo.myLayouts()
        val device = repo.deviceLayouts()
        return (mine + device) to device.map { it.id }.toSet()
    }

    private var accountKnown = false

    /**
     * Called whenever the signed-in account changes (sign-in, sign-out, session expiry).
     * Clears the in-memory plan list and any open plan right away, then reloads the list for
     * the new user: their own plans plus this phone's device plans.
     */
    fun onAccountChanged(userId: String?) {
        val id = userId?.takeIf { it.isNotBlank() }
        if (accountKnown && id == _uiState.value.signedInUserId) return
        val firstCall = !accountKnown
        accountKnown = true
        scoped?.setUser(id)
        _uiState.update { state ->
            if (firstCall) {
                state.copy(signedInUserId = id)
            } else {
                state.copy(
                    signedInUserId = id,
                    savedLayouts = emptyList(),
                    devicePlanIds = emptySet(),
                    currentLayout = Layout(),
                    selectedObjectId = null,
                    isPersisted = false,
                    hasUnsavedChanges = false,
                    isViewOnly = false,
                    lastSavedTimestamp = null
                )
            }
        }
        loadSavedLayouts()
    }

    /** True when [layoutId] is a device plan that the signed-in user may only look at. */
    fun isViewOnlyPlan(layoutId: String): Boolean = scoped?.isViewOnly(layoutId) == true

    /**
     * Copies a device plan into the signed-in user's own plans ("<name> (copy)", new id).
     * The original is not changed. When that plan is open, the editable copy is opened instead.
     */
    suspend fun copyToMyPlansDirect(layoutId: String): Layout? {
        val repo = scoped
        if (repo == null || !repo.isSignedIn) {
            _uiState.update { it.copy(userFeedbackMessage = "Sign in to keep your own copy") }
            return null
        }
        val copy = repo.copyToMine(layoutId)
        if (copy == null) {
            _uiState.update { it.copy(userFeedbackMessage = "Could not copy this plan") }
            return null
        }
        val (all, deviceIds) = listSavedLayouts()
        val wasOpen = _uiState.value.currentLayout.id == layoutId
        _uiState.update { it.copy(savedLayouts = all, devicePlanIds = deviceIds) }
        if (wasOpen) loadLayout(copy)
        _uiState.update { it.copy(userFeedbackMessage = "Copied into your plans as '${copy.name}'") }
        return copy
    }

    fun copyToMyPlans(layoutId: String, onComplete: ((Layout?) -> Unit)? = null) {
        viewModelScope.launch {
            val copy = copyToMyPlansDirect(layoutId)
            onComplete?.invoke(copy)
        }
    }

    /** Refuses a change to a view-only plan and tells the user why. */
    private fun viewOnlyBlocked(): Boolean {
        if (!_uiState.value.isViewOnly) return false
        _uiState.update { it.copy(userFeedbackMessage = VIEW_ONLY_MESSAGE) }
        return true
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
        val viewOnly = isViewOnlyPlan(layout.id)
        _uiState.update { state ->
            val unselectedObjects = layout.objects.map { it.copy(isSelected = false) }
            val loaded = state.copy(
                currentLayout = layout.copy(objects = unselectedObjects),
                selectedObjectId = null,
                isPersisted = true,
                hasUnsavedChanges = false,
                cursorCoordinatesMeters = null,
                panOffset = Offset.Zero,
                zoom = CoordinateTransformer.DEFAULT_ZOOM,
                planningSurface = PlanningSurface.MAP,
                mapStyleError = null,
                isGeoOriginPanelExpanded = false,
                geoAccuracyWarning = null,
                userFeedbackMessage = if (viewOnly) {
                    "Viewing '${layout.name}' (view only)"
                } else {
                    "Loaded layout '${layout.name}'"
                },
                isViewOnly = viewOnly,
                isBrandNewLayout = false,
                setupSession = PlanningSetupSession(),
                placeSearch = PlaceSearchUiState()
            )
            val step = resolvePlanningSetupStep(
                layout = loaded.currentLayout,
                session = loaded.setupSession,
                isBrandNewLayout = false
            )
            applySetupStep(loaded, step)
        }
        locationController.stopUpdates()
    }

    /**
     * Creates a brand-new blank layout with an empty canvas and reset status.
     */
    fun createNewLayout(name: String = "Tactical Field Alpha") {
        awaitingAutoLocate = false
        val freshLayout = Layout(
            id = UUID.randomUUID().toString(),
            name = name,
            anchor = com.example.dhruvar.domain.model.Anchor(),
            objects = emptyList(),
            createdAtEpochMs = System.currentTimeMillis(),
            updatedAtEpochMs = System.currentTimeMillis()
        )
        _uiState.update { state ->
            applySetupStep(
                state = state.copy(
                    currentLayout = freshLayout,
                    selectedObjectId = null,
                    isPersisted = false,
                    hasUnsavedChanges = false,
                    cursorCoordinatesMeters = null,
                    panOffset = Offset.Zero,
                    zoom = CoordinateTransformer.DEFAULT_ZOOM,
                    planningSurface = PlanningSurface.MAP,
                    mapStyleError = null,
                    isGeoOriginPanelExpanded = false,
                    geoAccuracyWarning = null,
                    isViewOnly = false,
                    isBrandNewLayout = true,
                    setupSession = PlanningSetupSession(),
                    placeSearch = PlaceSearchUiState()
                ),
                step = PlanningSetupStep.FIND_LOCATION
            )
        }
        locationController.stopUpdates()
    }

    /**
     * Applies [step] and coerces map mode / surface. Does not derive the step from layout —
     * callers decide (resolver on load/clear, explicit advance/back otherwise).
     */
    private fun applySetupStep(state: PlanningUiState, step: PlanningSetupStep): PlanningUiState {
        val collapsedOrigin = if (step != PlanningSetupStep.SET_ORIGIN) {
            false
        } else {
            state.isGeoOriginPanelExpanded
        }
        val withStep = state.copy(
            setupStep = step,
            mapInteractionMode = coerceMapInteractionMode(step, state.mapInteractionMode),
            planningSurface = if (step != PlanningSetupStep.PLACE_ASSETS) {
                PlanningSurface.MAP
            } else {
                state.planningSurface
            },
            isGeoOriginPanelExpanded = collapsedOrigin,
            selectedObjectId = if (step == PlanningSetupStep.PLACE_ASSETS) {
                state.selectedObjectId
            } else {
                null
            },
            placeSearch = when (step) {
                PlanningSetupStep.FIND_LOCATION -> expandedFindLocationSearch(state.placeSearch)
                PlanningSetupStep.SET_REGION,
                PlanningSetupStep.SET_ORIGIN -> PlaceSearchUiState()
                PlanningSetupStep.PLACE_ASSETS -> state.placeSearch
            }
        )
        // No camera move here: the map already flew to the chosen place when it was picked (search
        // hit or GNSS fix). Moving it again on every step change threw away wherever the planner
        // had dragged the map to before tapping Continue.
        return withStep
    }

    /** Opens the search field without wiping a query the user already typed. */
    private fun expandedFindLocationSearch(current: PlaceSearchUiState): PlaceSearchUiState {
        if (current.isExpanded) return current
        return PlaceSearchUiState(
            isExpanded = true,
            error = if (placeSearch.isConfigured) null else SEARCH_NOT_CONFIGURED
        )
    }

    /**
     * After region/origin clear, drop back to the first incomplete step. Brand-new layouts stay
     * on Find location until [advanceSetupStep] runs.
     */
    private fun resyncSetupStep(state: PlanningUiState): PlanningUiState {
        if (state.isBrandNewLayout) {
            return applySetupStep(state, PlanningSetupStep.FIND_LOCATION)
        }
        val resolved = resolvePlanningSetupStep(
            layout = state.currentLayout,
            session = state.setupSession,
            isBrandNewLayout = false
        )
        return applySetupStep(state, resolved)
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
        if (viewOnlyBlocked()) return false
        val targetName = customName?.trim()?.ifEmpty { null } ?: _uiState.value.currentLayout.name
        val updated = _uiState.value.currentLayout.copy(
            name = targetName,
            updatedAtEpochMs = System.currentTimeMillis()
        )
        val success = repository.saveLayout(updated)
        if (success) {
            val (allLayouts, deviceIds) = listSavedLayouts()
            _uiState.update { state ->
                state.copy(
                    currentLayout = updated,
                    isPersisted = true,
                    hasUnsavedChanges = false,
                    lastSavedTimestamp = updated.updatedAtEpochMs,
                    savedLayouts = allLayouts,
                    devicePlanIds = deviceIds,
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
        if (isViewOnlyPlan(id)) {
            _uiState.update { it.copy(userFeedbackMessage = VIEW_ONLY_MESSAGE) }
            return null
        }
        val updated = repository.renameLayout(id, newName)
        if (updated != null) {
            val (all, deviceIds) = listSavedLayouts()
            _uiState.update { state ->
                val isCurrent = state.currentLayout.id == id
                val newCurrent = if (isCurrent) state.currentLayout.copy(name = updated.name) else state.currentLayout
                state.copy(
                    currentLayout = newCurrent,
                    savedLayouts = all,
                    devicePlanIds = deviceIds,
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
        if (isViewOnlyPlan(id)) {
            _uiState.update { it.copy(userFeedbackMessage = VIEW_ONLY_MESSAGE) }
            return false
        }
        val success = repository.deleteLayout(id)
        if (success) {
            val (all, deviceIds) = listSavedLayouts()
            _uiState.update { state ->
                val isCurrent = state.currentLayout.id == id
                state.copy(
                    isPersisted = if (isCurrent) false else state.isPersisted,
                    hasUnsavedChanges = if (isCurrent) true else state.hasUnsavedChanges,
                    savedLayouts = all,
                    devicePlanIds = deviceIds,
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
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
        moveObject(objectId, newX, newZ)
    }

    fun updateSelectedObjectPosition(newX: Float, newZ: Float) {
        if (viewOnlyBlocked()) return
        val selectedId = _uiState.value.selectedObjectId ?: return
        moveObject(selectedId, newX, newZ)
    }

    fun updateObjectDimensions(
        objectId: String,
        newWidth: Float,
        newLength: Float,
        newHeight: Float? = null
    ) {
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
        val selectedId = _uiState.value.selectedObjectId ?: return
        updateObjectDimensions(selectedId, newWidth, newLength, newHeight)
    }

    fun rotateObject(objectId: String, degreesDelta: Float) {
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
        val selectedId = _uiState.value.selectedObjectId ?: return
        rotateObject(selectedId, degreesDelta)
    }

    fun setSelectedObjectRotation(absoluteDegrees: Float) {
        if (viewOnlyBlocked()) return
        val selectedId = _uiState.value.selectedObjectId ?: return
        setObjectRotation(selectedId, absoluteDegrees)
    }

    fun duplicateSelectedObject(offsetXMeters: Float = 1.5f, offsetZMeters: Float = 1.5f) {
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
        _uiState.update { state ->
            val count = state.currentLayout.objects.count { it.assetType == assetType } + 1
            val newObj = newSizedObject(
                state = state,
                assetType = assetType,
                name = "${assetType.displayName} $count",
                x = xMeters,
                z = zMeters
            )
            val updatedList = state.currentLayout.objects.map { it.copy(isSelected = false) } + newObj
            state.copy(
                selectedAssetType = assetType,
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                // A newly placed asset is not selected: the properties card opens only on touch.
                selectedObjectId = null
            )
        }
    }

    /** Side (m) of the current region, or the picked side when no region exists yet. */
    private fun effectiveRegionSide(state: PlanningUiState): Double =
        state.currentLayout.regionBounds
            ?.let { RegionDraftGeometry.sideLengthsMeters(it).let { (w, h) -> maxOf(w, h) } }
            ?: state.regionSideMeters

    /** New object at its real-world size times the factor for the current region. */
    private fun newSizedObject(
        state: PlanningUiState,
        assetType: AssetType,
        name: String,
        x: Float,
        z: Float
    ): LayoutObject {
        val k = AssetSizeConfig.regionSizeFactor(effectiveRegionSide(state))
        return LayoutObject(
            assetType = assetType,
            name = name,
            x = x,
            z = z,
            rotationDegrees = 0.0f,
            widthMeters = assetType.defaultWidthMeters * k,
            lengthMeters = assetType.defaultLengthMeters * k,
            heightMeters = assetType.defaultHeightMeters * k,
            scale = 1.0f,
            isSelected = true
        )
    }

    /**
     * Picks how much area to visualize (side of the square around the operator). When a region
     * already exists it is resized about its own center; shrinking is refused if that would leave
     * a placed asset outside.
     */
    fun setRegionSideMeters(sideMeters: Double) {
        if (viewOnlyBlocked()) return
        val side = sideMeters.coerceIn(RegionDraftGeometry.MIN_SIDE_METERS, 300.0)
        val state = _uiState.value
        val current = state.currentLayout.regionBounds
        if (current == null) {
            _uiState.update { it.copy(regionSideMeters = side) }
            return
        }
        val center = com.example.dhruvar.domain.spatial.RegionWorkspaceGeometry.centroid(current)
        val bounds = RegionDraftGeometry.squareAround(center.latitude, center.longitude, side)
            ?: return
        val anchor = state.currentLayout.anchor
        val strandsAsset = anchor.hasGeographicFix && state.currentLayout.objects.any { obj ->
            val at = com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
                .metersOffsetToLatLng(anchor, obj.x, obj.z)
            !bounds.contains(at.latitude, at.longitude)
        }
        if (strandsAsset) {
            _uiState.update {
                it.copy(userFeedbackMessage = "Move or remove assets near the edge before shrinking the area")
            }
            return
        }
        // Placed assets follow the region: rescale them by the change in size factor.
        val ratio = AssetSizeConfig.regionSizeFactor(side) /
            AssetSizeConfig.regionSizeFactor(effectiveRegionSide(state))
        _uiState.update { s ->
            s.copy(
                regionSideMeters = side,
                currentLayout = s.currentLayout.copy(
                    objects = s.currentLayout.objects.map {
                        it.copy(
                            widthMeters = it.widthMeters * ratio,
                            lengthMeters = it.lengthMeters * ratio,
                            heightMeters = it.heightMeters * ratio
                        )
                    }
                )
            )
        }
        setRegionBounds(bounds)
        MapCameraBridge.moveTo(
            latitude = center.latitude,
            longitude = center.longitude,
            zoom = AUTO_REGION_ZOOM
        )
    }

    fun addAssetAt(xMeters: Float, zMeters: Float) {
        if (viewOnlyBlocked()) return
        val assetType = _uiState.value.selectedAssetType
        val count = _uiState.value.currentLayout.objects.count { it.assetType == assetType } + 1
        _uiState.update { state ->
            val newObj = newSizedObject(
                state = state,
                assetType = assetType,
                name = "${assetType.displayName} $count",
                x = xMeters,
                z = zMeters
            )
            val updatedList = state.currentLayout.objects.map { it.copy(isSelected = false) } + newObj
            state.copy(
                hasUnsavedChanges = true,
                currentLayout = state.currentLayout.copy(
                    objects = updatedList,
                    updatedAtEpochMs = System.currentTimeMillis()
                ),
                // Not selected on placement: the card opens only when the user touches an asset.
                selectedObjectId = null
            )
        }
    }

    fun deleteSelectedObject() {
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
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
        if (viewOnlyBlocked()) return
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
        const val VIEW_ONLY_MESSAGE =
            "View only: this plan is saved on this phone. Make a copy in your plans to change it."

        const val SITE_LEVEL_ACCURACY_WARNING =
            "Site-level only; relative meter placement remains precise"

        /** Long enough that ordinary typing issues one request per word, not per letter. */
        const val SEARCH_DEBOUNCE_MILLIS = 250L
        const val SEARCH_NOT_CONFIGURED = "Map search server not configured"
        const val SEARCH_UNAVAILABLE = "Search unavailable — check the map server"
        const val CURRENT_LOCATION_NAME = "Current location (GNSS)"

        /** A satellite fix this tight ends the search; anything looser keeps refining. */
        const val AUTO_LOCATE_GOOD_ACCURACY_METERS = 15f

        /** Zoom at which a 250 m region fills a phone-width map. */
        const val AUTO_REGION_ZOOM = 17.5
        const val ADVANCE_NEEDS_REGION = "Set a region to continue"
        const val ADVANCE_NEEDS_ORIGIN = "Set geographic origin to continue"
        const val GPS_OUTSIDE_REGION =
            "GPS is outside the selected region — move closer or tap on the map"
        const val ORIGIN_MUST_BE_INSIDE_REGION = "Origin must be inside the selected region"
        const val ORIGIN_OUTSIDE_NEW_REGION = "Origin is outside the new region — set it again"
        const val CHANGE_REGION_KEEPS_ASSETS =
            "Changing the region does not delete assets. Placements outside the new box cannot be moved until they are inside."
        const val CHANGE_ORIGIN_KEEPS_METERS =
            "Assets stay at the same meter offsets. A new origin moves them on the map."
    }
}
