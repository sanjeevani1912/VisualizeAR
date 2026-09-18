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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dhruvar.ui.ar.render.ARRenderer
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.PrimaryButton
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.TacticalGreen
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.CameraNotAvailableException
import com.google.ar.core.exceptions.UnavailableApkTooOldException
import com.google.ar.core.exceptions.UnavailableArcoreNotInstalledException
import com.google.ar.core.exceptions.UnavailableDeviceNotCompatibleException
import com.google.ar.core.exceptions.UnavailableSdkTooOldException

/**
 * Screen providing native Android ARCore camera streaming, lifecycle management,
 * horizontal plane detection, and plane visualization with a tactical HUD.
 */
@Composable
fun ARVisualizationScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

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
            onStatusChanged = { status -> trackingStatus = status }
        )
    }

    // Session initialization helper
    fun initOrResumeSession(surfaceView: GLSurfaceView) {
        if (!hasCameraPermission || arCoreStatus != "SUPPORTED") return

        try {
            if (arSession == null) {
                val session = Session(context)
                val config = Config(session).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                }
                session.configure(config)
                arSession = session
                arRenderer.session = session
            }

            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
            @Suppress("DEPRECATION")
            val rotation = windowManager?.defaultDisplay?.rotation ?: Display.DEFAULT_DISPLAY
            arRenderer.displayRotation = rotation

            arSession?.resume()
            surfaceView.onResume()
        } catch (e: Exception) {
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

    // Lifecycle synchronization
    DisposableEffect(lifecycleOwner, glSurfaceViewRef, hasCameraPermission, arCoreStatus) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    glSurfaceViewRef?.let { view ->
                        initOrResumeSession(view)
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    glSurfaceViewRef?.onPause()
                    try {
                        arSession?.pause()
                    } catch (_: Exception) {}
                }
                Lifecycle.Event.ON_DESTROY -> {
                    try {
                        arSession?.pause()
                        arSession?.close()
                    } catch (_: Exception) {}
                    arSession = null
                    arRenderer.session = null
                }
                else -> Unit
            }
        }

        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            try {
                arSession?.pause()
                arSession?.close()
            } catch (_: Exception) {}
            arSession = null
            arRenderer.session = null
        }
    }

    // Reset tracking action
    fun resetTracking() {
        try {
            arSession?.let { ses ->
                ses.pause()
                val config = Config(ses).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    focusMode = Config.FocusMode.AUTO
                }
                ses.configure(config)
                ses.resume()
                detectedPlaneCount = 0
                trackingStatus = "Scanning environment..."
            }
        } catch (e: Exception) {
            trackingStatus = "Tracking reset failed: ${e.message}"
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "AR VISUALIZATION",
                onNavigateBack = {
                    try {
                        arSession?.pause()
                        arSession?.close()
                    } catch (_: Exception) {}
                    arSession = null
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
                // 1. Camera Permission Required
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

                // 2. ARCore Not Supported on Device
                arCoreStatus == "UNSUPPORTED" -> {
                    UnsupportedDeviceView(onNavigateBack = onNavigateBack)
                }

                // 3. ARCore Needs Install / Update
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

                // 4. Active AR Camera & Plane Tracking Feed
                else -> {
                    // OpenGL Surface View for AR rendering
                    AndroidView(
                        factory = { ctx ->
                            GLSurfaceView(ctx).apply {
                                preserveEGLContextOnPause = true
                                setEGLContextClientVersion(2)
                                setRenderer(arRenderer)
                                renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                                glSurfaceViewRef = this
                                initOrResumeSession(this)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Overlay HUD Controls & Status
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Top HUD Bar: Status & Plane Counter
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
                                    .border(1.dp, PrecisionBlue.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (detectedPlaneCount > 0) TacticalGreen else Color(0xFFF59E0B)
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = trackingStatus,
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    )
                                }
                            }

                            // Detected Planes Counter Pill
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xCC0F172A))
                                    .border(
                                        1.dp,
                                        if (detectedPlaneCount > 0) TacticalGreen else Color(0x6694A3B8),
                                        RoundedCornerShape(8.dp)
                                    )
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (detectedPlaneCount > 0) Icons.Default.CheckCircle else Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = if (detectedPlaneCount > 0) TacticalGreen else Color(0xFF94A3B8),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "$detectedPlaneCount PLANES",
                                        style = TextStyle(
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (detectedPlaneCount > 0) TacticalGreen else Color(0xFF94A3B8)
                                        )
                                    )
                                }
                            }
                        }

                        // Bottom Action Controls
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedButton(
                                onClick = { resetTracking() },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color(0xCC0F172A),
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reset tracking",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset Tracking", style = MaterialTheme.typography.labelMedium)
                            }

                            Button(
                                onClick = {
                                    try {
                                        arSession?.pause()
                                        arSession?.close()
                                    } catch (_: Exception) {}
                                    arSession = null
                                    onNavigateBack()
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = PrecisionBlue,
                                    contentColor = Color.White
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Back to Planner", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
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
