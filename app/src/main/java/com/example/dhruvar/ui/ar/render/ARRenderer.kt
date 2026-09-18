package com.example.dhruvar.ui.ar.render

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Handler
import android.os.Looper
import com.google.ar.core.Plane
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Coordinates OpenGL ES 2.0 rendering for ARCore:
 * - Camera background streaming
 * - Horizontal plane detection and mesh rendering
 * - Lifecycle and display rotation synchronization
 * - UI status reporting (Plane count & Tracking status)
 */
class ARRenderer(
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onStatusChanged: (String) -> Unit
) : GLSurfaceView.Renderer {

    var session: Session? = null
    var displayRotation: Int = 0

    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastReportedPlaneCount = -1
    private var lastReportedStatus = ""

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        backgroundRenderer.createOnGlThread()
        planeRenderer.createOnGlThread()

        session?.let { ses ->
            ses.setCameraTextureName(backgroundRenderer.textureId)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        session?.setDisplayGeometry(displayRotation, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val currentSession = session ?: return

        try {
            currentSession.setCameraTextureName(backgroundRenderer.textureId)
            val frame = currentSession.update()
            val camera = frame.camera

            // 1. Draw live camera background feed
            backgroundRenderer.draw(frame)

            // 2. Process tracking and detected planes
            val trackingState = camera.trackingState
            if (trackingState == TrackingState.TRACKING) {
                camera.getViewMatrix(viewMatrix, 0)
                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

                val allPlanes = currentSession.getAllTrackables(Plane::class.java)
                val horizontalPlanes = allPlanes.filter {
                    it.trackingState == TrackingState.TRACKING &&
                        (it.type == Plane.Type.HORIZONTAL_UPWARD_FACING || it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING)
                }

                // Render detected horizontal ground planes
                planeRenderer.draw(horizontalPlanes, viewMatrix, projectionMatrix)

                val count = horizontalPlanes.size
                if (count != lastReportedPlaneCount) {
                    lastReportedPlaneCount = count
                    mainHandler.post { onPlaneCountChanged(count) }
                }

                val status = if (count > 0) {
                    "Surface detected • Ready"
                } else {
                    "Move device slowly across floor..."
                }

                if (status != lastReportedStatus) {
                    lastReportedStatus = status
                    mainHandler.post { onStatusChanged(status) }
                }
            } else if (trackingState == TrackingState.PAUSED) {
                val reason = when (camera.trackingFailureReason) {
                    TrackingFailureReason.EXCESSIVE_MOTION -> "Move device more slowly"
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Insufficient lighting"
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Point camera at textured surface"
                    else -> "Scanning environment..."
                }
                if (reason != lastReportedStatus) {
                    lastReportedStatus = reason
                    mainHandler.post { onStatusChanged(reason) }
                }
            }
        } catch (e: Exception) {
            // Log or ignore frame drop during pause/resume transitions
        }
    }
}
