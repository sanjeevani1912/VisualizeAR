package com.example.dhruvar.domain.location

/**
 * A single GNSS / fused-location sample in WGS84.
 */
data class DeviceLocationFix(
    val latitude: Double,
    val longitude: Double,
    val horizontalAccuracyMeters: Float? = null,
    val altitudeMeters: Double? = null,
    val timestampEpochMs: Long = System.currentTimeMillis()
) {
    val isFinite: Boolean
        get() = latitude.isFinite() &&
            longitude.isFinite() &&
            latitude in -90.0..90.0 &&
            (horizontalAccuracyMeters == null || horizontalAccuracyMeters.isFinite())
}

/**
 * Why a usable fix is unavailable (or [NONE] when location flow is healthy).
 */
enum class LocationAvailability {
    /** Updates running / last fix present without a hard error. */
    AVAILABLE,
    /** Runtime location permission not granted. */
    PERMISSION_MISSING,
    /** Location services / GPS appear disabled. */
    GPS_DISABLED,
    /** Permission OK but no sample yet. */
    NO_FIX,
    /** Play Services / provider failure. */
    PROVIDER_ERROR
}

/**
 * Readiness chip for stamping a geographic origin from the live device fix.
 *
 * Threshold: [POOR_ACCURACY_METERS] — above this, setting is allowed with a site-level warning.
 */
enum class GeoOriginReadiness {
    NO_FIX,
    ACCURACY_POOR,
    READY
}

/**
 * Snapshot of the device location pipeline for UI / ViewModel.
 */
data class DeviceLocationState(
    val permissionGranted: Boolean = false,
    val isUpdating: Boolean = false,
    val lastFix: DeviceLocationFix? = null,
    val availability: LocationAvailability = LocationAvailability.PERMISSION_MISSING,
    val statusMessage: String? = null
) {
    val hasFix: Boolean
        get() = lastFix?.isFinite == true

    val readiness: GeoOriginReadiness
        get() {
            val fix = lastFix
            if (fix == null || !fix.isFinite) return GeoOriginReadiness.NO_FIX
            val accuracy = fix.horizontalAccuracyMeters
            return if (accuracy != null && accuracy > POOR_ACCURACY_METERS) {
                GeoOriginReadiness.ACCURACY_POOR
            } else {
                GeoOriginReadiness.READY
            }
        }

    companion object {
        const val POOR_ACCURACY_METERS: Float = 15f
    }
}

/**
 * Mockable location data source. Android fused-provider details stay behind this interface
 * so JVM unit tests can inject a fake.
 */
interface DeviceLocationController {
    val state: kotlinx.coroutines.flow.StateFlow<DeviceLocationState>

    /** Notify the controller of the latest runtime permission result. */
    fun onPermissionResult(granted: Boolean)

    /** Begin high-accuracy updates while the user is setting origin. */
    fun startHighAccuracyUpdates()

    /** Stop updates (lifecycle pause / panel collapsed) to save battery. */
    fun stopUpdates()

    /** Request a one-shot refresh of the current location. */
    fun refreshLocation()
}
