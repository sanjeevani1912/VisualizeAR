package com.example.dhruvar.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Place
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.ar.capture.ARCaptureManager
import com.example.dhruvar.ui.ar.capture.CaptureMetadata
import com.example.dhruvar.ui.ar.render.ARDistanceLineLabelProjection
import com.example.dhruvar.ui.ar.render.ARObjectLabelProjection
import com.example.dhruvar.ui.ar.render.AROriginLabelProjection
import com.example.dhruvar.ui.ar.render.ARRenderer
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.PrimaryButton
import com.example.dhruvar.ui.components.toIcon
import com.example.dhruvar.ui.theme.AnchorOrange
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.TacticalGreen
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

    LaunchedEffect(captureFeedbackMessage) {
        if (captureFeedbackMessage != null) {
            kotlinx.coroutines.delay(4000)
            captureFeedbackMessage = null
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
                        CircularProgressIndicator(color = PrecisionBlue)
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
                                            if (label.isSelected) AnchorOrange.copy(alpha = 0.90f) else Color(0xCC0F172A)
                                        )
                                        .border(
                                            1.dp,
                                            if (label.isSelected) Color.White else PrecisionBlue.copy(alpha = 0.6f),
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
                                                .background(if (label.isSelected) Color.White else PrecisionBlue)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = label.name.uppercase(),
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
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
                                        .background(Color(0xCC0F172A))
                                        .border(1.dp, AnchorOrange, RoundedCornerShape(6.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(AnchorOrange)
                                        )
                                        Spacer(modifier = Modifier.width(5.dp))
                                        Text(
                                            text = "PLANNING ORIGIN",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 8.5.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = AnchorOrange
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
                                        .background(AnchorOrange.copy(alpha = 0.92f))
                                        .border(1.dp, Color.White.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Straighten,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(11.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "≈ ${String.format(Locale.US, "%.1f", badge.distanceMeters)} m",
                                            style = TextStyle(
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
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
                                        .background(Color(0x66000000))
                                        .border(
                                            2.dp,
                                            if (canSetOrigin) TacticalGreen else Color(0x99FFFFFF),
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Adjust,
                                        contentDescription = "Target reticle",
                                        tint = if (canSetOrigin) TacticalGreen else Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                Box(
                                    modifier = Modifier
                                        .background(Color(0xCC0F172A), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (canSetOrigin) "Target Acquired • Press Button or Tap Ground" else "Point at floor or tap detected ground",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 9.5.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (canSetOrigin) TacticalGreen else Color.White
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
                                .background(Color(0xEE0F172A))
                                .border(1.dp, PrecisionBlue, RoundedCornerShape(10.dp))
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = PrecisionBlue
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Capturing…",
                                    style = TextStyle(
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                )
                            }
                        }
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Top HUD Section: Tracking Status, Counter Pill & 3-Step AR Preparation Progress
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // Status Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xCC0F172A))
                                            .border(
                                                1.dp,
                                                if (isOriginCalibrated) AnchorOrange else PrecisionBlue.copy(alpha = 0.6f),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(
                                                        when {
                                                            isOriginCalibrated -> AnchorOrange
                                                            canSetOrigin -> TacticalGreen
                                                            else -> Color(0xFFF59E0B)
                                                        }
                                                    )
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = if (isOriginCalibrated) "Tracking normal" else trackingStatus,
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                            )
                                        }
                                    }

                                    // Counter Pill
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xCC0F172A))
                                            .border(
                                                1.dp,
                                                if (isOriginCalibrated) PrecisionBlue else (if (detectedPlaneCount > 0) TacticalGreen else Color(0x6694A3B8)),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = if (isOriginCalibrated) Icons.Default.CheckCircle else Icons.Default.Sensors,
                                                contentDescription = null,
                                                tint = if (isOriginCalibrated) PrecisionBlue else (if (detectedPlaneCount > 0) TacticalGreen else Color(0xFF94A3B8)),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (isOriginCalibrated) "${uiState.currentLayout.objects.size} ASSETS" else "$detectedPlaneCount PLANES",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isOriginCalibrated) PrecisionBlue else (if (detectedPlaneCount > 0) TacticalGreen else Color(0xFF94A3B8))
                                                )
                                            )
                                        }
                                    }
                                }

                                // 3-Step AR Preparation Progress Bar
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xD90F172A)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0x3394A3B8)),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StepIndicator(
                                            stepNumber = "1",
                                            label = "Detect Ground",
                                            isCompleted = detectedPlaneCount > 0,
                                            isActive = detectedPlaneCount == 0
                                        )
                                        Text("›", color = Color(0x6694A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        StepIndicator(
                                            stepNumber = "2",
                                            label = "Set Origin",
                                            isCompleted = isOriginCalibrated,
                                            isActive = detectedPlaneCount > 0 && !isOriginCalibrated
                                        )
                                        Text("›", color = Color(0x6694A3B8), fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        StepIndicator(
                                            stepNumber = "3",
                                            label = "Visualize Layout",
                                            isCompleted = isOriginCalibrated && uiState.currentLayout.objects.isNotEmpty(),
                                            isActive = isOriginCalibrated
                                        )
                                    }
                                }

                                if (isOriginCalibrated && uiState.currentLayout.objects.isEmpty()) {
                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = Color(0xD90F172A)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AnchorOrange),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = AnchorOrange,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "No objects placed. Go back to the 2D planner to add assets.",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp,
                                                    color = Color.White
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                        // Bottom Section: Selected Object Card + Action Buttons + Disclaimer
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 4.6 Compact Selected Object Information Card (when an AR object is tapped)
                            uiState.selectedObject?.let { selectedObj ->
                                val distMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObj)

                                Card(
                                    shape = RoundedCornerShape(10.dp),
                                    colors = CardDefaults.cardColors(
                                        containerColor = Color(0xEE0F172A)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .border(1.5.dp, AnchorOrange, RoundedCornerShape(10.dp))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        // Header: Name, Type, and Close Action
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(24.dp)
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(AnchorOrange.copy(alpha = 0.2f)),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Icon(
                                                        imageVector = selectedObj.assetType.toIcon(),
                                                        contentDescription = null,
                                                        tint = AnchorOrange,
                                                        modifier = Modifier.size(14.dp)
                                                    )
                                                }
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    text = selectedObj.name,
                                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text(
                                                    text = "• ${selectedObj.assetType.displayName.uppercase()}",
                                                    style = TextStyle(
                                                        fontFamily = FontFamily.Monospace,
                                                        fontSize = 8.5.sp,
                                                        color = PrecisionBlue
                                                    )
                                                )
                                            }

                                            IconButton(
                                                onClick = { viewModel.deselectObject() },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Deselect",
                                                    tint = Color(0xFF94A3B8),
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(6.dp))

                                        // Coordinates & Distance Row
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "POS: X: ${String.format(Locale.US, "%.1f", selectedObj.x)}m  Z: ${String.format(Locale.US, "%.1f", selectedObj.z)}m",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                            )
                                            Text(
                                                text = "ROT: ${selectedObj.rotationDegrees.toInt()}°",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 9.5.sp,
                                                    color = Color(0xFFCBD5E1)
                                                )
                                            )
                                        }

                                        Spacer(modifier = Modifier.height(2.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "DISTANCE FROM ORIGIN: ${String.format(Locale.US, "%.1f", distMeters)} m",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 10.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = AnchorOrange
                                                )
                                            )
                                            Text(
                                                text = "DIM: ${selectedObj.widthMeters}×${selectedObj.lengthMeters}m",
                                                style = TextStyle(
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 8.5.sp,
                                                    color = Color(0xFF94A3B8)
                                                )
                                            )
                                        }
                                    }
                                }
                            }

                            // 4.7 Action Buttons Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (!isOriginCalibrated) {
                                    // Set Planning Origin Action
                                    Button(
                                        onClick = { arRenderer.setOriginAtCenterHit() },
                                        enabled = canSetOrigin,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = PrecisionBlue,
                                            contentColor = Color.White,
                                            disabledContainerColor = Color(0x660F172A),
                                            disabledContentColor = Color(0x6694A3B8)
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = if (canSetOrigin) "Set Planning Origin" else "Scanning ground...",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                            )
                                        }
                                    }
                                } else {
                                    // Capture Visualization Action
                                    Button(
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
                                                        showPreviewDialog = true
                                                    }.onFailure { err ->
                                                        captureFeedbackMessage = "Capture failed: ${err.localizedMessage ?: "Unknown error"}"
                                                    }
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TacticalGreen,
                                            contentColor = Color.Black
                                        ),
                                        modifier = Modifier
                                            .weight(1.1f)
                                            .height(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CameraAlt,
                                            contentDescription = "Capture AR View",
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Capture", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                                    }

                                    // Reset Origin Action
                                    OutlinedButton(
                                        onClick = {
                                            arRenderer.resetOrigin()
                                            viewModel.deselectObject()
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = Color(0xCC0F172A),
                                            contentColor = Color.White
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, AnchorOrange),
                                        modifier = Modifier
                                            .weight(0.85f)
                                            .height(48.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Reset Origin",
                                            tint = AnchorOrange,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Reset", color = AnchorOrange, style = MaterialTheme.typography.labelMedium)
                                    }
                                }

                                // Back to Planner Action
                                OutlinedButton(
                                    onClick = {
                                        arRenderer.resetOrigin()
                                        viewModel.deselectObject()
                                        try {
                                            arSession?.pause()
                                            arSession?.close()
                                        } catch (_: Exception) {}
                                        arSession = null
                                        onNavigateBack()
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        containerColor = Color(0xCC0F172A),
                                        contentColor = Color.White
                                    ),
                                    modifier = Modifier
                                        .weight(0.65f)
                                        .height(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Back", style = MaterialTheme.typography.labelMedium)
                                }
                            }

                            // 4.8 Safety & Accuracy Disclaimer
                            Text(
                                text = "AR placement is an approximate visualization and depends on device tracking and surface detection.",
                                style = TextStyle(
                                    fontFamily = FontFamily.Default,
                                    fontSize = 9.5.sp,
                                    color = Color(0x99FFFFFF),
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )
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
                        .background(Color(0xF00F172A))
                        .border(1.dp, TacticalGreen, RoundedCornerShape(8.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = TacticalGreen,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = msg,
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        )
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
                            .background(Color(0xE60F172A))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(0.95f)
                                .padding(vertical = 16.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E293B)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PrecisionBlue.copy(alpha = 0.5f))
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
                                            color = Color.White
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
                                            tint = Color(0xFF94A3B8)
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
                                        .border(1.dp, Color(0x3394A3B8), RoundedCornerShape(8.dp))
                                        .background(Color.Black),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Image(
                                        bitmap = capturedBitmap!!.asImageBitmap(),
                                        contentDescription = "Captured AR View",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = ContentScale.Fit
                                    )
                                }

                                Spacer(modifier = Modifier.height(16.dp))

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
                                            containerColor = Color(0x33334155),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Retake", style = MaterialTheme.typography.labelMedium)
                                    }

                                    Button(
                                        onClick = {
                                            isSavingImage = true
                                            val metadata = CaptureMetadata(
                                                layoutName = uiState.currentLayout.name,
                                                objectCount = uiState.currentLayout.objects.size,
                                                isCalibrated = isOriginCalibrated
                                            )
                                            val saveResult = ARCaptureManager.saveImageToGallery(
                                                context = context,
                                                bitmap = capturedBitmap!!,
                                                metadata = metadata
                                            )
                                            isSavingImage = false
                                            saveResult.onSuccess {
                                                captureFeedbackMessage = "Visualization saved to Pictures/SurakshaAR"
                                                showPreviewDialog = false
                                                capturedBitmap = null
                                            }.onFailure { err ->
                                                captureFeedbackMessage = "Failed to save: ${err.localizedMessage ?: "Storage error"}"
                                            }
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(48.dp),
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = TacticalGreen,
                                            contentColor = Color.Black
                                        ),
                                        enabled = !isSavingImage
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
                .background(
                    when {
                        isCompleted -> TacticalGreen
                        isActive -> PrecisionBlue
                        else -> Color(0xFF334155)
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            if (isCompleted) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(12.dp)
                )
            } else {
                Text(
                    text = stepNumber,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isActive) Color.White else Color(0xFF94A3B8)
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
                    isCompleted -> TacticalGreen
                    isActive -> Color.White
                    else -> Color(0xFF94A3B8)
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
                        .background(PrecisionBlue.copy(alpha = 0.15f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = PrecisionBlue,
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
                    tint = Color(0xFFF59E0B),
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
                    tint = PrecisionBlue,
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
