package com.example.dhruvar.domain.spatial

import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject

/**
 * Writes the layout origin to the center of [bounds].
 *
 * Asset meter offsets are rewritten from the previous geographic origin so each
 * asset stays on the same ground. Heading is kept. A layout that had no
 * geographic fix keeps its meter offsets as-is, now measured from the center.
 */
fun Layout.withOriginAtRegionCenter(bounds: GeoBounds, updatedAtEpochMs: Long): Layout {
    val center = RegionWorkspaceGeometry.centroid(bounds)
    val nextAnchor = anchor.copy(
        latitude = center.latitude,
        longitude = center.longitude,
        headingDegrees = anchor.headingDegrees ?: 0f,
        horizontalAccuracyMeters = null
    )
    val shifted = if (anchor.hasGeographicFix) {
        objects.map { it.rebasedOnto(from = anchor, to = nextAnchor) }
    } else {
        objects
    }
    return copy(
        regionBounds = bounds,
        anchor = nextAnchor,
        objects = shifted,
        updatedAtEpochMs = updatedAtEpochMs
    )
}

private fun LayoutObject.rebasedOnto(from: Anchor, to: Anchor): LayoutObject {
    val geographic = GeographicCoordinateConverter.metersOffsetToLatLng(from, x, z)
    val meters = GeographicCoordinateConverter.latLngToMetersOffset(to, geographic)
    return copy(x = meters.first, z = meters.second)
}
