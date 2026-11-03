package com.example.dhruvar.domain.model

import com.example.dhruvar.domain.spatial.RegionDraftGeometry

/**
 * Session-only map location chosen during Find location. Never written to layout JSON.
 */
data class ChosenMapLocation(
    val latitude: Double,
    val longitude: Double,
    val displayName: String,
    val zoom: Double? = null
)

/**
 * Ephemeral guided-setup session. Survives back/forward inside Planning; cleared on New Plan.
 */
data class PlanningSetupSession(
    val chosenLocation: ChosenMapLocation? = null
)

enum class PlanningSetupStep {
    FIND_LOCATION,
    SET_REGION,
    SET_ORIGIN,
    PLACE_ASSETS
}

fun PlanningSetupStep.planningScreenTitle(): String = when (this) {
    PlanningSetupStep.FIND_LOCATION -> "Find location"
    PlanningSetupStep.SET_REGION -> "Set region"
    PlanningSetupStep.SET_ORIGIN -> "Set origin"
    PlanningSetupStep.PLACE_ASSETS -> "Planning"
}

/** Null on the asset workspace — that step has no progress bar. */
fun PlanningSetupStep.progressSubtitle(): String? = when (this) {
    PlanningSetupStep.FIND_LOCATION -> "Step 1 of 3 · Find location"
    PlanningSetupStep.SET_REGION -> "Step 2 of 3 · Set region"
    PlanningSetupStep.SET_ORIGIN -> "Step 3 of 3 · Set origin"
    PlanningSetupStep.PLACE_ASSETS -> null
}


/** Continue on SET_ORIGIN is for an origin that already exists (legacy / stepped back). */
fun canContinueSetOrigin(hasGeographicFix: Boolean): Boolean = hasGeographicFix

const val AR_REQUIRES_REGION_AND_ORIGIN = "Set region and origin before AR"

/** AR needs a confirmed box and a geographic origin. Zero assets is still allowed. */
fun canEnterAr(layout: Layout): Boolean =
    layout.regionBounds != null && layout.anchor.hasGeographicFix

/**
 * Confirm on SET_REGION is enabled for a valid live draft, or for an already committed box
 * ("Continue with current region").
 */
fun canConfirmSetupRegion(
    draftBounds: GeoBounds?,
    committedBounds: GeoBounds?
): Boolean {
    if (draftBounds != null) {
        return RegionDraftGeometry.rejectionReason(draftBounds) == null
    }
    return committedBounds != null
}

fun PlaceSearchHit.toChosenMapLocation(zoom: Double? = null): ChosenMapLocation =
    ChosenMapLocation(
        latitude = latitude,
        longitude = longitude,
        displayName = displayName,
        zoom = zoom
    )

fun PlanningSetupStep.handlesSystemBackInternally(): Boolean =
    this == PlanningSetupStep.SET_REGION || this == PlanningSetupStep.SET_ORIGIN

/**
 * Modes a step may legally hold. [PlanningSetupStep.SET_ORIGIN] includes [MapInteractionMode.SET_ANCHOR]
 * so the existing origin sheet can still switch into tap-to-place; the step still *enters* on Navigate.
 */
fun allowedModes(step: PlanningSetupStep): Set<MapInteractionMode> = when (step) {
    PlanningSetupStep.FIND_LOCATION -> setOf(MapInteractionMode.NAVIGATE)
    PlanningSetupStep.SET_REGION -> setOf(MapInteractionMode.SELECT_REGION)
    PlanningSetupStep.SET_ORIGIN -> setOf(
        MapInteractionMode.NAVIGATE,
        MapInteractionMode.SET_ANCHOR
    )
    PlanningSetupStep.PLACE_ASSETS -> setOf(
        MapInteractionMode.NAVIGATE,
        MapInteractionMode.PLACE_ASSET,
        MapInteractionMode.EDIT
    )
}

fun defaultModeFor(step: PlanningSetupStep): MapInteractionMode = when (step) {
    PlanningSetupStep.FIND_LOCATION -> MapInteractionMode.NAVIGATE
    PlanningSetupStep.SET_REGION -> MapInteractionMode.SELECT_REGION
    PlanningSetupStep.SET_ORIGIN -> MapInteractionMode.NAVIGATE
    PlanningSetupStep.PLACE_ASSETS -> MapInteractionMode.NAVIGATE
}

fun coerceMapInteractionMode(
    step: PlanningSetupStep,
    requested: MapInteractionMode
): MapInteractionMode {
    val allowed = allowedModes(step)
    return if (requested in allowed) requested else defaultModeFor(step)
}

/** Region drag overlay is a SET_REGION tool. Change region leaves the workspace to use it. */
fun showsRegionDraftOverlay(
    step: PlanningSetupStep,
    mode: MapInteractionMode,
    surface: PlanningSurface
): Boolean {
    if (surface != PlanningSurface.MAP) return false
    return step == PlanningSetupStep.SET_REGION
}

/**
 * Derives the guided-setup step from layout + session.
 *
 * [isBrandNewLayout] always yields [PlanningSetupStep.FIND_LOCATION], ignoring leftover session
 * chrome. Region confirm does **not** auto-advance — that is Phase 03.
 */
fun resolvePlanningSetupStep(
    layout: Layout,
    session: PlanningSetupSession,
    isBrandNewLayout: Boolean
): PlanningSetupStep {
    if (isBrandNewLayout) return PlanningSetupStep.FIND_LOCATION

    val region = layout.regionBounds
    val hasFix = layout.anchor.hasGeographicFix

    if (region == null) {
        // Legacy: origin without a region still has to pick a box.
        return if (session.chosenLocation != null || hasFix) {
            PlanningSetupStep.SET_REGION
        } else {
            PlanningSetupStep.FIND_LOCATION
        }
    }

    if (!hasFix) return PlanningSetupStep.SET_ORIGIN
    return PlanningSetupStep.PLACE_ASSETS
}
