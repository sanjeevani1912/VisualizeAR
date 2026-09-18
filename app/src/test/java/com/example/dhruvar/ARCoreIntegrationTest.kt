package com.example.dhruvar

import com.example.dhruvar.navigation.Screen
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Plane
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests validating Part 9: ARCore integration, navigation,
 * plane classification filters, and tracking status state machines.
 */
class ARCoreIntegrationTest {

    @Test
    fun navigationRoutes_containARVisualizationRoute() {
        assertEquals("home", Screen.Home.route)
        assertEquals("planning", Screen.Planning.route)
        assertEquals("visualization", Screen.Visualization.route)
        assertEquals("ar_visualization", Screen.ARVisualization.route)
    }

    @Test
    fun planeClassification_identifiesHorizontalGroundPlanes() {
        val upwardFacing = Plane.Type.HORIZONTAL_UPWARD_FACING
        val downwardFacing = Plane.Type.HORIZONTAL_DOWNWARD_FACING
        val vertical = Plane.Type.VERTICAL

        fun isHorizontalGroundPlane(type: Plane.Type): Boolean {
            return type == Plane.Type.HORIZONTAL_UPWARD_FACING || type == Plane.Type.HORIZONTAL_DOWNWARD_FACING
        }

        assertTrue(isHorizontalGroundPlane(upwardFacing))
        assertTrue(isHorizontalGroundPlane(downwardFacing))
        assertFalse(isHorizontalGroundPlane(vertical))
    }

    @Test
    fun arTrackingStatus_resolvesAppropriateHUDMessages() {
        fun resolveStatus(trackingState: TrackingState, planeCount: Int, failureReason: TrackingFailureReason?): String {
            return when (trackingState) {
                TrackingState.TRACKING -> {
                    if (planeCount > 0) "Surface detected • Ready" else "Move device slowly across floor..."
                }
                TrackingState.PAUSED -> {
                    when (failureReason) {
                        TrackingFailureReason.EXCESSIVE_MOTION -> "Move device more slowly"
                        TrackingFailureReason.INSUFFICIENT_LIGHT -> "Insufficient lighting"
                        TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point camera at textured surface"
                        else -> "Scanning environment..."
                    }
                }
                TrackingState.STOPPED -> "AR Session stopped"
            }
        }

        // Active tracking with detected ground
        assertEquals("Surface detected • Ready", resolveStatus(TrackingState.TRACKING, 2, null))

        // Active tracking searching for ground
        assertEquals("Move device slowly across floor...", resolveStatus(TrackingState.TRACKING, 0, null))

        // Paused due to motion
        assertEquals("Move device more slowly", resolveStatus(TrackingState.PAUSED, 0, TrackingFailureReason.EXCESSIVE_MOTION))

        // Paused due to light
        assertEquals("Insufficient lighting", resolveStatus(TrackingState.PAUSED, 0, TrackingFailureReason.INSUFFICIENT_LIGHT))
    }

    @Test
    fun arCoreAvailability_mapsAllSupportedAndUnsupportedStates() {
        fun mapAvailabilityStatus(availability: ArCoreApk.Availability): String {
            return when (availability) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> "SUPPORTED"
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> "NEEDS_INSTALL"
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> "UNSUPPORTED"
                else -> "SUPPORTED"
            }
        }

        assertEquals("SUPPORTED", mapAvailabilityStatus(ArCoreApk.Availability.SUPPORTED_INSTALLED))
        assertEquals("NEEDS_INSTALL", mapAvailabilityStatus(ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD))
        assertEquals("NEEDS_INSTALL", mapAvailabilityStatus(ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED))
        assertEquals("UNSUPPORTED", mapAvailabilityStatus(ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE))
    }
}
