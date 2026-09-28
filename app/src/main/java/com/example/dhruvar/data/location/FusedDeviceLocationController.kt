package com.example.dhruvar.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.example.dhruvar.domain.location.DeviceLocationController
import com.example.dhruvar.domain.location.DeviceLocationFix
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.LocationAvailability
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Fused Location Provider backed [DeviceLocationController].
 *
 * High-accuracy updates run only while [startHighAccuracyUpdates] is active;
 * call [stopUpdates] when the Geo Origin panel collapses or the screen pauses.
 */
class FusedDeviceLocationController(
    context: Context
) : DeviceLocationController {

    private val appContext = context.applicationContext
    private val fusedClient = LocationServices.getFusedLocationProviderClient(appContext)
    private val locationManager =
        appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private val _state = MutableStateFlow(
        DeviceLocationState(
            permissionGranted = hasLocationPermission(),
            availability = if (hasLocationPermission()) {
                LocationAvailability.NO_FIX
            } else {
                LocationAvailability.PERMISSION_MISSING
            }
        )
    )
    override val state: StateFlow<DeviceLocationState> = _state.asStateFlow()

    private var updatesActive = false

    private val locationRequest: LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, UPDATE_INTERVAL_MS)
            .setMinUpdateIntervalMillis(MIN_UPDATE_INTERVAL_MS)
            .setWaitForAccurateLocation(true)
            .build()

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            publishLocation(location)
        }
    }

    override fun onPermissionResult(granted: Boolean) {
        _state.update { current ->
            current.copy(
                permissionGranted = granted,
                availability = when {
                    !granted -> LocationAvailability.PERMISSION_MISSING
                    !isLocationEnabled() -> LocationAvailability.GPS_DISABLED
                    current.lastFix != null -> LocationAvailability.AVAILABLE
                    else -> LocationAvailability.NO_FIX
                },
                statusMessage = when {
                    !granted -> "Location permission denied"
                    !isLocationEnabled() -> "Enable location services to capture origin"
                    else -> null
                },
                lastFix = if (granted) current.lastFix else null,
                isUpdating = if (granted) current.isUpdating else false
            )
        }
        if (granted && updatesActive) {
            requestUpdatesInternal()
        } else if (!granted) {
            stopUpdatesInternal()
        }
    }

    override fun startHighAccuracyUpdates() {
        updatesActive = true
        if (!hasLocationPermission()) {
            _state.update {
                it.copy(
                    permissionGranted = false,
                    availability = LocationAvailability.PERMISSION_MISSING,
                    statusMessage = "Location permission required",
                    isUpdating = false
                )
            }
            return
        }
        if (!isLocationEnabled()) {
            _state.update {
                it.copy(
                    permissionGranted = true,
                    availability = LocationAvailability.GPS_DISABLED,
                    statusMessage = "Enable location services to capture origin",
                    isUpdating = false
                )
            }
            return
        }
        requestUpdatesInternal()
        refreshLocation()
    }

    override fun stopUpdates() {
        updatesActive = false
        stopUpdatesInternal()
    }

    @SuppressLint("MissingPermission")
    override fun refreshLocation() {
        if (!hasLocationPermission()) {
            onPermissionResult(false)
            return
        }
        if (!isLocationEnabled()) {
            _state.update {
                it.copy(
                    availability = LocationAvailability.GPS_DISABLED,
                    statusMessage = "Enable location services to capture origin"
                )
            }
            return
        }
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { location ->
                if (location != null) {
                    publishLocation(location)
                } else if (_state.value.lastFix == null) {
                    _state.update {
                        it.copy(
                            availability = LocationAvailability.NO_FIX,
                            statusMessage = "Waiting for GNSS fix…"
                        )
                    }
                }
            }
            .addOnFailureListener { error ->
                _state.update {
                    it.copy(
                        availability = LocationAvailability.PROVIDER_ERROR,
                        statusMessage = error.message ?: "Location provider error"
                    )
                }
            }
    }

    @SuppressLint("MissingPermission")
    private fun requestUpdatesInternal() {
        if (!hasLocationPermission()) return
        _state.update { it.copy(isUpdating = true, permissionGranted = true) }
        fusedClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    private fun stopUpdatesInternal() {
        fusedClient.removeLocationUpdates(locationCallback)
        _state.update { it.copy(isUpdating = false) }
    }

    private fun publishLocation(location: android.location.Location) {
        val fix = DeviceLocationFix(
            latitude = location.latitude,
            longitude = location.longitude,
            horizontalAccuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
            altitudeMeters = if (location.hasAltitude()) location.altitude else null,
            timestampEpochMs = location.time.takeIf { it > 0L } ?: System.currentTimeMillis()
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

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun isLocationEnabled(): Boolean {
        return try {
            locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            true
        }
    }

    companion object {
        private const val UPDATE_INTERVAL_MS = 1_000L
        private const val MIN_UPDATE_INTERVAL_MS = 500L
    }
}
