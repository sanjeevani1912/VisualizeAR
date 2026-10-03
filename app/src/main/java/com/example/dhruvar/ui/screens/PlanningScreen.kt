package com.example.dhruvar.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.FloatingAssetPickerOverlay
import com.example.dhruvar.ui.components.GeoOriginChip
import com.example.dhruvar.ui.components.GeoOriginSheet
import com.example.dhruvar.ui.components.PlanningCanvas
import com.example.dhruvar.ui.components.PlanningModeContext
import com.example.dhruvar.ui.components.PlanningModeRail
import com.example.dhruvar.ui.components.PlanningSearchButton
import com.example.dhruvar.ui.components.PlanningSurfaceSwitcher
import com.example.dhruvar.ui.components.planningHudToolsFor
import com.example.dhruvar.ui.components.SelectedObjectEditorOverlay
import com.example.dhruvar.ui.components.rememberCompassState
import com.example.dhruvar.ui.map.MapViewportBoundsBridge
import com.example.dhruvar.ui.map.PlanningMapView
import com.example.dhruvar.ui.theme.Rust
import com.example.dhruvar.viewmodel.PlanningViewModel
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun PlanningScreen(
    onNavigateBack: () -> Unit,
    onNavigateToVisualization: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PlanningViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showMenu by remember { mutableStateOf(false) }
    var isAssetMenuOpen by remember { mutableStateOf(false) }
    var isPropertyEditorMinimized by remember { mutableStateOf(false) }
    val compassState by rememberCompassState()
    val context = LocalContext.current
    var locationPermissionPermanentlyDenied by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val granted = results[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            results[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onLocationPermissionResult(granted)
        if (!granted) {
            val activity = context as? Activity
            val showRationale = activity != null && (
                ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) || ActivityCompat.shouldShowRequestPermissionRationale(
                    activity,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            locationPermissionPermanentlyDenied = !showRationale
        } else {
            locationPermissionPermanentlyDenied = false
        }
    }

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    fun requestLocationPermission() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    fun openAppLocationSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

    // Sync permission status when the geo panel opens (e.g. already granted at OS level)
    LaunchedEffect(uiState.isGeoOriginPanelExpanded) {
        if (uiState.isGeoOriginPanelExpanded) {
            viewModel.onLocationPermissionResult(hasLocationPermission())
        }
    }

    // Battery: stop GNSS when leaving Planning
    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopLocationUpdates()
        }
    }

    // Save/Rename Dialog state
    var showSaveDialog by remember { mutableStateOf(false) }
    var showSaveBeforeVisualizeDialog by remember { mutableStateOf(false) }
    var layoutNameInput by remember { mutableStateOf("") }

    // Reset minimized flag when selection changes / clears
    LaunchedEffect(uiState.selectedObjectId) {
        if (uiState.selectedObjectId == null) {
            isPropertyEditorMinimized = false
        }
    }

    // Handle user feedback messages (save confirmation, error, etc.)
    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserFeedback()
        }
    }

    // Save Dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            title = {
                Text(
                    text = if (uiState.isPersisted) "Rename / Save Plan" else "Save Planning Layout",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a tactical designation for this deployment layout:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = layoutNameInput,
                        onValueChange = { layoutNameInput = it },
                        label = { Text("Layout Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = layoutNameInput.trim().ifEmpty { uiState.currentLayout.name }
                        viewModel.saveCurrentLayout(finalName)
                        showSaveDialog = false
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Save Before Visualize Dialog (prevents silent discard of changes)
    if (showSaveBeforeVisualizeDialog) {
        AlertDialog(
            onDismissRequest = { showSaveBeforeVisualizeDialog = false },
            title = {
                Text(
                    text = "Save Before AR Visualization",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "This layout has unsaved tactical modifications. Would you like to save before opening AR visualization?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val finalName = uiState.currentLayout.name.ifBlank { "Tactical Plan" }
                        viewModel.saveCurrentLayout(finalName)
                        showSaveBeforeVisualizeDialog = false
                        onNavigateToVisualization()
                    }
                ) {
                    Text("Save & Visualize", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveBeforeVisualizeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val planSubtitle = buildString {
        append(uiState.currentLayout.name)
        append(" · ")
        append(
            when {
                uiState.hasUnsavedChanges -> "Unsaved"
                uiState.isPersisted -> "Saved"
                else -> "Draft"
            }
        )
        append(" · ")
        append(uiState.currentLayout.objects.size)
        append(if (uiState.currentLayout.objects.size == 1) " asset" else " assets")
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "Planning",
                subtitle = planSubtitle,
                onNavigateBack = onNavigateBack,
                actions = {
                    // AR is an infrequent, deliberate hand-off, so it sits in the top bar and
                    // leaves the frequently used mode rail sole occupant of the bottom edge.
                    IconButton(onClick = {
                        if (uiState.hasUnsavedChanges) {
                            if (uiState.isPersisted) {
                                viewModel.saveCurrentLayout()
                                scope.launch {
                                    snackbarHostState.showSnackbar("Layout '${uiState.currentLayout.name}' saved automatically")
                                }
                                onNavigateToVisualization()
                            } else {
                                showSaveBeforeVisualizeDialog = true
                            }
                        } else {
                            onNavigateToVisualization()
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.ViewInAr,
                            contentDescription = "Visualize in AR"
                        )
                    }

                    IconButton(onClick = {
                        if (!uiState.isPersisted) {
                            layoutNameInput = uiState.currentLayout.name
                            showSaveDialog = true
                        } else {
                            if (uiState.hasUnsavedChanges) {
                                viewModel.saveCurrentLayout()
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Layout '${uiState.currentLayout.name}' is already up to date")
                                }
                            }
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = "Save layout"
                        )
                    }

                    Box {
                        IconButton(onClick = { showMenu = !showMenu }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Menu options"
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (uiState.isPersisted) "Save As / Rename..." else "Save Layout...") },
                                onClick = {
                                    layoutNameInput = uiState.currentLayout.name
                                    showSaveDialog = true
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Reset View") },
                                onClick = {
                                    viewModel.resetView()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Refresh, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (uiState.showDistances) "Hide Distances" else "Show Distances") },
                                onClick = {
                                    viewModel.toggleShowDistances()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Straighten, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Clear Canvas") },
                                onClick = {
                                    viewModel.clearLayout()
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            val density = LocalDensity.current
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }

            val hudTools = planningHudToolsFor(
                surface = uiState.planningSurface,
                mode = uiState.mapInteractionMode,
                hasSelection = uiState.selectedObjectId != null,
                isEditorOpen = uiState.selectedObject != null && !isPropertyEditorMinimized,
                isOriginExpanded = uiState.isGeoOriginPanelExpanded
            )
            // The map renders a mandatory attribution line along its bottom edge; bottom-anchored
            // HUD sits above it. The grid has no basemap and so no attribution to clear.
            val bottomHudInset =
                if (uiState.planningSurface == PlanningSurface.MAP) 42.dp else 12.dp
            // Stop the contextual strip short of the asset FAB column at BottomEnd.
            val modeContextWidth = maxWidth - 84.dp

            if (uiState.planningSurface == PlanningSurface.MAP) {
                PlanningMapView(
                    layout = uiState.currentLayout,
                    selectedObjectId = uiState.selectedObjectId,
                    interactionMode = uiState.mapInteractionMode,
                    onMapTap = { lat, lng ->
                        when (uiState.mapInteractionMode) {
                            MapInteractionMode.NAVIGATE -> Unit
                            MapInteractionMode.SELECT_REGION -> Unit
                            MapInteractionMode.SET_ANCHOR -> {
                                viewModel.setGeographicAnchorFromMap(
                                    latitude = lat,
                                    longitude = lng,
                                    headingDegrees = compassState.magneticHeadingDegrees
                                )
                            }
                            MapInteractionMode.PLACE_ASSET -> {
                                viewModel.placeAssetAtGeographic(lat, lng)
                            }
                            MapInteractionMode.EDIT -> {
                                viewModel.handleMapEditTap(lat, lng)
                            }
                        }
                    },
                    onStyleError = { viewModel.setMapStyleError(it) },
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                PlanningCanvas(
                    layout = uiState.currentLayout,
                    panOffset = uiState.panOffset,
                    zoom = uiState.zoom,
                    basePixelsPerMeter = uiState.basePixelsPerMeter,
                    cursorCoordinatesMeters = uiState.cursorCoordinatesMeters,
                    onPan = { delta -> viewModel.onPan(delta) },
                    onZoom = { factor, centroid -> viewModel.onZoom(factor, centroid) },
                    onZoomIn = { viewModel.zoomIn() },
                    onZoomOut = { viewModel.zoomOut() },
                    onResetView = { viewModel.resetView() },
                    onCursorMoved = { x, z -> viewModel.updateCursorCoordinates(x, z) },
                    onCursorCleared = { viewModel.clearCursorCoordinates() },
                    onCanvasTapped = { _, _ ->
                        if (uiState.selectedObjectId != null) {
                            viewModel.deselectObject()
                        }
                    },
                    onObjectSelected = { objectId ->
                        viewModel.selectObject(objectId)
                    },
                    onMoveObject = { id, x, z ->
                        viewModel.moveObject(id, x, z)
                    },
                    onDeselect = {
                        viewModel.deselectObject()
                    },
                    selectedObjectId = uiState.selectedObjectId,
                    showDistances = uiState.showDistances,
                    onToggleShowDistances = { viewModel.toggleShowDistances() },
                    topInset = 52.dp
                )
            }

            // Origin expands downward over the map. The scrim keeps it transient so it never
            // becomes a second permanent panel stacked against the mode rail.
            if (uiState.isGeoOriginPanelExpanded) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = { viewModel.setGeoOriginPanelExpanded(false) }
                        )
                        .semantics { contentDescription = "Dismiss origin panel" }
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hudTools.showSearch) {
                        PlanningSearchButton(
                            onClick = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Place search arrives in the next update")
                                }
                            }
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    GeoOriginChip(
                        expanded = uiState.isGeoOriginPanelExpanded,
                        onExpandedChange = { viewModel.setGeoOriginPanelExpanded(it) },
                        anchor = uiState.currentLayout.anchor,
                        locationState = uiState.locationState,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    PlanningSurfaceSwitcher(
                        surface = uiState.planningSurface,
                        onSurfaceChange = { viewModel.setPlanningSurface(it) }
                    )
                }

                if (uiState.isGeoOriginPanelExpanded) {
                    Spacer(modifier = Modifier.height(8.dp))
                    GeoOriginSheet(
                        anchor = uiState.currentLayout.anchor,
                        locationState = uiState.locationState,
                        liveHeadingDegrees = compassState.magneticHeadingDegrees,
                        compassAvailable = compassState.isAvailable,
                        geoAccuracyWarning = uiState.geoAccuracyWarning,
                        onRequestPermission = { requestLocationPermission() },
                        onOpenAppSettings = { openAppLocationSettings() },
                        permissionPermanentlyDenied = locationPermissionPermanentlyDenied,
                        onRefreshLocation = {
                            if (hasLocationPermission()) {
                                viewModel.refreshLocation()
                            } else {
                                requestLocationPermission()
                            }
                        },
                        onUseMyLocation = {
                            if (!hasLocationPermission()) {
                                requestLocationPermission()
                                return@GeoOriginSheet
                            }
                            viewModel.setGeographicAnchorFromDeviceFix(compassState.magneticHeadingDegrees)
                        },
                        onCaptureHeading = {
                            viewModel.updateGeographicHeading(compassState.magneticHeadingDegrees)
                        },
                        onClear = { viewModel.clearGeographicAnchor() },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                uiState.mapStyleError?.let { err ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Map tiles unavailable: $err",
                            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                            color = Rust,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, bottom = bottomHudInset),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (hudTools.hasModeContext) {
                    PlanningModeContext(
                        tools = hudTools,
                        hasRegion = uiState.currentLayout.regionBounds != null,
                        selectedAssetName = uiState.selectedAssetType.displayName,
                        onConfirmViewportRegion = {
                            val bounds = MapViewportBoundsBridge.queryVisibleBounds()
                            if (bounds == null) {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Map not ready — wait for tiles to load")
                                }
                            } else {
                                viewModel.setRegionBoundsFromViewport(
                                    northLatitude = bounds.north,
                                    southLatitude = bounds.south,
                                    eastLongitude = bounds.east,
                                    westLongitude = bounds.west
                                )
                            }
                        },
                        onClearRegion = { viewModel.clearRegionBounds() },
                        modifier = Modifier.width(modeContextWidth)
                    )
                }

                if (hudTools.showModeRail) {
                    PlanningModeRail(
                        mode = uiState.mapInteractionMode,
                        onModeChange = { viewModel.setMapInteractionMode(it) }
                    )
                }
            }

            FloatingAssetPickerOverlay(
                isOpen = isAssetMenuOpen,
                onToggle = { isAssetMenuOpen = !isAssetMenuOpen },
                onDismiss = { isAssetMenuOpen = false },
                modifier = Modifier.padding(bottom = bottomHudInset - 12.dp),
                onAssetChosen = { assetType ->
                    viewModel.selectAssetType(assetType)
                    if (uiState.planningSurface == PlanningSurface.METRIC_GRID) {
                        val transformer = CoordinateTransformer(
                            viewportWidth = viewportWidthPx,
                            viewportHeight = viewportHeightPx,
                            panOffset = uiState.panOffset,
                            zoom = uiState.zoom,
                            basePixelsPerMeter = uiState.basePixelsPerMeter
                        )
                        val (worldX, worldZ) = transformer.canvasToWorld(
                            Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                        )
                        val snappedX = (worldX * 2f).roundToInt() / 2f
                        val snappedZ = (worldZ * 2f).roundToInt() / 2f
                        viewModel.addAssetOfType(assetType, snappedX, snappedZ)
                    } else {
                        viewModel.setMapInteractionMode(MapInteractionMode.PLACE_ASSET)
                        scope.launch {
                            snackbarHostState.showSnackbar(
                                "Place mode: tap the map to drop ${assetType.displayName}"
                            )
                        }
                    }
                    isAssetMenuOpen = false
                    isPropertyEditorMinimized = false
                }
            )

            SelectedObjectEditorOverlay(
                selectedObject = uiState.selectedObject,
                isMinimized = isPropertyEditorMinimized,
                onMinimizedChange = { isPropertyEditorMinimized = it },
                viewportWidthPx = viewportWidthPx,
                viewportHeightPx = viewportHeightPx,
                panOffset = uiState.panOffset,
                zoom = uiState.zoom,
                basePixelsPerMeter = uiState.basePixelsPerMeter,
                onDelete = { viewModel.deleteSelectedObject() },
                onDeselect = { viewModel.deselectObject() },
                onDuplicate = { viewModel.duplicateSelectedObject() },
                onRotate = { delta -> viewModel.rotateSelectedObject(delta) },
                onSetRotation = { degrees -> viewModel.setSelectedObjectRotation(degrees) },
                onUpdatePosition = { x, z -> viewModel.updateSelectedObjectPosition(x, z) },
                onUpdateDimensions = { w, l, h ->
                    viewModel.updateSelectedObjectDimensions(w, l, h)
                },
                bottomInset = bottomHudInset
            )
        }
    }
}
