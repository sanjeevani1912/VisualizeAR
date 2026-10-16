package com.example.dhruvar.domain.ar

import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.LatLng

/**
 * Pre-AR geographic readiness relative to the layout's saved geographic anchor.
 *
 * GPS is used only to guide the user to the site — not to drag AR meshes every frame.
 */
enum class ArGeoProximityStatus {
    /** Layout has no WGS84 fix — AR uses local-only meter offsets (legacy behaviour). */
    NO_GEO,
    /** Device location unavailable while layout has a geo fix. */
    NO_DEVICE_FIX,
    /** Device is farther than [ArGeoGuidance.DEFAULT_NEAR_ORIGIN_METERS] from the anchor. */
    TOO_FAR,
    /** Device is within the near-origin threshold. */
    NEAR_ORIGIN
}

/**
 * Snapshot shown on the AR geo briefing panel.
 */
data class ArGeoGuidance(
    val status: ArGeoProximityStatus,
    val anchorLatitude: Double? = null,
    val anchorLongitude: Double? = null,
    val anchorHeadingDegrees: Float? = null,
    val distanceMeters: Double? = null,
    val bearingDegrees: Float? = null,
    val deviceAccuracyMeters: Float? = null,
    val nearOriginThresholdMeters: Double = DEFAULT_NEAR_ORIGIN_METERS
) {
    val statusLabel: String
        get() = when (status) {
            ArGeoProximityStatus.NO_GEO -> "NO GEO"
            ArGeoProximityStatus.NO_DEVICE_FIX -> "NO DEVICE FIX"
            ArGeoProximityStatus.TOO_FAR -> "TOO FAR"
            ArGeoProximityStatus.NEAR_ORIGIN -> "NEAR ORIGIN"
        }

    /** One-line chip after calibration. Details stay behind expand. */
    fun compactChipLabel(): String = when (status) {
        ArGeoProximityStatus.NO_GEO -> "Geo · Local"
        ArGeoProximityStatus.NO_DEVICE_FIX -> "Geo · No fix"
        ArGeoProximityStatus.TOO_FAR -> "Geo · Far"
        ArGeoProximityStatus.NEAR_ORIGIN -> "Geo · Near"
    }

    companion object {
        const val DEFAULT_NEAR_ORIGIN_METERS: Double = 50.0

        /**
         * Builds guidance from the layout [Anchor] and an optional live GNSS [DeviceLocationFix].
         */
        fun from(
            anchor: Anchor,
            deviceFix: DeviceLocationFix?,
            nearOriginThresholdMeters: Double = DEFAULT_NEAR_ORIGIN_METERS
        ): ArGeoGuidance {
            if (!anchor.hasGeographicFix) {
                return ArGeoGuidance(status = ArGeoProximityStatus.NO_GEO)
            }
            val lat = anchor.latitude!!
            val lng = anchor.longitude!!
            if (deviceFix == null || !deviceFix.isFinite) {
                return ArGeoGuidance(
                    status = ArGeoProximityStatus.NO_DEVICE_FIX,
                    anchorLatitude = lat,
                    anchorLongitude = lng,
                    anchorHeadingDegrees = anchor.headingDegrees,
                    nearOriginThresholdMeters = nearOriginThresholdMeters
                )
            }
            val distance = distanceMetersToAnchor(lat, lng, deviceFix.latitude, deviceFix.longitude)
            val bearing = bearingDegreesToAnchor(lat, lng, deviceFix.latitude, deviceFix.longitude)
            val status = if (distance > nearOriginThresholdMeters) {
                ArGeoProximityStatus.TOO_FAR
            } else {
                ArGeoProximityStatus.NEAR_ORIGIN
            }
            return ArGeoGuidance(
                status = status,
                anchorLatitude = lat,
                anchorLongitude = lng,
                anchorHeadingDegrees = anchor.headingDegrees,
                distanceMeters = distance,
                bearingDegrees = bearing,
                deviceAccuracyMeters = deviceFix.horizontalAccuracyMeters,
                nearOriginThresholdMeters = nearOriginThresholdMeters
            )
        }

        fun distanceMetersToAnchor(
            anchorLat: Double,
            anchorLng: Double,
            deviceLat: Double,
            deviceLng: Double
        ): Double {
            return GeographicCoordinateConverter.distanceMeters(
                LatLng(deviceLat, deviceLng),
                LatLng(anchorLat, anchorLng)
            )
        }

        fun bearingDegreesToAnchor(
            anchorLat: Double,
            anchorLng: Double,
            deviceLat: Double,
            deviceLng: Double
        ): Float {
            return GeographicCoordinateConverter.bearingDegrees(
                LatLng(deviceLat, deviceLng),
                LatLng(anchorLat, anchorLng)
            )
        }
    }
}
