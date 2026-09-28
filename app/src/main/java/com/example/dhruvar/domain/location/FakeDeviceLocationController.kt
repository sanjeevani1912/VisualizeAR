package com.example.dhruvar.domain.location

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * In-memory [DeviceLocationController] for JVM tests and pre-init app startup.
 * Does not touch Android location APIs.
 */
class FakeDeviceLocationController(
    initialPermissionGranted: Boolean = false
) : DeviceLocationController {

    private val _state = MutableStateFlow(
        DeviceLocationState(
            permissionGranted = initialPermissionGranted,
            availability = if (initialPermissionGranted) {
                LocationAvailability.NO_FIX
            } else {
                LocationAvailability.PERMISSION_MISSING
            }
        )
    )
    override val state: StateFlow<DeviceLocationState> = _state.asStateFlow()

    override fun onPermissionResult(granted: Boolean) {
        _state.update { current ->
            current.copy(
                permissionGranted = granted,
                availability = when {
                    !granted -> LocationAvailability.PERMISSION_MISSING
                    current.lastFix != null -> LocationAvailability.AVAILABLE
                    else -> LocationAvailability.NO_FIX
                },
                statusMessage = if (granted) null else "Location permission denied",
                lastFix = if (granted) current.lastFix else null,
                isUpdating = if (granted) current.isUpdating else false
            )
        }
    }

    override fun startHighAccuracyUpdates() {
        if (!_state.value.permissionGranted) {
            _state.update {
                it.copy(
                    availability = LocationAvailability.PERMISSION_MISSING,
                    statusMessage = "Location permission required",
                    isUpdating = false
                )
            }
            return
        }
        _state.update { it.copy(isUpdating = true, statusMessage = null) }
    }

    override fun stopUpdates() {
        _state.update { it.copy(isUpdating = false) }
    }

    override fun refreshLocation() {
        // Fake has no hardware; tests push fixes via [emitFix].
        if (!_state.value.permissionGranted) {
            onPermissionResult(false)
        }
    }

    /** Test helper: publish a GNSS sample. */
    fun emitFix(fix: DeviceLocationFix, availability: LocationAvailability = LocationAvailability.AVAILABLE) {
        require(fix.isFinite) { "fix must be finite" }
        _state.update {
            it.copy(
                lastFix = fix,
                availability = availability,
                permissionGranted = true,
                statusMessage = null
            )
        }
    }

    /** Test helper: simulate GPS disabled. */
    fun emitGpsDisabled() {
        _state.update {
            it.copy(
                availability = LocationAvailability.GPS_DISABLED,
                statusMessage = "Location services disabled",
                isUpdating = false
            )
        }
    }
}
