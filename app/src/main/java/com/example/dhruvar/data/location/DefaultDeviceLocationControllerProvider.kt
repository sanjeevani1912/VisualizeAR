package com.example.dhruvar.data.location

import android.content.Context
import com.example.dhruvar.domain.location.DeviceLocationController
import com.example.dhruvar.domain.location.FakeDeviceLocationController

/**
 * Provides the active [DeviceLocationController].
 * Initialized from [com.example.dhruvar.MainActivity] with a fused-location implementation;
 * falls back to [FakeDeviceLocationController] for JVM tests / pre-init.
 */
object DefaultDeviceLocationControllerProvider {

    @Volatile
    private var instance: DeviceLocationController? = null

    fun init(context: Context) {
        instance = FusedDeviceLocationController(context.applicationContext)
    }

    fun setController(controller: DeviceLocationController) {
        instance = controller
    }

    fun getController(): DeviceLocationController {
        return instance ?: synchronized(this) {
            instance ?: FakeDeviceLocationController().also { instance = it }
        }
    }

    fun reset() {
        instance = null
    }
}
