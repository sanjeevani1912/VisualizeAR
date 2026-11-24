package com.example.dhruvar.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.opengl.GLSurfaceView
import android.provider.Settings
import android.view.Display
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import com.example.dhruvar.ui.ar.render.PanoramaStitcher
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Adjust
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.PanoramaHorizontal
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dhruvar.data.location.DefaultDeviceLocationControllerProvider
import com.example.dhruvar.domain.ar.ArGeoGuidance
import com.example.dhruvar.domain.ar.ArGeoProximityStatus
import com.example.dhruvar.domain.ar.ArAssetSize
import com.example.dhruvar.domain.ar.ArVisualizationScale
import com.example.dhruvar.domain.spatial.GeographicCoordinateConverter
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.ar.capture.ARCaptureManager
import com.example.dhruvar.ui.ar.capture.CaptureMetadata
import com.example.dhruvar.ui.ar.capture.SavedImage
import com.example.dhruvar.ui.ar.capture.SavedImageSupport
import com.example.dhruvar.data.remote.AttachmentUploader
import com.example.dhruvar.data.remote.SendEligibility
import com.example.dhruvar.data.remote.SentPlanStore
import com.example.dhruvar.data.remote.SessionStore
import com.example.dhruvar.data.remote.UploadResult
import com.example.dhruvar.ui.ar.capture.buildViewImageIntent
import com.example.dhruvar.ui.ar.render.ARDistanceLineLabelProjection
import com.example.dhruvar.ui.ar.render.ARObjectLabelProjection
import com.example.dhruvar.ui.ar.render.AROriginLabelProjection
import com.example.dhruvar.ui.ar.render.ARRenderer
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.ArGeoBriefingPanel
import com.example.dhruvar.ui.components.ArAssetSizeRow
import com.example.dhruvar.ui.components.ArScaleModeRow
import com.example.dhruvar.ui.components.DeviceCompassWidget
import com.example.dhruvar.ui.components.PrimaryButton
import com.example.dhruvar.ui.components.rememberCompassState
import com.example.dhruvar.ui.components.toIcon
import com.example.dhruvar.ui.theme.Cacao
import com.example.dhruvar.ui.theme.Coal
import com.example.dhruvar.ui.theme.FieldAnchor
import com.example.dhruvar.ui.theme.GoldenHour
import com.example.dhruvar.ui.theme.Meadow
import com.example.dhruvar.ui.theme.Mist
import com.example.dhruvar.ui.theme.Moss
import com.example.dhruvar.ui.theme.OverlayFaint
import com.example.dhruvar.ui.theme.OverlayFill
import com.example.dhruvar.ui.theme.OverlayFillStrong
import com.example.dhruvar.ui.theme.OverlayHairline
import com.example.dhruvar.ui.theme.OverlayMuted
import com.example.dhruvar.ui.theme.OverlayScrim
import com.example.dhruvar.ui.theme.Rust
import com.example.dhruvar.viewmodel.PlanningViewModel
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Screen providing native Android ARCore camera streaming, lifecycle management,
 * horizontal ground plane detection, planning origin calibration, 3D asset visualization,
 * interactive selection, and screen-space billboarded tactical labels.
 */
@Composable
fun ARVisualizationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanningViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val uiState by viewModel.uiState.collectAsState()
    val compassState by rememberCompassState()

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionDeniedPermanently by remember { mutableStateOf(false) }

    // ARCore availability states: "CHECKING", "SUPPORTED", "UNSUPPORTED", "NEEDS_INSTALL"
    var arCoreStatus by remember { mutableStateOf("CHECKING") }
    var arSession by remember { mutableStateOf<Session?>(null) }
    var detectedPlaneCount by remember { mutableIntStateOf(0) }
    var trackingStatus by remember { mutableStateOf("Initializing ARCore...") }
    var glSurfaceViewRef by remember { mutableStateOf<GLSurfaceView?>(null) }

    // Calibration states
    var canSetOrigin by remember { mutableStateOf(false) }
    var isOriginCalibrated by remember { mutableStateOf(false) }
    // Expanded while the operator still needs the prep briefing; collapses once the origin is set.
    var briefingExpanded by rememberSaveable { mutableStateOf(true) }
    var activePanel by rememberSaveable { mutableIntStateOf(AR_PANEL_NONE) }
    var calibrationFeedback by remember { mutableStateOf<String?>(null) }
    var groundDebugLine by remember { mutableStateOf("") }
    var visualizationScaleMode by remember { mutableStateOf(ArVisualizationScale.FULL) }
    var assetSize by remember { mutableStateOf(ArAssetSize.DEFAULT) }

    // Phase 5: geo briefing via satellite GNSS (GPS guides to site; AR tracking owns local stability)
    val locationController = remember { DefaultDeviceLocationControllerProvider.getController() }
    val locationState by locationController.state.collectAsState()
    var hasLocationPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                PackageManager.PERMISSION_GRANTED
        )
    }

    val geoGuidance = remember(uiState.currentLayout.anchor, locationState.lastFix) {
        ArGeoGuidance.from(
            anchor = uiState.currentLayout.anchor,
            deviceFix = locationState.lastFix
        )
    }

    // Screen-space 2D projections from OpenGL pipeline
    var projectedOriginLabel by remember { mutableStateOf<AROriginLabelProjection?>(null) }
    var projectedObjectLabels by remember { mutableStateOf<List<ARObjectLabelProjection>>(emptyList()) }
    var projectedDistanceBadge by remember { mutableStateOf<ARDistanceLineLabelProjection?>(null) }

    // Part 13: AR Capture & Preview states
    var isCapturing by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showPreviewDialog by remember { mutableStateOf(false) }
    var isSavingImage by remember { mutableStateOf(false) }
    var captureFeedbackMessage by remember { mutableStateOf<String?>(null) }
    // The last picture saved to the gallery; the banner offers "Open" only while its own message is showing.
    var lastSavedImage by remember { mutableStateOf<SavedImage?>(null) }
    // Sending the picture to the plan on the dashboard server (optional; the gallery copy never depends on it).
    val sessionStore = remember { SessionStore(context) }
    val sentPlanStore = remember { SentPlanStore(context) }
    var isSendingImage by remember { mutableStateOf(false) }
    var sendError by remember { mutableStateOf<String?>(null) }
    var savedForBitmap by remember { mutableStateOf<Pair<Bitmap, SavedImage>?>(null) }

    // Panorama: hold the button and sweep the phone; shots are stitched on release.
    val panoramaScope = rememberCoroutineScope()
    var isPanoramaRecording by remember { mutableStateOf(false) }
    var isPanoramaStitching by remember { mutableStateOf(false) }
    var panoramaShotCount by remember { mutableIntStateOf(0) }
    var panoramaSweptDegrees by remember { mutableStateOf(0f) }
    var panoramaTooFast by remember { mutableStateOf(false) }
    var capturedIsPanorama by remember { mutableStateOf(false) }

    LaunchedEffect(captureFeedbackMessage) {
        if (captureFeedbackMessage != null) {
            kotlinx.coroutines.delay(if (lastSavedImage?.message == captureFeedbackMessage) 8000 else 4000)
            captureFeedbackMessage = null
        }
    }

    LaunchedEffect(calibrationFeedback) {
        if (calibrationFeedback != null) {
            kotlinx.coroutines.delay(4500)
            calibrationFeedback = null
        }
    }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true
        hasLocationPermission = granted
        locationController.onPermissionResult(granted)
        if (granted) {
            locationController.startHighAccuracyUpdates()
            locationController.refreshLocation()
        }
    }

    LaunchedEffect(Unit) {
        if (hasLocationPermission) {
            locationController.onPermissionResult(true)
            locationController.startHighAccuracyUpdates()
            locationController.refreshLocation()
        } else {
            locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            locationController.stopUpdates()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        if (!granted) {
            val activity = context.findActivity()
            if (activity != null && !activity.shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                permissionDeniedPermanently = true
            }
        }
    }

    // Check ARCore device availability
    LaunchedEffect(Unit) {
        try {
            when (ArCoreApk.getInstance().checkAvailability(context)) {
                ArCoreApk.Availability.SUPPORTED_INSTALLED -> {
                    arCoreStatus = "SUPPORTED"
                }
                ArCoreApk.Availability.SUPPORTED_APK_TOO_OLD,
                ArCoreApk.Availability.SUPPORTED_NOT_INSTALLED -> {
                    arCoreStatus = "NEEDS_INSTALL"
                }
                ArCoreApk.Availability.UNSUPPORTED_DEVICE_NOT_CAPABLE -> {
                    arCoreStatus = "UNSUPPORTED"
                }
                else -> {
                    arCoreStatus = "SUPPORTED"
                }
            }
        } catch (_: Exception) {
            arCoreStatus = "SUPPORTED"
        }
    }

    // AR Renderer instance
    val arRenderer = remember {
        ARRenderer(
            onPlaneCountChanged = { count -> detectedPlaneCount = count },
            onStatusChanged = { status -> trackingStatus = status },
            onCenterHitChanged = { canSet -> canSetOrigin = canSet },
            onOriginCalibratedChanged = { calibrated -> isOriginCalibrated = calibrated },
            onProjectionsUpdated = { origin, objects, distBadge ->
                projectedOriginLabel = origin
                projectedObjectLabels = objects
                projectedDistanceBadge = distBadge
            },
            onObjectTapped = { tappedId ->
                if (tappedId != null) {
                    viewModel.selectObject(tappedId)
                } else {
                    viewModel.deselectObject()
                }
            }
        ).apply {
            onGroundDebug = { line -> groundDebugLine = line }
            onPanoramaProgress = { shots, swept, tooFast ->
                panoramaShotCount = shots
                panoramaSweptDegrees = swept
                panoramaTooFast = tooFast
            }
            displayRotationProvider = {
                val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                @Suppress("DEPRECATION")
                wm?.defaultDisplay?.rotation ?: Display.DEFAULT_DISPLAY
            }
        }
    }

    // Synchronize current layout & selection with ARRenderer
    LaunchedEffect(uiState.currentLayout, uiState.selectedObjectId) {
        arRenderer.currentLayout = uiState.currentLayout
        arRenderer.selectedObjectId = uiState.selectedObjectId
    }

    LaunchedEffect(visualizationScaleMode) {
        arRenderer.visualizationScale = visualizationScaleMode.scaleFactor
    }

    LaunchedEffect(assetSize) {
        arRenderer.assetSizeFactor = assetSize
    }

    LaunchedEffect(isOriginCalibrated) {
        briefingExpanded = !isOriginCalibrated
        if (isOriginCalibrated) {
            val n = uiState.currentLayout.objects.size
            calibrationFeedback = if (arRenderer.originOnRaisedSurface) {
                "Origin is on a raised surface; aim at the floor and set it again"
            } else {
                "Origin calibrated — rendering $n object${if (n == 1) "" else "s"}"
            }
        }
    }

    // Session initialization & resume helper
    fun resumeSession(surfaceView: GLSurfaceView) {
        if (!hasCameraPermission || arCoreStatus != "SUPPORTED") return

        try {
            if (arSession == null) {
                val session = Session(context)
                val config = Config(session).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.BLOCKING
                    focusMode = Config.FocusMode.AUTO
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                        depthMode = Config.DepthMode.AUTOMATIC
                    }
                }
                session.configure(config)
                arSession = session
                arRenderer.currentLayout = uiState.currentLayout
                arRenderer.selectedObjectId = uiState.selectedObjectId
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val rotation = windowManager?.defaultDisplay?.rotation ?: Display.DEFAULT_DISPLAY
            arRenderer.displayRotation = rotation
            arRenderer.notifySessionResumed()

            // 1. Resume ARCore session first
            arSession?.resume()
            // 2. Attach session to renderer and mark unpaused
            arRenderer.session = arSession
            arRenderer.isSessionPaused = false
            // 3. Resume GLSurfaceView render loop
            surfaceView.onResume()
        } catch (e: Exception) {
            arRenderer.isSessionPaused = true
            trackingStatus = when (e) {
                is UnavailableArcoreNotInstalledException -> "ARCore not installed"
                is UnavailableApkTooOldException -> "Update Google Play Services for AR"
                is UnavailableSdkTooOldException -> "App update required"
                is UnavailableDeviceNotCompatibleException -> "Device incompatible with AR"
                is CameraNotAvailableException -> "Camera busy or unavailable"
                else -> "AR Session error: ${e.message}"
            }
        }
    }

    fun pauseSession(surfaceView: GLSurfaceView?) {
        arRenderer.isSessionPaused = true
        try {
            surfaceView?.onPause()
        } catch (_: Exception) {}
        try {
            arSession?.pause()
        } catch (_: Exception) {}
    }

    fun destroySession(surfaceView: GLSurfaceView?) {
        arRenderer.isSessionPaused = true
        try {
            surfaceView?.onPause()
        } catch (_: Exception) {}
        arRenderer.session = null
        arRenderer.resetOrigin()
        try {
            arSession?.pause()
            arSession?.close()
        } catch (_: Exception) {}
        arSession = null
    }

    // Lifecycle synchronization
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceViewRef?.let { view ->
                        resumeSession(view)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    pauseSession(glSurfaceViewRef)
                }
                Lifecycle.Event.ON_DESTROY -> {
                    destroySession(glSurfaceViewRef)
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            destroySession(glSurfaceViewRef)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AR VISUALIZATION",
                onNavigateBack = {
                    destroySession(glSurfaceViewRef)
                    onNavigateBack()
                }
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                // 1. Camera Permission Required View
                !hasCameraPermission -> {
                    PermissionRequiredView(
                        isPermanentlyDenied = permissionDeniedPermanently,
                        onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                        onOpenSettings = {
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        },
                        onNavigateBack = onNavigateBack
                    )
                }

                // 2. ARCore Not Supported on Device View
                arCoreStatus == "UNSUPPORTED" -> {
                    UnsupportedDeviceView(onNavigateBack = onNavigateBack)
                }

                // 3. ARCore Needs Install / Update View
                arCoreStatus == "NEEDS_INSTALL" -> {
                    NeedsInstallView(
                        onInstall = {
                            val activity = context.findActivity()
                            if (activity != null) {
                                try {
                                    ArCoreApk.getInstance().requestInstall(activity, true)
                                } catch (_: Exception) {}
                            }
                        },
                        onNavigateBack = onNavigateBack
                    )
                }

                // 4. ARCore Availability Checking View
                arCoreStatus == "CHECKING" -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Moss)
                    }
                }

                // 5. Active AR Camera & 3D Visualization View
                else -> {
                    // OpenGL Surface View with Touch Tap Handling
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                preserveEGLContextOnPause = true
                                setEGLContextClientVersion(2)
                                setEGLConfigChooser(8, 8, 8, 8, 16, 0)
                                setRenderer(arRenderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                glSurfaceViewRef = this
                                if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
                                    resumeSession(this)
                                }
                            }
                        },
                        update = { glView ->
                            glSurfaceViewRef = glView
                            if (lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                                (arSession == null || arRenderer.isSessionPaused)
                            ) {
                                resumeSession(glView)
                            }
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    arRenderer.handleScreenTap(offset.x, offset.y)
                                }
                            }
                    )

                    // 4.1 Screen-Space Floating Object Labels (Billboarded above each 3D asset)
                    if (isOriginCalibrated) {
                        projectedObjectLabels.forEach { label ->
                            if (label.isVisible) {
                                val labelOffsetX = with(density) { (label.screenX).toDp() - 40.dp }
                                val labelOffsetY = with(density) { (label.screenY).toDp() - 28.dp }

                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                x = (label.screenX - 80f).roundToInt(),
                                                y = (label.screenY - 50f).roundToInt()
                                            )
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            if (label.isSelected) FieldAnchor.copy(alpha = 0.90f) else OverlayFill
                                        )
                                        .border(
                                            1.dp,
                                            if (label.isSelected) Mist else Moss.copy(alpha = 0.6f),
                                            RoundedCornerShape(6.dp)
                                        )
                                        .clickable { viewModel.selectObject(label.objectId) }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (label.isSelected) Mist else Moss)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = label.name.uppercase(),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                // Selected labels sit on Rust, unselected on cream
                                                color = if (label.isSelected) Mist else Coal
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 4.2 Screen-Space Planning Origin Badge
                        projectedOriginLabel?.let { originProj ->
                            if (originProj.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                x = (originProj.screenX - 70f).roundToInt(),
                                                y = (originProj.screenY - 35f).roundToInt()
                                            )
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(OverlayFill)
                                        .border(1.dp, FieldAnchor, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(FieldAnchor)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "PLANNING ORIGIN",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FieldAnchor
                                            )
                                        )
                                    }
                                }
                            }
                        }

                        // 4.3 3D Distance Line Midpoint Badge (when an object is selected)
                        projectedDistanceBadge?.let { badge ->
                            if (badge.isVisible) {
                                Box(
                                    modifier = Modifier
                                        .offset {
                                            IntOffset(
                                                x = (badge.screenX - 50f).roundToInt(),
                                                y = (badge.screenY - 30f).roundToInt()
                                            )
                                        }
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(FieldAnchor.copy(alpha = 0.92f))
                                        .border(1.dp, Mist.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Straighten,
                                            contentDescription = null,
                                            tint = Mist,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "≈ ${String.format(Locale.US, "%.1f", badge.distanceMeters)} m",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Mist
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 4.4 Center-screen Reticle (shown only during calibration)
                    if (!isOriginCalibrated) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(OverlayScrim)
                                        // The reticle sits on a scrim over live camera rather than
                                        // a cream panel, so it needs the light end of the palette.
                                        .border(
                                            2.dp,
                                            if (canSetOrigin) Meadow else Mist.copy(alpha = 0.70f),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "Target reticle",
                                        tint = if (canSetOrigin) Meadow else Mist,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .background(OverlayFill, RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = when {
                                            canSetOrigin -> "2/3 · Target acquired, tap Set Origin"
                                            detectedPlaneCount > 0 -> "Aim the ring at the detected ground"
                                            else -> "1/3 · Move slowly to scan the floor"
                                        },
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canSetOrigin) Moss else Coal
                                        )
                                    )
                                }
                            }
                        }
                    }

                    // 4.5 Tactical HUD Overlay Controls & Selected Object Inspector
                    if (isCapturing) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .clip(RoundedCornerShape(10.dp))
                                .background(OverlayFillStrong)
                                .border(1.dp, Moss, RoundedCornerShape(10.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = Moss
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Capturing…",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Coal
                                    )
                                )
                            }
                        }
                    } else {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top: one slim status row, tools open on demand, so the camera stays clear.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val counterAccent = when {
                                    isOriginCalibrated -> Moss
                                    detectedPlaneCount > 0 -> Cacao
                                    else -> Rust
                                }
                                HudStatusPill(
                                    dotColor = when {
                                        isOriginCalibrated -> FieldAnchor
                                        canSetOrigin -> Moss
                                        else -> Cacao
                                    },
                                    text = if (isOriginCalibrated) "Tracking normal" else trackingStatus,
                                    countText = if (isOriginCalibrated) {
                                        "${uiState.currentLayout.objects.size} assets"
                                    } else {
                                        "$detectedPlaneCount planes"
                                    },
                                    countColor = counterAccent,
                                    modifier = Modifier.weight(1f)
                                )
                                HudRoundButton(
                                    icon = Icons.Default.Explore,
                                    description = "Location and compass",
                                    active = activePanel == AR_PANEL_GEO,
                                    tint = when (geoGuidance.status) {
                                        ArGeoProximityStatus.NEAR_ORIGIN -> Moss
                                        ArGeoProximityStatus.NO_GEO -> Coal
                                        else -> Rust
                                    },
                                    onClick = { activePanel = if (activePanel == AR_PANEL_GEO) AR_PANEL_NONE else AR_PANEL_GEO }
                                )
                                HudRoundButton(
                                    icon = Icons.Default.Tune,
                                    description = "Scale and asset size",
                                    active = activePanel == AR_PANEL_TUNE,
                                    tint = Coal,
                                    onClick = { activePanel = if (activePanel == AR_PANEL_TUNE) AR_PANEL_NONE else AR_PANEL_TUNE }
                                )
                            }

                            when (activePanel) {
                                AR_PANEL_GEO -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    ArGeoBriefingPanel(
                                        guidance = geoGuidance,
                                        expanded = true,
                                        onExpandedChange = { activePanel = AR_PANEL_NONE },
                                        modifier = Modifier.weight(1f)
                                    )
                                    DeviceCompassWidget(compassState = compassState)
                                }
                                AR_PANEL_TUNE -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    ArScaleModeRow(
                                        selected = visualizationScaleMode,
                                        onSelected = { visualizationScaleMode = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    ArAssetSizeRow(
                                        factor = assetSize,
                                        onFactorChange = { assetSize = it },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text(
                                        text = "AR placement is an approximate visualization and depends on device tracking and surface detection.",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Default,
                                            fontSize = 10.sp,
                                            color = Coal
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(OverlayFill)
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            if (com.example.dhruvar.BuildConfig.DEBUG && isOriginCalibrated && groundDebugLine.isNotEmpty()) {
                                HudNote(text = groundDebugLine, color = FieldAnchor)
                            }

                            calibrationFeedback?.let { msg ->
                                HudNote(text = msg, color = Moss)
                            }

                            if (isOriginCalibrated && uiState.currentLayout.objects.isEmpty()) {
                                HudNote(
                                    text = "No objects placed. Go back to the 2D planner to add assets.",
                                    color = FieldAnchor
                                )
                            }
                        }

                        // Bottom: selected-asset card + one rounded action bar.
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            uiState.selectedObject?.let { selectedObj ->
                                val distMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObj)

                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(OverlayFillStrong)
                                        .border(1.5.dp, FieldAnchor, RoundedCornerShape(14.dp))
                                        .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(28.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(FieldAnchor.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = selectedObj.assetType.toIcon(),
                                                contentDescription = null,
                                                tint = FieldAnchor,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = selectedObj.name,
                                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                color = Coal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = selectedObj.assetType.displayName.uppercase(),
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = Moss
                                                )
                                            )
                                        }
                                        Text(
                                            text = "${String.format(Locale.US, "%.1f", distMeters)} m",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = FieldAnchor
                                            )
                                        )
                                        IconButton(
                                            onClick = { viewModel.deselectObject() },
                                            modifier = Modifier.size(36.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Deselect",
                                                tint = OverlayMuted,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "X ${String.format(Locale.US, "%.1f", selectedObj.x)}m · Z ${String.format(Locale.US, "%.1f", selectedObj.z)}m · ROT ${selectedObj.rotationDegrees.toInt()}° · ${selectedObj.widthMeters}×${selectedObj.lengthMeters}m",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            color = Coal
                                        ),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )

                                    // Optional field-verify: derived WGS84 from meter offsets (not live GPS drag)
                                    val layoutAnchor = uiState.currentLayout.anchor
                                    if (layoutAnchor.hasGeographicFix) {
                                        val derived = runCatching {
                                            GeographicCoordinateConverter.metersOffsetToLatLng(
                                                layoutAnchor,
                                                selectedObj.x,
                                                selectedObj.z
                                            )
                                        }.getOrNull()
                                        if (derived != null) {
                                            Text(
                                                text = String.format(
                                                    Locale.US,
                                                    "GEO ≈ %.6f, %.6f",
                                                    derived.latitude,
                                                    derived.longitude
                                                ),
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.sp,
                                                    color = Moss
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // Panorama progress sits above the bar only while it is in use.
                            if (isOriginCalibrated && (isPanoramaRecording || isPanoramaStitching)) {
                                val panoLabel = when {
                                    isPanoramaStitching -> "Stitching panorama..."
                                    panoramaTooFast -> "Slow down!"
                                    else -> "Sweeping · $panoramaShotCount shots · ${panoramaSweptDegrees.toInt()}° · release to finish"
                                }
                                HudNote(
                                    text = panoLabel,
                                    color = if (isPanoramaRecording && panoramaTooFast) FieldAnchor else Moss
                                )
                            }

                            // 4.7 Action bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(32.dp))
                                    .background(OverlayFill)
                                    .border(1.dp, OverlayHairline, RoundedCornerShape(32.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = if (isOriginCalibrated) Arrangement.SpaceEvenly else Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val backAction = {
                                    arRenderer.resetOrigin()
                                    viewModel.deselectObject()
                                    try {
                                        arSession?.pause()
                                        arSession?.close()
                                    } catch (_: Exception) {}
                                    arSession = null
                                    onNavigateBack()
                                }

                                if (!isOriginCalibrated) {
                                    HudAction(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        label = "Back",
                                        onClick = backAction
                                    )
                                    // Set Planning Origin Action — prefer saved layout heading, else live compass
                                    Button(
                                        onClick = {
                                            val heading = uiState.currentLayout.anchor.headingDegrees
                                                ?: compassState.magneticHeadingDegrees
                                            val ok = arRenderer.setOriginAtCenterHit(heading)
                                            if (ok) {
                                                val n = uiState.currentLayout.objects.size
                                                calibrationFeedback = if (arRenderer.originOnRaisedSurface) {
                                                    "Origin is on a raised surface; aim at the floor and set it again"
                                                } else {
                                                    "Origin calibrated — rendering $n object${if (n == 1) "" else "s"}"
                                                }
                                            }
                                        },
                                        enabled = canSetOrigin,
                                        shape = RoundedCornerShape(26.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Moss,
                                            contentColor = Mist,
                                            disabledContainerColor = OverlayHairline,
                                            disabledContentColor = OverlayFaint
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(52.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = if (canSetOrigin) "Set Planning Origin" else "Scanning ground...",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                                        )
                                    }
                                } else {
                                    HudAction(
                                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                                        label = "Back",
                                        onClick = backAction
                                    )
                                    HudAction(
                                        icon = Icons.Default.Refresh,
                                        label = "Reset",
                                        onClick = {
                                            arRenderer.resetOrigin()
                                            viewModel.deselectObject()
                                        }
                                    )
                                    // Capture Visualization Action
                                    HudAction(
                                        icon = Icons.Default.CameraAlt,
                                        label = "Capture",
                                        primary = true,
                                        onClick = {
                                            val surfaceView = glSurfaceViewRef
                                            if (surfaceView != null) {
                                                isCapturing = true
                                                val metadata = CaptureMetadata(
                                                    layoutName = uiState.currentLayout.name,
                                                    objectCount = uiState.currentLayout.objects.size,
                                                    isCalibrated = isOriginCalibrated
                                                )
                                                ARCaptureManager.captureARView(surfaceView, arRenderer) { result ->
                                                    isCapturing = false
                                                    result.onSuccess { rawBitmap ->
                                                        val composite = ARCaptureManager.compositeLabelsAndMetadata(
                                                            baseBitmap = rawBitmap,
                                                            objectLabels = projectedObjectLabels,
                                                            originLabel = projectedOriginLabel,
                                                            distanceBadge = projectedDistanceBadge,
                                                            metadata = metadata
                                                        )
                                                        capturedBitmap = composite
                                                        capturedIsPanorama = false
                                                        showPreviewDialog = true
                                                    }.onFailure { err ->
                                                        captureFeedbackMessage = "Capture failed: ${err.localizedMessage ?: "Unknown error"}"
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    // 4.7b Hold-to-record panorama (camera feed + assets in one wide photo)
                                    HudAction(
                                        icon = Icons.Default.PanoramaHorizontal,
                                        label = if (isPanoramaRecording) "Release" else "Panorama",
                                        highlighted = isPanoramaRecording || isPanoramaStitching,
                                        circleModifier = Modifier.pointerInput(isPanoramaStitching) {
                                            if (isPanoramaStitching) return@pointerInput
                                            detectTapGestures(
                                                onPress = {
                                                    isPanoramaRecording = true
                                                    arRenderer.startPanorama()
                                                    tryAwaitRelease()
                                                    isPanoramaRecording = false
                                                    val shots = arRenderer.stopPanorama()
                                                    if (shots.size < 2) {
                                                        shots.forEach { s -> s.bitmap.recycle() }
                                                        captureFeedbackMessage = "Panorama too short - hold and sweep slowly"
                                                    } else {
                                                        isPanoramaStitching = true
                                                        panoramaScope.launch(kotlinx.coroutines.Dispatchers.Default) {
                                                            val result = try {
                                                                PanoramaStitcher.stitch(shots)
                                                            } catch (_: Throwable) {
                                                                null
                                                            }
                                                            shots.forEach { s -> s.bitmap.recycle() }
                                                            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                                                                isPanoramaStitching = false
                                                                if (result != null) {
                                                                    capturedBitmap = result
                                                                    capturedIsPanorama = true
                                                                    showPreviewDialog = true
                                                                } else {
                                                                    captureFeedbackMessage = "Panorama failed - try again"
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            )
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

            // 5. Capture Feedback Toast Banner
            captureFeedbackMessage?.let { msg ->
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 16.dp, start = 16.dp, end = 16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(OverlayFillStrong)
                        .border(1.dp, Moss, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Moss,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Coal
                            )
                        )
                        val saved = lastSavedImage
                        if (saved != null && saved.message == msg) {
                            Spacer(modifier = Modifier.width(12.dp))
                            TextButton(
                                onClick = {
                                    try {
                                        context.startActivity(buildViewImageIntent(saved.uri))
                                    } catch (_: ActivityNotFoundException) {
                                        captureFeedbackMessage = "No app on this phone can open pictures"
                                    } catch (_: SecurityException) {
                                        captureFeedbackMessage = "Could not open the picture"
                                    }
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                            ) {
                                Text("Open", fontWeight = FontWeight.Bold, color = Moss)
                            }
                        }
                    }
                }
            }

            // 6. Capture Preview Modal Dialog
            if (showPreviewDialog && capturedBitmap != null) {
                Dialog(
                    onDismissRequest = {
                        showPreviewDialog = false
                        capturedBitmap = null
                    },
                    properties = DialogProperties(usePlatformDefaultWidth = false)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(OverlayFillStrong)
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = GoldenHour),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Moss.copy(alpha = 0.5f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "CAPTURE PREVIEW",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Coal
                                        )
                                    )
                                    IconButton(
                                        onClick = {
                                            showPreviewDialog = false
                                            capturedBitmap = null
                                        },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Close preview",
                                            tint = OverlayMuted
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                // Preview Image Display
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(380.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, OverlayHairline, RoundedCornerShape(8.dp))
                                        .background(Coal),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (capturedIsPanorama) {
                                        val pano = capturedBitmap!!
                                        Image(
                                            bitmap = pano.asImageBitmap(),
                                            contentDescription = "Panorama",
                                            modifier = Modifier
                                                .fillMaxHeight()
                                                .horizontalScroll(rememberScrollState())
                                                .aspectRatio(pano.width.toFloat() / pano.height.toFloat()),
                                            contentScale = ContentScale.FillBounds
                                        )
                                    } else {
                                        Image(
                                            bitmap = capturedBitmap!!.asImageBitmap(),
                                            contentDescription = "Captured AR View",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(16.dp))

                                // Send to plan: offered only when signed in and the plan has a server id.
                                val sendOption = remember(showPreviewDialog, capturedBitmap, uiState.currentLayout.id) {
                                    AttachmentUploader.eligibility(
                                        signedIn = sessionStore.token() != null,
                                        sent = sentPlanStore.all()[uiState.currentLayout.id]
                                    )
                                }
                                val sendHint = when {
                                    sendError != null -> sendError
                                    sendOption is SendEligibility.Blocked -> sendOption.reason
                                    else -> null
                                }
                                if (sendHint != null) {
                                    Text(
                                        text = sendHint,
                                        style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = if (sendError != null) FieldAnchor else OverlayMuted)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                Button(
                                    onClick = {
                                        val bmp = capturedBitmap ?: return@Button
                                        val ready = sendOption as? SendEligibility.Ready ?: return@Button
                                        val token = sessionStore.token()
                                        if (token == null) {
                                            sendError = "Not signed in. Sign in again, then retry."
                                            return@Button
                                        }
                                        val base = sessionStore.serverUrl.ifBlank { com.example.dhruvar.BuildConfig.DEFAULT_SERVER_URL }
                                        val isPano = capturedIsPanorama
                                        val metadata = CaptureMetadata(
                                            layoutName = uiState.currentLayout.name,
                                            objectCount = uiState.currentLayout.objects.size,
                                            isCalibrated = isOriginCalibrated
                                        )
                                        isSendingImage = true
                                        sendError = null
                                        panoramaScope.launch {
                                            // Save first, so a failed upload never loses the picture.
                                            var saved = savedForBitmap?.takeIf { it.first === bmp }?.second
                                            var saveNote = ""
                                            if (saved == null) {
                                                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    ARCaptureManager.saveImageToGallery(context, bmp, metadata)
                                                }.onSuccess {
                                                    saved = it
                                                    savedForBitmap = bmp to it
                                                    lastSavedImage = it
                                                }.onFailure { saveNote = " (a gallery copy could not be saved)" }
                                            }
                                            val result = AttachmentUploader.sendBitmap(
                                                baseUrl = base,
                                                token = token,
                                                planId = ready.planId,
                                                bitmap = bmp,
                                                caption = AttachmentUploader.defaultCaption(isPano, metadata.timestampMillis),
                                                fileName = saved?.fileName ?: ARCaptureManager.buildImageFileName(metadata)
                                            )
                                            isSendingImage = false
                                            when (result) {
                                                is UploadResult.Success -> {
                                                    captureFeedbackMessage = "Sent to plan ${ready.planCode} as field image" +
                                                        (saved?.let { ". Saved as ${it.fileName}" } ?: saveNote)
                                                    showPreviewDialog = false
                                                    capturedBitmap = null
                                                }
                                                is UploadResult.Failure -> {
                                                    sendError = result.message +
                                                        (saved?.let { " The picture is saved in ${SavedImageSupport.FOLDER}." } ?: "")
                                                }
                                            }
                                        }
                                    },
                                    enabled = sendOption is SendEligibility.Ready && !isSendingImage && !isSavingImage,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Coal, contentColor = Mist)
                                ) {
                                    if (isSendingImage) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Mist)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Sending...", fontWeight = FontWeight.Bold)
                                    } else {
                                        Text(if (sendError != null) "Retry send to plan" else "Send to plan", fontWeight = FontWeight.Bold)
                                    }
                                }
                                Spacer(modifier = Modifier.height(8.dp))

                                // Action Buttons: Retake and Save
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            showPreviewDialog = false
                                            capturedBitmap = null
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = OverlayHairline,
                                            contentColor = Coal
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retake", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Button(
                                        onClick = {
                                            val bitmapToSave = capturedBitmap ?: return@Button
                                            isSavingImage = true
                                            val metadata = CaptureMetadata(
                                                layoutName = uiState.currentLayout.name,
                                                objectCount = uiState.currentLayout.objects.size,
                                                isCalibrated = isOriginCalibrated
                                            )
                                            // JPEG compression of a big panorama is slow: do it off the main thread.
                                            panoramaScope.launch {
                                                val saveResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                                                    ARCaptureManager.saveImageToGallery(
                                                        context = context,
                                                        bitmap = bitmapToSave,
                                                        metadata = metadata
                                                    )
                                                }
                                                isSavingImage = false
                                                saveResult.onSuccess { saved ->
                                                    lastSavedImage = saved
                                                    captureFeedbackMessage = saved.message
                                                    showPreviewDialog = false
                                                    capturedBitmap = null
                                                }.onFailure { err ->
                                                    captureFeedbackMessage = "Failed to save: ${err.localizedMessage ?: "Storage error"}"
                                                }
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = Moss,
                                            contentColor = Mist
                                        ),
                                        enabled = !isSavingImage && !isSendingImage
                                    ) {
                                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (isSavingImage) "Saving..." else "Save", fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepIndicator(
    stepNumber: String,
    label: String,
    isCompleted: Boolean,
    isActive: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .clip(CircleShape)
                // Done and in-progress must stay tellable apart: Moss fill for completed,
                // lighter Meadow for the step currently being worked.
                .background(
                    when {
                        isCompleted -> Moss
                        isActive -> Meadow
                        else -> OverlayHairline
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Mist,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Coal else OverlayMuted
                    )
                )
            }
        }
        Text(
            text = label,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = if (isActive || isCompleted) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    isCompleted -> Moss
                    isActive -> Coal
                    else -> OverlayMuted
                }
            )
        )
    }
}

@Composable
private fun PermissionRequiredView(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Moss.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = Moss,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Camera Permission Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Camera access is required to visualize the planning layout in AR. Augmented Reality detects physical floors and surfaces to anchor your models.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                if (isPermanentlyDenied) {
                    PrimaryButton(
                        text = "Open App Settings",
                        icon = Icons.Default.CameraAlt,
                        onClick = onOpenSettings
                    )
                } else {
                    PrimaryButton(
                        text = "Allow Camera",
                        icon = Icons.Default.CameraAlt,
                        onClick = onRequestPermission
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun UnsupportedDeviceView(onNavigateBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = Rust,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "ARCore Unsupported",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "This device does not support Google Play Services for AR (ARCore). You can still design, measure, and save tactical layouts in 2D Planning mode.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "Return to Planner",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = onNavigateBack
                )
            }
        }
    }
}

@Composable
private fun NeedsInstallView(
    onInstall: () -> Unit,
    onNavigateBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Sensors,
                    contentDescription = null,
                    tint = Moss,
                    modifier = Modifier.size(48.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Google Play Services for AR Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "To enable augmented reality visualization, please install or update Google Play Services for AR.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(24.dp))

                PrimaryButton(
                    text = "Install / Update ARCore",
                    icon = Icons.Default.Sensors,
                    onClick = onInstall
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onNavigateBack,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text("Return to Planner")
                }
            }
        }
    }
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

private const val AR_PANEL_NONE = 0
private const val AR_PANEL_GEO = 1
private const val AR_PANEL_TUNE = 2

@Composable
private fun HudStatusPill(
    dotColor: Color,
    text: String,
    countText: String,
    countColor: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(OverlayFill)
            .border(1.dp, dotColor.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(dotColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Coal
            ),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = countText.uppercase(),
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = countColor
            ),
            maxLines = 1
        )
    }
}

@Composable
private fun HudRoundButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    active: Boolean,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (active) tint.copy(alpha = 0.25f) else OverlayFill)
            .border(1.dp, if (active) tint else OverlayHairline, CircleShape)
            .clickable(onClick = onClick)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun HudNote(text: String, color: Color) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = color
        ),
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(OverlayFill)
            .padding(horizontal = 10.dp, vertical = 6.dp)
    )
}

/** Circular action with a caption underneath; `primary` is the larger Capture button. */
@Composable
private fun HudAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    highlighted: Boolean = false,
    circleModifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val diameter = if (primary) 64.dp else 46.dp
    val filled = primary || highlighted
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(diameter)
                .clip(CircleShape)
                .background(if (filled) Moss else OverlayHairline)
                .then(circleModifier)
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (filled) Mist else Coal,
                modifier = Modifier.size(if (primary) 30.dp else 22.dp)
            )
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = TextStyle(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Coal
            ),
            maxLines = 1
        )
    }
}
