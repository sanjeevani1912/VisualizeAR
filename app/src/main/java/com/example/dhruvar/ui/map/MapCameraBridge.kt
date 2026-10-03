package com.example.dhruvar.ui.map

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the planning camera should fly next. */
data class MapCameraTarget(
    val latitude: Double,
    val longitude: Double,
    val zoom: Double
)

/**
 * One-shot camera requests from outside the map composable, consumed by [PlanningMapView].
 *
 * A target rather than a `MapLibreMap` reference, so a request made while the map is still
 * loading its style is honoured once it is ready instead of being dropped.
 */
object MapCameraBridge {
    private val _target = MutableStateFlow<MapCameraTarget?>(null)
    val target: StateFlow<MapCameraTarget?> = _target.asStateFlow()

    fun moveTo(latitude: Double, longitude: Double, zoom: Double) {
        _target.value = MapCameraTarget(latitude, longitude, zoom)
    }

    /** Clears [target] unless a newer request already replaced it. */
    fun consumed(target: MapCameraTarget) {
        _target.compareAndSet(target, null)
    }
}
