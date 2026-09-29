package com.example.dhruvar.domain.model

/**
 * Which planning surface is active. MapLibre is primary for geo layouts;
 * [METRIC_GRID] retains the existing meter canvas for precise relative editing.
 */
enum class PlanningSurface {
    MAP,
    METRIC_GRID
}

/**
 * Interaction mode for the MapLibre planning surface.
 */
enum class MapInteractionMode {
    /** Pan / zoom the map freely. */
    NAVIGATE,
    /** Confirm visible viewport (or clear) as [GeoBounds]. */
    SELECT_REGION,
    /** Tap to set / move the geographic anchor. */
    SET_ANCHOR,
    /** Tap to place the currently selected [AssetType]. */
    PLACE_ASSET,
    /** Tap to select; tap again (empty ground) to move the selection. */
    EDIT
}
