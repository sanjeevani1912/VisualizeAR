package com.example.dhruvar.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.FloatingAssetPickerOverlay
import com.example.dhruvar.ui.components.PlanningCanvas
import com.example.dhruvar.ui.components.PrimaryButton
import com.example.dhruvar.ui.components.SelectedObjectEditorOverlay
import com.example.dhruvar.ui.theme.TacticalAmber
import com.example.dhruvar.ui.theme.TacticalGreen
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

    Scaffold(
        topBar = {
            AppTopBar(
                title = "DhruvAR",
                onNavigateBack = onNavigateBack,
                actions = {
                    // Quick Save Button
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
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Workspace Header Bar with active plan metadata and persistence status
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ACTIVE PLAN: ",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = uiState.currentLayout.name,
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))

                // Persistence Status Badge
                when {
                    uiState.hasUnsavedChanges -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TacticalAmber.copy(alpha = 0.15f))
                                .border(1.dp, TacticalAmber, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "UNSAVED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = TacticalAmber
                                )
                            )
                        }
                    }
                    uiState.isPersisted -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(TacticalGreen.copy(alpha = 0.15f))
                                .border(1.dp, TacticalGreen, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "SAVED",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = TacticalGreen
                                )
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "DRAFT",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.secondary
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = "${uiState.currentLayout.objects.size} Placed Assets",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // 1. 2D Top-Down Planning Canvas Area (takes majority of screen)
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                val density = LocalDensity.current
                val viewportWidthPx = with(density) { maxWidth.toPx() }
                val viewportHeightPx = with(density) { maxHeight.toPx() }

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
                        // Placement is handled by the floating asset menu (+)
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
                    onToggleShowDistances = { viewModel.toggleShowDistances() }
                )

                FloatingAssetPickerOverlay(
                    isOpen = isAssetMenuOpen,
                    onToggle = { isAssetMenuOpen = !isAssetMenuOpen },
                    onDismiss = { isAssetMenuOpen = false },
                    onAssetChosen = { assetType ->
                        val transformer = CoordinateTransformer(
                            viewportWidth = viewportWidthPx,
                            viewportHeight = viewportHeightPx,
                            panOffset = uiState.panOffset,
                            zoom = uiState.zoom,
                            basePixelsPerMeter = uiState.basePixelsPerMeter
                        )
                        // Place at the currently visible canvas center (preserves pan/zoom)
                        val (worldX, worldZ) = transformer.canvasToWorld(
                            Offset(viewportWidthPx / 2f, viewportHeightPx / 2f)
                        )
                        val snappedX = (worldX * 2f).roundToInt() / 2f
                        val snappedZ = (worldZ * 2f).roundToInt() / 2f
                        viewModel.addAssetOfType(assetType, snappedX, snappedZ)
                        isAssetMenuOpen = false
                        isPropertyEditorMinimized = false
                    }
                )

                // Translucent property editor — canvas stays visible behind/around it
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
                    }
                )
            }

            // Primary Visualize in AR Action Button
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                PrimaryButton(
                    text = "Visualize in AR",
                    icon = Icons.Default.ViewInAr,
                    onClick = {
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
                    }
                )
            }
        }
    }
}
