package com.example.dhruvar.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.dhruvar.domain.location.DeviceLocationController
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.LocationAvailability
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * [DeviceLocationController] that reads the device's own GNSS receiver through the Android
 * platform [LocationManager]. Every constellation the chip tracks (GPS, GLONASS, Galileo,
 * BeiDou, NavIC, QZSS, SBAS) counts toward the fix.
 *
 * Satellites ([LocationManager.GPS_PROVIDER]) always win. If no satellite fix arrives within
 * [NETWORK_FALLBACK_DELAY_MS] (indoors, cold start, emulator) the platform network provider is
 * also listened to, so detection still returns an approximate position, flagged
 * [DeviceLocationFix.satelliteDerived] = false. A later satellite fix replaces it.
 * Google Play Services is not used. Mock locations are ignored.
 *
 * Updates run only between [startHighAccuracyUpdates] and [stopUpdates].
 */
class PlatformGnssLocationController(
    context: Context
) : DeviceLocationController {

    private val appContext = context.applicationContext
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(
        DeviceLocationState(
            permissionGranted = hasFinePermission(),
            availability = if (hasFinePermission()) {
                LocationAvailability.NO_FIX
            } else {
                LocationAvailability.PERMISSION_MISSING
            }
        )
    )
    override val state: StateFlow<DeviceLocationState> = _state.asStateFlow()

    private var updatesActive = false
    private var listening = false

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            publishLocation(location)
        }

        override fun onProviderEnabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER && updatesActive) {
                _state.update {
                    it.copy(availability = availabilityFor(it.lastFix), statusMessage = null)
                }
            }
        }

        override fun onProviderDisabled(provider: String) {
            if (provider == LocationManager.GPS_PROVIDER) {
                _state.update {
                    it.copy(
                        availability = LocationAvailability.GPS_DISABLED,
                        statusMessage = GPS_OFF_MESSAGE,
                        satellitesInView = 0,
                        satellitesInFix = 0
                    )
                }
            }
        }
    }

    private val gnssCallback = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(status: GnssStatus) {
            var inView = 0
            var inFix = 0
            for (i in 0 until status.satelliteCount) {
                inView++
                if (status.usedInFix(i)) inFix++
            }
            _state.update { it.copy(satellitesInView = inView, satellitesInFix = inFix) }
        }

        override fun onStopped() {
            _state.update { it.copy(satellitesInView = 0, satellitesInFix = 0) }
        }
    }

    override fun onPermissionResult(granted: Boolean) {
        val ok = granted && hasFinePermission()
        _state.update { current ->
            current.copy(
                permissionGranted = ok,
                availability = when {
                    !ok -> LocationAvailability.PERMISSION_MISSING
                    !isGpsEnabled() -> LocationAvailability.GPS_DISABLED
                    else -> availabilityFor(current.lastFix)
                },
                statusMessage = when {
                    !ok -> PERMISSION_MESSAGE
                    !isGpsEnabled() -> GPS_OFF_MESSAGE
                    else -> null
                },
                lastFix = if (ok) current.lastFix else null,
                isUpdating = if (ok) current.isUpdating else false
            )
        }
        if (ok && updatesActive) {
            startListening()
        } else if (!ok) {
            stopListening()
        }
    }

    override fun startHighAccuracyUpdates() {
        updatesActive = true
        if (!hasFinePermission()) {
            _state.update {
                it.copy(
                    permissionGranted = false,
                    availability = LocationAvailability.PERMISSION_MISSING,
                    statusMessage = PERMISSION_MESSAGE,
                    isUpdating = false
                )
            }
            return
        }
        if (!isGpsEnabled()) {
            _state.update {
                it.copy(
                    permissionGranted = true,
                    availability = LocationAvailability.GPS_DISABLED,
                    statusMessage = GPS_OFF_MESSAGE,
                    isUpdating = false
                )
            }
            return
        }
        startListening()
        refreshLocation()
    }

    override fun stopUpdates() {
        updatesActive = false
        stopListening()
    }

    /**
     * Publishes the receiver's last satellite fix when it is recent. Otherwise the running
     * listener delivers the next live fix; this never falls back to any other provider.
     */
    @SuppressLint("MissingPermission")
    override fun refreshLocation() {
        if (!hasFinePermission()) {
            onPermissionResult(false)
            return
        }
        if (!isGpsEnabled()) {
            _state.update {
                it.copy(availability = LocationAvailability.GPS_DISABLED, statusMessage = GPS_OFF_MESSAGE)
            }
            return
        }
        if (!listening && updatesActive) startListening()
        val recent = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: SecurityException) {
            null
        }
        if (recent != null && isFresh(recent) && !isMock(recent)) {
            publishLocation(recent)
        } else if (_state.value.lastFix == null) {
            _state.update {
                it.copy(availability = LocationAvailability.NO_FIX, statusMessage = SEARCHING_MESSAGE)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startListening() {
        if (listening || !hasFinePermission()) return
        try {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                UPDATE_INTERVAL_MS,
                0f,
                locationListener,
                Looper.getMainLooper()
            )
            registerGnssStatus()
            listening = true
            scheduleNetworkFallback()
            _state.update {
                it.copy(
                    isUpdating = true,
                    permissionGranted = true,
                    availability = availabilityFor(it.lastFix),
                    statusMessage = if (it.lastFix == null) SEARCHING_MESSAGE else null
                )
            }
        } catch (e: SecurityException) {
            _state.update {
                it.copy(
                    permissionGranted = false,
                    availability = LocationAvailability.PERMISSION_MISSING,
                    statusMessage = PERMISSION_MESSAGE,
                    isUpdating = false
                )
            }
        } catch (e: RuntimeException) {
            _state.update {
                it.copy(
                    availability = LocationAvailability.PROVIDER_ERROR,
                    statusMessage = e.message ?: "GNSS receiver error",
                    isUpdating = false
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun registerGnssStatus() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                locationManager.registerGnssStatusCallback(
                    ContextCompat.getMainExecutor(appContext),
                    gnssCallback
                )
            } else {
                @Suppress("DEPRECATION")
                locationManager.registerGnssStatusCallback(gnssCallback, null)
            }
        } catch (_: SecurityException) {
            // Counts are informational; the fix itself still arrives through the listener.
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val networkFallback = Runnable { startNetworkFallback() }

    private fun scheduleNetworkFallback() {
        mainHandler.removeCallbacks(networkFallback)
        mainHandler.postDelayed(networkFallback, NETWORK_FALLBACK_DELAY_MS)
    }

    /** Also listens to the platform network provider when no satellite fix has arrived yet. */
    @SuppressLint("MissingPermission")
    private fun startNetworkFallback() {
        val satelliteFix = _state.value.lastFix?.satelliteDerived == true
        if (!updatesActive || !listening || satelliteFix) return
        try {
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_INTERVAL_MS,
                    0f,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
        } catch (_: SecurityException) {
            // Satellite listening keeps working.
        } catch (_: RuntimeException) {
        }
    }

    private fun stopListening() {
        mainHandler.removeCallbacks(networkFallback)
        if (listening) {
            locationManager.removeUpdates(locationListener)
            locationManager.unregisterGnssStatusCallback(gnssCallback)
            listening = false
        }
        _state.update { it.copy(isUpdating = false, satellitesInView = 0, satellitesInFix = 0) }
    }

    private fun publishLocation(location: Location) {
        if (isMock(location)) return
        val fromSatellites = location.provider == LocationManager.GPS_PROVIDER
        if (!fromSatellites && location.provider != LocationManager.NETWORK_PROVIDER) return
        // A network position never replaces a satellite one.
        if (!fromSatellites && _state.value.lastFix?.satelliteDerived == true) return
        val fix = DeviceLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            timestampEpochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis(),
            satelliteDerived = fromSatellites
        )
        if (!fix.isFinite) return
        _state.update {
            it.copy(
                lastFix = fix,
                permissionGranted = true,
                availability = LocationAvailability.AVAILABLE,
                statusMessage = null
            )
        }
    }

    private fun availabilityFor(fix: DeviceLocationFix?) =
        if (fix != null) LocationAvailability.AVAILABLE else LocationAvailability.NO_FIX

    private fun isFresh(location: Location): Boolean {
        val ageMs = System.currentTimeMillis() - location.time
        return location.time > 0L && ageMs in 0..MAX_LAST_KNOWN_AGE_MS
    }

    private fun isMock(location: Location): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

    private fun hasFinePermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    private fun isGpsEnabled(): Boolean = try {
        locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MAX_LAST_KNOWN_AGE_MS = 30_000L
        private const val NETWORK_FALLBACK_DELAY_MS = 10_000L
        const val PERMISSION_MESSAGE = "Precise location permission is needed for GNSS"
        const val GPS_OFF_MESSAGE = "Turn on location (GPS) to use satellite positioning"
        const val SEARCHING_MESSAGE = "Searching for satellites…"
    }
}
