package com.example.dhruvar

import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.LocationAvailability
import com.example.dhruvar.ui.components.gnssStatusText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GnssStatusTextTest {

    @Test
    fun permissionMissing_asksForPreciseLocation() {
        val text = gnssStatusText(DeviceLocationState(availability = LocationAvailability.PERMISSION_MISSING))
        assertTrue(text.contains("precise location", ignoreCase = true))
    }

    @Test
    fun gpsDisabled_asksToTurnOnGps() {
        val text = gnssStatusText(DeviceLocationState(availability = LocationAvailability.GPS_DISABLED))
        assertTrue(text.contains("GPS"))
    }

    @Test
    fun searching_showsSatellitesInView() {
        val text = gnssStatusText(
            DeviceLocationState(
                permissionGranted = true,
                isUpdating = true,
                availability = LocationAvailability.NO_FIX,
                satellitesInView = 7
            )
        )
        assertEquals("Searching for satellites… 7 in view", text)
    }

    @Test
    fun networkFallbackFix_isLabelledApproximate() {
        val text = gnssStatusText(
            DeviceLocationState(
                permissionGranted = true,
                availability = LocationAvailability.AVAILABLE,
                lastFix = DeviceLocationFix(
                    28.6, 77.2, horizontalAccuracyMeters = 120f, satelliteDerived = false
                )
            )
        )
        assertEquals("Approximate position ±120 m (network) · waiting for satellites", text)
    }

    @Test
    fun withFix_showsAccuracyAndSatellitesUsed() {
        val text = gnssStatusText(
            DeviceLocationState(
                permissionGranted = true,
                availability = LocationAvailability.AVAILABLE,
                lastFix = DeviceLocationFix(28.6, 77.2, horizontalAccuracyMeters = 4.4f),
                satellitesInView = 12,
                satellitesInFix = 8
            )
        )
        assertEquals("GNSS fix ±4 m · 8/12 satellites", text)
    }
}
