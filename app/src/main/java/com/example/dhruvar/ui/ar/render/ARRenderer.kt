package com.example.dhruvar.ui.ar.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.sqrt

/**
 * 2D screen projected coordinate for an AR object label.
 */
data class ARObjectLabelProjection(
    val objectId: String,
    val assetType: AssetType,
    val name: String,
    val screenX: Float,
    val screenY: Float,
    val distanceToCameraMeters: Float,
    val isSelected: Boolean,
    val isVisible: Boolean
)

/**
 * 2D screen projected coordinate for the planning origin marker label.
 */
data class AROriginLabelProjection(
    val screenX: Float,
    val screenY: Float,
    val isVisible: Boolean
)

/**
 * 2D screen projected coordinate for the 3D distance line midpoint badge.
 */
data class ARDistanceLineLabelProjection(
    val screenX: Float,
    val screenY: Float,
    val distanceMeters: Float,
    val isVisible: Boolean
)

/**
 * Coordinates OpenGL ES 2.0 rendering for ARCore:
 * - Camera background streaming
 * - Horizontal plane detection and mesh rendering
 * - Center-screen raycasting for planning origin calibration
 * - 3D tactical asset rendering with directional lighting & selection glow
 * - 3D distance line rendering from origin to selected object
 * - 3D-to-2D screen projection for Compose billboard labels & distance badges
 * - Touch tap hit-testing for interactive object selection
 */
class ARRenderer(
    private val onPlaneCountChanged: (Int) -> Unit,
    private val onStatusChanged: (String) -> Unit,
    private val onCenterHitChanged: (canSetOrigin: Boolean) -> Unit = {},
    private val onOriginCalibratedChanged: (isCalibrated: Boolean) -> Unit = {},
    private val onProjectionsUpdated: (
        originLabel: AROriginLabelProjection?,
        objectLabels: List<ARObjectLabelProjection>,
        distanceBadge: ARDistanceLineLabelProjection?
    ) -> Unit = { _, _, _ -> },
    private val onObjectTapped: (objectId: String?) -> Unit = {}
) : GLSurfaceView.Renderer {

    var session: Session? = null
    var displayRotation: Int = 0

    var displayRotationProvider: (() -> Int)? = null

    @Volatile
    var viewportChanged = true
        private set

    @Volatile
    private var lastAppliedRotation = -1

    var currentLayout: Layout? = null
    var selectedObjectId: String? = null

    var originAnchor: Anchor? = null
        private set

    private val backgroundRenderer = BackgroundRenderer()
    private val planeRenderer = PlaneRenderer()
    private val originMarkerRenderer = OriginMarkerRenderer()
    private val arObjectRenderer = ARObjectRenderer()
    private val arDistanceLineRenderer = ARDistanceLineRenderer()

    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewProjMatrix = FloatArray(16)

    private var viewportWidth = 1
    private var viewportHeight = 1

    @Volatile
    private var currentCenterHit: HitResult? = null

    @Volatile
    private var latestFrame: com.google.ar.core.Frame? = null

    @Volatile
    private var latestHorizontalPlanes: List<Plane> = emptyList()

    @Volatile
    private var lastProjectedLabels = emptyList<ARObjectLabelProjection>()

    @Volatile
    private var pendingCaptureCallback: ((Bitmap?) -> Unit)? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastReportedPlaneCount = -1
    private var lastReportedStatus = ""
    private var lastReportedCanSetOrigin = false

    fun notifySessionResumed() {
        viewportChanged = true
        lastAppliedRotation = -1
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)
        backgroundRenderer.createOnGlThread()
        planeRenderer.createOnGlThread()
        originMarkerRenderer.createOnGlThread()
        arObjectRenderer.createOnGlThread()
        arDistanceLineRenderer.createOnGlThread()

        session?.let { ses ->
            ses.setCameraTextureName(backgroundRenderer.textureId)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewportWidth = width
        viewportHeight = height
        viewportChanged = true
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val currentSession = session ?: return

        try {
            val currentRotation = displayRotationProvider?.invoke() ?: displayRotation
            if (viewportChanged || currentRotation != lastAppliedRotation) {
                currentSession.setDisplayGeometry(currentRotation, viewportWidth, viewportHeight)
                viewportChanged = false
                lastAppliedRotation = currentRotation
            }

            currentSession.setCameraTextureName(backgroundRenderer.textureId)
            val frame = currentSession.update()
            latestFrame = frame
            val camera = frame.camera

            // 1. Draw live camera background feed
            backgroundRenderer.draw(frame)

            // 2. Process tracking and planes
            val trackingState = camera.trackingState
            if (trackingState == TrackingState.TRACKING) {
                camera.getViewMatrix(viewMatrix, 0)
                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
                Matrix.multiplyMM(viewProjMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                val allPlanes = currentSession.getAllTrackables(Plane::class.java)
                val horizontalPlanes = allPlanes.filter {
                    it.trackingState == TrackingState.TRACKING &&
                        (it.type == Plane.Type.HORIZONTAL_UPWARD_FACING || it.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING)
                }
                latestHorizontalPlanes = horizontalPlanes

                // Render detected horizontal ground planes
                planeRenderer.draw(horizontalPlanes, viewMatrix, projectionMatrix)

                val planeCount = horizontalPlanes.size
                if (planeCount != lastReportedPlaneCount) {
                    lastReportedPlaneCount = planeCount
                    mainHandler.post { onPlaneCountChanged(planeCount) }
                }

                // If not yet calibrated, raycast center of screen
                if (originAnchor == null) {
                    val hitResults = frame.hitTest(viewportWidth / 2.0f, viewportHeight / 2.0f)
                    val validHit = hitResults.firstOrNull { hit ->
                        val trackable = hit.trackable
                        trackable is Plane &&
                            (trackable.isPoseInPolygon(hit.hitPose) || trackable.isPoseInExtents(hit.hitPose)) &&
                            (trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING || trackable.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING)
                    }

                    currentCenterHit = validHit
                    val canSet = validHit != null || horizontalPlanes.isNotEmpty()
                    if (canSet != lastReportedCanSetOrigin) {
                        lastReportedCanSetOrigin = canSet
                        mainHandler.post { onCenterHitChanged(canSet) }
                    }

                    val status = when {
                        validHit != null -> "Ground target acquired"
                        horizontalPlanes.isNotEmpty() -> "Ground detected • Tap surface or press Set Origin"
                        else -> "Scanning for ground..."
                    }

                    if (status != lastReportedStatus) {
                        lastReportedStatus = status
                        mainHandler.post { onStatusChanged(status) }
                    }
                } else {
                    // Origin is calibrated
                    val anchor = originAnchor!!
                    if (anchor.trackingState == TrackingState.TRACKING) {
                        // 3. Draw physical origin ground marker
                        originMarkerRenderer.draw(anchor, viewMatrix, projectionMatrix)

                        // 4. Draw 3D tactical assets
                        val layout = currentLayout
                        if (layout != null && layout.objects.isNotEmpty()) {
                            arObjectRenderer.draw(
                                objects = layout.objects,
                                originPose = anchor.pose,
                                selectedObjectId = selectedObjectId,
                                viewMatrix = viewMatrix,
                                projectionMatrix = projectionMatrix
                            )

                            // 5. Draw 3D distance line if an object is selected
                            val selectedObj = layout.objects.firstOrNull { it.id == selectedObjectId }
                            var lineMidpointBadge: ARDistanceLineLabelProjection? = null

                            if (selectedObj != null) {
                                val selectedPose = ARCoordinateTransformer.transformPlanningToWorldPose(
                                    originPose = anchor.pose,
                                    planXMeters = selectedObj.x,
                                    planZMeters = selectedObj.z,
                                    planRotationDegrees = selectedObj.rotationDegrees
                                )
                                arDistanceLineRenderer.draw(anchor.pose, selectedPose, viewMatrix, projectionMatrix)

                                // Compute midpoint for 3D distance badge
                                val oPos = anchor.pose.translation
                                val sPos = selectedPose.translation
                                val midX = (oPos[0] + sPos[0]) * 0.5f
                                val midY = (oPos[1] + sPos[1]) * 0.5f + 0.15f
                                val midZ = (oPos[2] + sPos[2]) * 0.5f

                                val midScreen = projectWorldToScreen(midX, midY, midZ)
                                if (midScreen != null) {
                                    val distMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObj)
                                    lineMidpointBadge = ARDistanceLineLabelProjection(
                                        screenX = midScreen.first,
                                        screenY = midScreen.second,
                                        distanceMeters = distMeters,
                                        isVisible = true
                                    )
                                }
                            }

                            // 6. Compute 2D Screen-Space Projections for Compose Billboard Labels
                            val cameraPos = camera.pose.translation
                            val objectLabels = mutableListOf<ARObjectLabelProjection>()

                            for (obj in layout.objects) {
                                val objPose = ARCoordinateTransformer.transformPlanningToWorldPose(
                                    originPose = anchor.pose,
                                    planXMeters = obj.x,
                                    planZMeters = obj.z,
                                    planRotationDegrees = obj.rotationDegrees
                                )
                                val oTrans = objPose.translation
                                val topY = oTrans[1] + obj.heightMeters + 0.25f

                                val screenCoord = projectWorldToScreen(oTrans[0], topY, oTrans[2])
                                val dx = oTrans[0] - cameraPos[0]
                                val dy = oTrans[1] - cameraPos[1]
                                val dz = oTrans[2] - cameraPos[2]
                                val distToCam = sqrt(dx * dx + dy * dy + dz * dz)

                                if (screenCoord != null) {
                                    objectLabels.add(
                                        ARObjectLabelProjection(
                                            objectId = obj.id,
                                            assetType = obj.assetType,
                                            name = obj.name,
                                            screenX = screenCoord.first,
                                            screenY = screenCoord.second,
                                            distanceToCameraMeters = distToCam,
                                            isSelected = (obj.id == selectedObjectId),
                                            isVisible = true
                                        )
                                    )
                                }
                            }

                            lastProjectedLabels = objectLabels

                            // Project origin marker label
                            val oTrans = anchor.pose.translation
                            val originScreen = projectWorldToScreen(oTrans[0], oTrans[1] + 0.15f, oTrans[2])
                            val originLabel = originScreen?.let {
                                AROriginLabelProjection(
                                    screenX = it.first,
                                    screenY = it.second,
                                    isVisible = true
                                )
                            }

                            mainHandler.post {
                                onProjectionsUpdated(originLabel, objectLabels, lineMidpointBadge)
                            }
                        }
                    }

                    val status = "Tracking normal"
                    if (status != lastReportedStatus) {
                        lastReportedStatus = status
                        mainHandler.post { onStatusChanged(status) }
                    }
                }
            } else if (trackingState == TrackingState.PAUSED) {
                val reason = when (camera.trackingFailureReason) {
                    TrackingFailureReason.EXCESSIVE_MOTION -> "Tracking limited - Move device slowly"
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> "Not enough visual features - low light"
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> "Not enough visual features"
                    else -> "Scanning environment..."
                }
                if (reason != lastReportedStatus) {
                    lastReportedStatus = reason
                    mainHandler.post { onStatusChanged(reason) }
                }
            }

            // 7. Check if a frame capture has been requested
            val captureCb = pendingCaptureCallback
            if (captureCb != null && viewportWidth > 0 && viewportHeight > 0) {
                pendingCaptureCallback = null
                try {
                    val byteBuffer = ByteBuffer.allocateDirect(viewportWidth * viewportHeight * 4)
                        .order(ByteOrder.nativeOrder())
                    GLES20.glReadPixels(
                        0, 0, viewportWidth, viewportHeight,
                        GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, byteBuffer
                    )
                    val rawBitmap = Bitmap.createBitmap(viewportWidth, viewportHeight, Bitmap.Config.ARGB_8888)
                    byteBuffer.rewind()
                    rawBitmap.copyPixelsFromBuffer(byteBuffer)

                    val flipMatrix = android.graphics.Matrix().apply { preScale(1.0f, -1.0f) }
                    val flipped = Bitmap.createBitmap(rawBitmap, 0, 0, viewportWidth, viewportHeight, flipMatrix, false)
                    mainHandler.post { captureCb(flipped) }
                } catch (_: Exception) {
                    mainHandler.post { captureCb(null) }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ARRenderer", "Exception in onDrawFrame: ${e.message}", e)
        }
    }

    /**
     * Projects a 3D world coordinate (x, y, z) to 2D screen pixels (screenX, screenY).
     * Returns null if the point is behind the camera.
     */
    private fun projectWorldToScreen(worldX: Float, worldY: Float, worldZ: Float): Pair<Float, Float>? {
        val inVec = floatArrayOf(worldX, worldY, worldZ, 1.0f)
        val outVec = FloatArray(4)

        Matrix.multiplyMV(outVec, 0, viewProjMatrix, 0, inVec, 0)
        val w = outVec[3]
        if (w <= 0.1f) return null // Behind camera

        val ndcX = outVec[0] / w
        val ndcY = outVec[1] / w

        // Convert NDC [-1, 1] to screen pixels
        val screenX = (ndcX + 1.0f) * 0.5f * viewportWidth
        val screenY = (1.0f - ndcY) * 0.5f * viewportHeight

        // Check if within display bounds (with slight margin)
        if (screenX < -100f || screenX > viewportWidth + 100f || screenY < -100f || screenY > viewportHeight + 100f) {
            return null
        }

        return Pair(screenX, screenY)
    }

    /**
     * Handles touch taps on the screen.
     * In uncalibrated mode: taps on detected ground set the planning origin.
     * In calibrated mode: selects closest projected 3D object within proximity, or clears selection if tapping empty space.
     */
    fun handleScreenTap(tapX: Float, tapY: Float): Boolean {
        if (originAnchor == null) {
            val frame = latestFrame ?: return false
            val hitResults = frame.hitTest(tapX, tapY)
            val groundHit = hitResults.firstOrNull { hit ->
                val trackable = hit.trackable
                trackable is Plane &&
                    (trackable.isPoseInPolygon(hit.hitPose) || trackable.isPoseInExtents(hit.hitPose)) &&
                    (trackable.type == Plane.Type.HORIZONTAL_UPWARD_FACING || trackable.type == Plane.Type.HORIZONTAL_DOWNWARD_FACING)
            }

            if (groundHit != null) {
                val plane = groundHit.trackable as? Plane
                if (plane != null) {
                    val newAnchor = plane.createAnchor(groundHit.hitPose)
                    originAnchor?.detach()
                    originAnchor = newAnchor
                    mainHandler.post { onOriginCalibratedChanged(true) }
                    return true
                }
            }
            return false
        }

        val hit = lastProjectedLabels.filter { it.isVisible }.minByOrNull {
            val dx = it.screenX - tapX
            val dy = it.screenY - tapY
            dx * dx + dy * dy
        }

        // Tap hit radius of ~65dp (scaled to pixels, ~180px)
        val thresholdSq = 140.0f * 140.0f
        if (hit != null) {
            val dx = hit.screenX - tapX
            val dy = hit.screenY - tapY
            val distSq = dx * dx + dy * dy
            if (distSq <= thresholdSq) {
                mainHandler.post { onObjectTapped(hit.objectId) }
                return true
            }
        }

        // Tapped empty ground -> clear selection
        mainHandler.post { onObjectTapped(null) }
        return false
    }

    fun setOriginAtCenterHit(): Boolean {
        val hit = currentCenterHit
        if (hit != null) {
            val plane = hit.trackable as? Plane
            if (plane != null) {
                val newAnchor = plane.createAnchor(hit.hitPose)
                originAnchor?.detach()
                originAnchor = newAnchor
                mainHandler.post { onOriginCalibratedChanged(true) }
                return true
            }
        }

        // Fallback: If center hit wasn't strictly acquired but ground planes exist, anchor to nearest plane center
        val fallbackPlane = latestHorizontalPlanes.firstOrNull()
        if (fallbackPlane != null) {
            val newAnchor = fallbackPlane.createAnchor(fallbackPlane.centerPose)
            originAnchor?.detach()
            originAnchor = newAnchor
            mainHandler.post { onOriginCalibratedChanged(true) }
            return true
        }

        return false
    }

    fun resetOrigin() {
        originAnchor?.detach()
        originAnchor = null
        currentCenterHit = null
        lastReportedCanSetOrigin = false
        lastProjectedLabels = emptyList()
        mainHandler.post {
            onOriginCalibratedChanged(false)
            onCenterHitChanged(false)
            onProjectionsUpdated(null, emptyList(), null)
        }
    }

    /**
     * Schedules a direct OpenGL frame buffer capture on the next render pass.
     */
    fun captureNextFrame(callback: (Bitmap?) -> Unit) {
        pendingCaptureCallback = callback
    }
}
