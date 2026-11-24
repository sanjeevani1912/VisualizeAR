package com.example.dhruvar.ui.ar.render

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Handler
import android.os.Looper
import java.nio.ByteBuffer
import java.nio.ByteOrder
import com.example.dhruvar.domain.ar.ArAssetSize
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.spatial.ARCoordinateTransformer
import com.example.dhruvar.domain.spatial.ArHeadingFix
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.google.ar.core.Anchor
import com.google.ar.core.HitResult
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.NotYetAvailableException
import com.google.ar.core.exceptions.SessionPausedException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
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

    @Volatile
    var isSessionPaused: Boolean = true

    var displayRotationProvider: (() -> Int)? = null

    @Volatile
    var viewportChanged = true
        private set

    @Volatile
    private var lastAppliedRotation = -1

    var currentLayout: Layout? = null
    var selectedObjectId: String? = null

    /**
     * Uniform sand-table / full-scale factor applied to planning offsets (and meshes).
     * Does not persist into layout JSON — session visualization only.
     */
    @Volatile
    var visualizationScale: Float = 1.0f

    /** Display-only enlargement of the models about their own pivots; positions are unchanged. */
    @Volatile
    var assetSizeFactor: Float = ArAssetSize.DEFAULT

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

    /** Camera look direction on the ground plane, sampled each tracking frame. */
    @Volatile
    private var cameraForwardX = 0f

    @Volatile
    private var cameraForwardZ = 1f

    @Volatile
    private var latestFrame: com.google.ar.core.Frame? = null

    @Volatile
    private var latestHorizontalPlanes: List<Plane> = emptyList()

    @Volatile
    private var lastProjectedLabels = emptyList<ARObjectLabelProjection>()

    @Volatile
    private var pendingCaptureCallback: ((Bitmap?) -> Unit)? = null

    private val snappedGroundY = HashMap<String, Float>()

    private val panoramaLock = Any()
    private val panoramaShots = ArrayList<PanoramaShot>()

    @Volatile
    private var panoramaActive = false
    private var lastShotForward: FloatArray? = null
    private var prevForward: FloatArray? = null
    private var prevFrameNs = 0L
    private var unwrappedYaw = 0f
    private var lastRawYaw = Float.NaN
    private var lastReportedShots = -1
    private var lastTooFast = false
    private var readBuffer: ByteBuffer? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastReportedPlaneCount = -1
    private var lastReportedStatus = ""
    private var lastReportedCanSetOrigin = false

    private companion object {
        const val MIN_GROUND_AREA_M2 = 0.8f
        const val GROUND_SNAP_RANGE_M = 1.5f
        const val PANORAMA_STEP_DEG = 9f
        const val PANORAMA_MAX_SHOTS = 48
        const val PANORAMA_SHOT_WIDTH_PX = 400
        const val MAX_SWEEP_DEG_PER_SEC = 70f
    }

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

        if (isSessionPaused) {
            return
        }

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
            val lookAxis = camera.pose.zAxis
            cameraForwardX = -lookAxis[0]
            cameraForwardZ = -lookAxis[2]

            // 1. Draw live camera background feed
            backgroundRenderer.draw(frame)

            // 2. Process tracking and planes
            val trackingState = camera.trackingState
            if (trackingState == TrackingState.TRACKING) {
                camera.getViewMatrix(viewMatrix, 0)
                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
                Matrix.multiplyMM(viewProjMatrix, 0, projectionMatrix, 0, viewMatrix, 0)

                val allPlanes = currentSession.getAllTrackables(Plane::class.java)
                val horizontalPlanes = allPlanes.filter { isGroundCandidate(it) }
                latestHorizontalPlanes = horizontalPlanes

                // Render detected horizontal ground planes
                if (!panoramaActive) {
                    planeRenderer.draw(horizontalPlanes, viewMatrix, projectionMatrix)
                }

                val planeCount = horizontalPlanes.size
                if (planeCount != lastReportedPlaneCount) {
                    lastReportedPlaneCount = planeCount
                    mainHandler.post { onPlaneCountChanged(planeCount) }
                }

                // If not yet calibrated, raycast center of screen
                if (originAnchor == null) {
                    val hitResults = frame.hitTest(viewportWidth / 2.0f, viewportHeight / 2.0f)
                    val validHit = hitResults.firstOrNull { isUsableGroundHit(it) }

                    currentCenterHit = validHit
                    val canSet = validHit != null || bestGroundPlane(horizontalPlanes) != null
                    if (canSet != lastReportedCanSetOrigin) {
                        lastReportedCanSetOrigin = canSet
                        mainHandler.post { onCenterHitChanged(canSet) }
                    }

                    val status = when {
                        validHit != null -> "Ground target acquired"
                        horizontalPlanes.isNotEmpty() -> "Ground detected • Aim at the floor and press Set Origin"
                        else -> "Scanning for ground • Tilt phone down, move slowly"
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
                        val vizScale = visualizationScale
                        if (layout != null && layout.objects.isNotEmpty()) {
                            val groundedPoses = HashMap<String, Pose>(layout.objects.size)
                            for (obj in layout.objects) {
                                groundedPoses[obj.id] = groundedObjectPose(obj, anchor.pose, vizScale)
                            }
                            arObjectRenderer.draw(
                                objects = layout.objects,
                                originPose = anchor.pose,
                                selectedObjectId = selectedObjectId,
                                viewMatrix = viewMatrix,
                                projectionMatrix = projectionMatrix,
                                visualizationScale = vizScale,
                                assetSizeFactor = assetSizeFactor,
                                poseOverride = { groundedPoses[it.id] }
                            )

                            // 5. Draw 3D distance line if an object is selected
                            val selectedObj = layout.objects.firstOrNull { it.id == selectedObjectId }
                            var lineMidpointBadge: ARDistanceLineLabelProjection? = null

                            if (selectedObj != null) {
                                val selectedPose = groundedPoses[selectedObj.id]
                                    ?: ARCoordinateTransformer.transformPlanningToWorldPose(
                                        originPose = anchor.pose,
                                        planXMeters = selectedObj.x,
                                        planZMeters = selectedObj.z,
                                        planRotationDegrees = selectedObj.rotationDegrees,
                                        visualizationScale = vizScale
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
                                val objPose = groundedPoses[obj.id] ?: continue
                                val oTrans = objPose.translation
                                val topY = oTrans[1] + (obj.heightMeters * vizScale * assetSizeFactor) + 0.25f

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

            if (panoramaActive && trackingState == TrackingState.TRACKING) {
                capturePanoramaShotIfNeeded(frame, camera)
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
        } catch (e: SessionPausedException) {
            // Expected during pause/resume transitions; skip frame cleanly
            return
        } catch (e: NotYetAvailableException) {
            // Frame is not yet available; skip frame cleanly
            return
        } catch (e: CameraNotAvailableException) {
            android.util.Log.w("ARRenderer", "Camera not available during onDrawFrame: ${e.message}")
            mainHandler.post { onStatusChanged("Camera unavailable") }
            return
        } catch (e: Exception) {
            android.util.Log.e("ARRenderer", "Exception in onDrawFrame: ${e.message}", e)
        }
    }

    // ---- Ground detection & snapping --------------------------------------------------------

    /** Only tracked, upward-facing planes that are not merged into a larger plane count as ground. */
    private fun isGroundCandidate(plane: Plane): Boolean =
        plane.trackingState == TrackingState.TRACKING &&
            plane.subsumedBy == null &&
            plane.type == Plane.Type.HORIZONTAL_UPWARD_FACING

    private fun planeArea(plane: Plane): Float = plane.extentX * plane.extentZ

    private fun isUsableGroundHit(hit: HitResult): Boolean {
        val plane = hit.trackable as? Plane ?: return false
        return isGroundCandidate(plane) &&
            planeArea(plane) >= MIN_GROUND_AREA_M2 &&
            (plane.isPoseInPolygon(hit.hitPose) || plane.isPoseInExtents(hit.hitPose))
    }

    /** Lowest of the large planes (the floor), else the largest plane seen so far. */
    private fun bestGroundPlane(planes: List<Plane>): Plane? {
        val large = planes.filter { planeArea(it) >= MIN_GROUND_AREA_M2 }
        return large.minByOrNull { it.centerPose.ty() } ?: planes.maxByOrNull { planeArea(it) }
    }

    /**
     * Height of the detected ground under world position ([worldX], [worldZ]).
     *
     * Among planes that cover that spot and sit near [referenceY], the LOWEST wins, so a table or
     * ledge above the floor never lifts an asset. Where no plane reaches (far from the origin) the
     * floor plane's height is used instead of the origin's, so assets do not hang in the air.
     */
    private fun groundHeightAt(worldX: Float, worldZ: Float, referenceY: Float): Float {
        var coveredY = Float.MAX_VALUE
        var floorY = Float.MAX_VALUE
        for (plane in latestHorizontalPlanes) {
            val planeY = plane.centerPose.ty()
            if (abs(planeY - referenceY) > GROUND_SNAP_RANGE_M) continue
            if (planeArea(plane) >= MIN_GROUND_AREA_M2) floorY = minOf(floorY, planeY)
            val probe = Pose.makeTranslation(worldX, planeY, worldZ)
            if (plane.isPoseInPolygon(probe) || plane.isPoseInExtents(probe)) {
                coveredY = minOf(coveredY, planeY)
            }
        }
        return when {
            coveredY != Float.MAX_VALUE -> coveredY
            floorY != Float.MAX_VALUE -> floorY
            else -> referenceY
        }
    }

    /** Planning pose for [obj] with its height snapped to the ground plane beneath it. */
    private fun groundedObjectPose(
        obj: com.example.dhruvar.domain.model.LayoutObject,
        originPose: Pose,
        vizScale: Float
    ): Pose {
        val base = ARCoordinateTransformer.transformPlanningToWorldPose(
            originPose = originPose,
            planXMeters = obj.x,
            planZMeters = obj.z,
            planRotationDegrees = obj.rotationDegrees,
            visualizationScale = vizScale
        )
        val t = base.translation
        val target = groundHeightAt(t[0], t[2], t[1])
        val previous = snappedGroundY[obj.id]
        val y = if (previous == null || abs(target - previous) > 0.4f) target
        else previous + (target - previous) * 0.15f
        snappedGroundY[obj.id] = y
        return Pose(floatArrayOf(t[0], y, t[2]), base.rotationQuaternion)
    }

    // ---- Panorama capture -------------------------------------------------------------------

    /** Progress callback: shots taken, degrees swept so far, and whether the phone is moving too fast. */
    var onPanoramaProgress: (shots: Int, sweptDegrees: Float, tooFast: Boolean) -> Unit = { _, _, _ -> }

    /** Starts recording shots of the live AR view (camera feed + assets) while the user sweeps. */
    fun startPanorama() {
        synchronized(panoramaLock) {
            panoramaShots.forEach { it.bitmap.recycle() }
            panoramaShots.clear()
            lastShotForward = null
            prevForward = null
            prevFrameNs = 0L
            unwrappedYaw = 0f
            lastRawYaw = Float.NaN
            lastTooFast = false
            lastReportedShots = -1
            panoramaActive = true
        }
        mainHandler.post { onPanoramaProgress(0, 0f, false) }
    }

    /** Stops recording and returns the shots taken; the caller owns the bitmaps. */
    fun stopPanorama(): List<PanoramaShot> {
        synchronized(panoramaLock) {
            panoramaActive = false
            val result = panoramaShots.toList()
            panoramaShots.clear()
            return result
        }
    }

    private fun capturePanoramaShotIfNeeded(frame: com.google.ar.core.Frame, camera: com.google.ar.core.Camera) {
        val pose = camera.displayOrientedPose
        val xAxis = pose.xAxis
        val yAxis = pose.yAxis
        val zAxis = pose.zAxis
        val fwd = floatArrayOf(-zAxis[0], -zAxis[1], -zAxis[2])
        val rawYaw = Math.toDegrees(atan2(fwd[0].toDouble(), (-fwd[2]).toDouble())).toFloat()
        val pitch = Math.toDegrees(asin(fwd[1].coerceIn(-1f, 1f).toDouble())).toFloat()

        // Unwrap yaw so a full turn accumulates past +-180.
        if (!lastRawYaw.isNaN()) {
            var d = rawYaw - lastRawYaw
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            unwrappedYaw += d
        } else {
            unwrappedYaw = rawYaw
        }
        lastRawYaw = rawYaw

        val nowNs = frame.timestamp
        val prev = prevForward
        var tooFast = false
        if (prev != null && prevFrameNs > 0L) {
            val dtSec = (nowNs - prevFrameNs) / 1e9f
            if (dtSec > 0f) tooFast = angleBetween(prev, fwd) / dtSec > MAX_SWEEP_DEG_PER_SEC
        }
        prevForward = fwd
        prevFrameNs = nowNs

        val last = lastShotForward
        val due = last == null || angleBetween(last, fwd) >= PANORAMA_STEP_DEG
        val count: Int
        val swept: Float
        synchronized(panoramaLock) {
            if (!panoramaActive) return
            if (due && !tooFast && panoramaShots.size < PANORAMA_MAX_SHOTS) {
                val bmp = readFramebufferScaled()
                if (bmp != null) {
                    panoramaShots.add(
                        PanoramaShot(
                            bitmap = bmp,
                            xAxis = xAxis,
                            yAxis = yAxis,
                            zAxis = zAxis,
                            yawDegrees = unwrappedYaw,
                            pitchDegrees = pitch,
                            projX = projectionMatrix[0],
                            projY = projectionMatrix[5]
                        )
                    )
                    lastShotForward = fwd
                }
            }
            count = panoramaShots.size
            swept = PanoramaStitcher.sweptDegrees(panoramaShots)
        }
        if (count != lastReportedShots || tooFast != lastTooFast) {
            lastReportedShots = count
            lastTooFast = tooFast
            mainHandler.post { onPanoramaProgress(count, swept, tooFast) }
        }
    }

    private fun angleBetween(a: FloatArray, b: FloatArray): Float {
        val dot = (a[0] * b[0] + a[1] * b[1] + a[2] * b[2]).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(dot.toDouble())).toFloat()
    }

    /** Reads the current framebuffer (camera + assets) and returns it upright at panorama shot size. */
    private fun readFramebufferScaled(): Bitmap? {
        val w = viewportWidth
        val h = viewportHeight
        if (w <= 0 || h <= 0) return null
        return try {
            val needed = w * h * 4
            var buffer = readBuffer
            if (buffer == null || buffer.capacity() != needed) {
                buffer = ByteBuffer.allocateDirect(needed).order(ByteOrder.nativeOrder())
                readBuffer = buffer
            }
            buffer!!.clear()
            GLES20.glReadPixels(0, 0, w, h, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
            val raw = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            buffer.rewind()
            raw.copyPixelsFromBuffer(buffer)
            val s = PANORAMA_SHOT_WIDTH_PX.toFloat() / w
            val m = android.graphics.Matrix().apply { preScale(s, -s) }
            val out = Bitmap.createBitmap(raw, 0, 0, w, h, m, true)
            raw.recycle()
            out
        } catch (_: Exception) {
            null
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
            val groundHit = hitResults.firstOrNull { isUsableGroundHit(it) }

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

    /**
     * Places the planning origin on the ground and rotates it once so canvas north
     * matches [magneticHeadingDegrees] (prefer layout.anchor.headingDegrees at the call site;
     * fall back to live compass). The ARCore session anchor is ephemeral — never persisted to JSON.
     */
    fun setOriginAtCenterHit(magneticHeadingDegrees: Float, liveHeadingDegrees: Float = magneticHeadingDegrees): Boolean {
        val hit = currentCenterHit
        if (hit != null) {
            val plane = hit.trackable as? Plane
            if (plane != null) {
                val newAnchor = plane.createAnchor(
                    northAlignedPose(hit.hitPose, magneticHeadingDegrees, liveHeadingDegrees)
                )
                originAnchor?.detach()
                originAnchor = newAnchor
                mainHandler.post { onOriginCalibratedChanged(true) }
                return true
            }
        }

        // Fallback: If center hit wasn't strictly acquired but ground planes exist, anchor to nearest plane center
        val fallbackPlane = bestGroundPlane(latestHorizontalPlanes)
        if (fallbackPlane != null) {
            val newAnchor = fallbackPlane.createAnchor(
                northAlignedPose(fallbackPlane.centerPose, magneticHeadingDegrees, liveHeadingDegrees)
            )
            originAnchor?.detach()
            originAnchor = newAnchor
            mainHandler.post { onOriginCalibratedChanged(true) }
            return true
        }

        return false
    }

    private fun northAlignedPose(sourcePose: Pose, magneticHeadingDegrees: Float, liveHeadingDegrees: Float): Pose {
        // ArHeadingFix.ENABLED chooses between today's yaw and the candidate fix (saved heading minus live heading).
        val yaw = if (ArHeadingFix.ENABLED) {
            ARCoordinateTransformer.originYawDegreesForPlanHeading(
                cameraForwardX = cameraForwardX,
                cameraForwardZ = cameraForwardZ,
                savedHeadingDegrees = magneticHeadingDegrees,
                liveHeadingDegrees = liveHeadingDegrees
            )
        } else {
            ARCoordinateTransformer.originYawDegreesForMagneticNorth(
                cameraForwardX = cameraForwardX,
                cameraForwardZ = cameraForwardZ,
                magneticHeadingDegrees = magneticHeadingDegrees
            )
        }
        val alpha = Math.toRadians((-yaw).toDouble()).toFloat()
        val rotation = Pose.makeRotation(0f, sin(alpha / 2f), 0f, cos(alpha / 2f))
        val translation = Pose.makeTranslation(sourcePose.tx(), sourcePose.ty(), sourcePose.tz())
        return translation.compose(rotation)
    }

    fun resetOrigin() {
        originAnchor?.detach()
        originAnchor = null
        currentCenterHit = null
        snappedGroundY.clear()
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
