package com.example.dhruvar.domain.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.view.Surface
import android.view.WindowManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * Lifecycle-aware compass using Android [Sensor.TYPE_ROTATION_VECTOR] (preferred)
 * with accelerometer + magnetometer fallback.
 *
 * Heading is Magnetic North. Display rotation is remapped so azimuth matches the
 * phone's physical orientation. Circular low-pass filtering reduces jitter.
 *
 * Does not rotate the planning canvas — it only exposes orientation for the UI.
 */
class CompassHeadingController(
    context: Context
) : SensorEventListener, DefaultLifecycleObserver {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    private val rotationVectorSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val magnetometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

    private val useRotationVector = rotationVectorSensor != null
    private val useFallback = !useRotationVector && accelerometer != null && magnetometer != null

    private val rotationMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private val accelReading = FloatArray(3)
    private val magReading = FloatArray(3)
    private var hasAccel = false
    private var hasMag = false

    private var filteredHeading: Float? = null
    private var registered = false

    private val _state = MutableStateFlow(
        CompassState(isAvailable = useRotationVector || useFallback)
    )
    val state: StateFlow<CompassState> = _state.asStateFlow()

    override fun onResume(owner: LifecycleOwner) {
        start()
    }

    override fun onPause(owner: LifecycleOwner) {
        stop()
    }

    fun start() {
        if (registered) return
        when {
            useRotationVector -> {
                sensorManager.registerListener(
                    this,
                    rotationVectorSensor,
                    SensorManager.SENSOR_DELAY_GAME
                )
                registered = true
                _state.value = _state.value.copy(isAvailable = true)
            }
            useFallback -> {
                sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME)
                sensorManager.registerListener(this, magnetometer, SensorManager.SENSOR_DELAY_GAME)
                registered = true
                _state.value = _state.value.copy(isAvailable = true)
            }
            else -> {
                _state.value = CompassState(isAvailable = false)
            }
        }
    }

    fun stop() {
        if (!registered) return
        sensorManager.unregisterListener(this)
        registered = false
        hasAccel = false
        hasMag = false
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                publishFromRotationMatrix(rotationMatrix)
            }
            Sensor.TYPE_ACCELEROMETER -> {
                System.arraycopy(event.values, 0, accelReading, 0, 3)
                hasAccel = true
                tryPublishFallback()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                System.arraycopy(event.values, 0, magReading, 0, 3)
                hasMag = true
                tryPublishFallback()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        val needsCalibration = accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE ||
            accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
        val label = when (accuracy) {
            SensorManager.SENSOR_STATUS_ACCURACY_HIGH -> "HIGH"
            SensorManager.SENSOR_STATUS_ACCURACY_MEDIUM -> "MEDIUM"
            SensorManager.SENSOR_STATUS_ACCURACY_LOW -> "LOW"
            SensorManager.SENSOR_STATUS_UNRELIABLE -> "UNRELIABLE"
            else -> "UNKNOWN"
        }
        _state.value = _state.value.copy(
            needsCalibration = needsCalibration,
            accuracyLabel = label
        )
    }

    private fun tryPublishFallback() {
        if (!hasAccel || !hasMag) return
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            null,
            accelReading,
            magReading
        )
        if (success) {
            publishFromRotationMatrix(rotationMatrix)
        }
    }

    private fun publishFromRotationMatrix(matrix: FloatArray) {
        val (axisX, axisY) = displayRemapAxes()
        val remapped = SensorManager.remapCoordinateSystem(matrix, axisX, axisY, remappedMatrix)
        val source = if (remapped) remappedMatrix else matrix
        SensorManager.getOrientation(source, orientation)

        // orientation[0] = azimuth (radians): angle around -Z between device Y and Magnetic North
        var heading = Math.toDegrees(orientation[0].toDouble()).toFloat()
        heading = normalizeDegrees(heading)
        heading = smoothHeading(heading)

        _state.value = _state.value.copy(
            magneticHeadingDegrees = heading,
            isAvailable = true
        )
    }

    /**
     * Remap world axes into the device's current display orientation so azimuth
     * matches what the user sees with the phone upright in hand.
     */
    private fun displayRemapAxes(): Pair<Int, Int> {
        val rotation = currentDisplayRotation()
        return when (rotation) {
            Surface.ROTATION_90 ->
                SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
            Surface.ROTATION_180 ->
                SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
            Surface.ROTATION_270 ->
                SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
            else ->
                SensorManager.AXIS_X to SensorManager.AXIS_Y
        }
    }

    @Suppress("DEPRECATION")
    private fun currentDisplayRotation(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            appContext.display?.rotation ?: Surface.ROTATION_0
        } else {
            val wm = appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            wm.defaultDisplay.rotation
        }
    }

    /**
     * Circular low-pass filter so heading stays smooth without wrapping jumps at 0°/360°.
     */
    private fun smoothHeading(raw: Float): Float {
        val previous = filteredHeading
        if (previous == null) {
            filteredHeading = raw
            return raw
        }
        var delta = raw - previous
        while (delta > 180f) delta -= 360f
        while (delta < -180f) delta += 360f
        // Ignore tiny noise; respond quickly to real turns
        if (abs(delta) < 0.15f) return previous
        val smoothed = normalizeDegrees(previous + SMOOTHING_ALPHA * delta)
        filteredHeading = smoothed
        return smoothed
    }

    companion object {
        /** 0 = full smoothing (laggy), 1 = no smoothing. ~0.18 balances stability/responsiveness. */
        private const val SMOOTHING_ALPHA = 0.18f

        fun normalizeDegrees(degrees: Float): Float {
            var d = degrees % 360f
            if (d < 0f) d += 360f
            return if (d >= 360f) 0f else d
        }
    }
}
