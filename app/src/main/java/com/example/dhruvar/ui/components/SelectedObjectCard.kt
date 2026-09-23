package com.example.dhruvar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.theme.AnchorOrange
import com.example.dhruvar.ui.theme.PrecisionBlue
import java.util.Locale

/**
 * Floating translucent property editor overlaid on the planning canvas.
 * Only consumes touches within its own bounds; canvas remains interactive outside.
 */
@Composable
fun BoxScope.SelectedObjectEditorOverlay(
    selectedObject: LayoutObject?,
    isMinimized: Boolean,
    onMinimizedChange: (Boolean) -> Unit,
    viewportWidthPx: Float,
    viewportHeightPx: Float,
    panOffset: Offset,
    zoom: Float,
    basePixelsPerMeter: Float,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    onDuplicate: () -> Unit,
    onRotate: (deltaDegrees: Float) -> Unit,
    onSetRotation: (degrees: Float) -> Unit,
    onUpdatePosition: (x: Float, z: Float) -> Unit,
    onUpdateDimensions: (width: Float, length: Float, height: Float) -> Unit
) {
    if (selectedObject == null) return

    val transformer = remember(viewportWidthPx, viewportHeightPx, panOffset, zoom, basePixelsPerMeter) {
        CoordinateTransformer(
            viewportWidth = viewportWidthPx,
            viewportHeight = viewportHeightPx,
            panOffset = panOffset,
            zoom = zoom,
            basePixelsPerMeter = basePixelsPerMeter
        )
    }
    val objectScreen = transformer.worldToCanvas(selectedObject.x, selectedObject.z)
    // Prefer the side opposite the selected object so context stays visible
    val alignEnd = objectScreen.x < viewportWidthPx * 0.55f
    val alignBottom = objectScreen.y < viewportHeightPx * 0.55f
    val panelAlignment = when {
        alignBottom && alignEnd -> Alignment.BottomEnd
        alignBottom && !alignEnd -> Alignment.BottomStart
        !alignBottom && alignEnd -> Alignment.TopEnd
        else -> Alignment.TopStart
    }
    // Leave room for the live compass widget at TopEnd
    val panelPadding = when (panelAlignment) {
        Alignment.TopEnd -> Modifier.padding(top = 108.dp, end = 12.dp, start = 12.dp, bottom = 12.dp)
        Alignment.TopStart -> Modifier.padding(top = 12.dp, end = 12.dp, start = 12.dp, bottom = 12.dp)
        else -> Modifier.padding(12.dp)
    }

    AnimatedVisibility(
        visible = isMinimized,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(panelAlignment)
            .then(panelPadding)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                PrecisionBlue.copy(alpha = 0.55f)
            ),
            modifier = Modifier
                .clickable { onMinimizedChange(false) }
                .semantics { contentDescription = "Open properties for ${selectedObject.name}" }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = null,
                    tint = PrecisionBlue,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = selectedObject.name,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }

    AnimatedVisibility(
        visible = !isMinimized,
        enter = fadeIn() + slideInVertically { it / 8 },
        exit = fadeOut() + slideOutVertically { it / 10 },
        modifier = Modifier
            .align(panelAlignment)
            .then(panelPadding)
    ) {
        SelectedObjectCard(
            selectedObject = selectedObject,
            onDelete = onDelete,
            onMinimize = { onMinimizedChange(true) },
            onDeselect = onDeselect,
            onDuplicate = onDuplicate,
            onRotate = onRotate,
            onSetRotation = onSetRotation,
            onUpdatePosition = onUpdatePosition,
            onUpdateDimensions = onUpdateDimensions,
            modifier = Modifier
                .widthIn(min = 240.dp, max = 300.dp)
                .heightIn(max = 420.dp)
        )
    }
}

/**
 * Translucent Material 3 property panel for the currently selected layout object.
 * Edits commit into the same ViewModel state used by canvas rendering and persistence.
 */
@Composable
fun SelectedObjectCard(
    selectedObject: LayoutObject,
    onDelete: () -> Unit,
    onDeselect: () -> Unit,
    onDuplicate: () -> Unit,
    onRotate: (deltaDegrees: Float) -> Unit,
    onSetRotation: (degrees: Float) -> Unit,
    onUpdatePosition: (x: Float, z: Float) -> Unit,
    modifier: Modifier = Modifier,
    onMinimize: () -> Unit = onDeselect,
    onUpdateDimensions: (width: Float, length: Float, height: Float) -> Unit = { _, _, _ -> }
) {
    val focusManager = LocalFocusManager.current

    var xInput by remember(selectedObject.id) {
        mutableStateOf(String.format(Locale.US, "%.1f", selectedObject.x))
    }
    var zInput by remember(selectedObject.id) {
        mutableStateOf(String.format(Locale.US, "%.1f", selectedObject.z))
    }
    var widthInput by remember(selectedObject.id) {
        mutableStateOf(String.format(Locale.US, "%.1f", selectedObject.widthMeters))
    }
    var lengthInput by remember(selectedObject.id) {
        mutableStateOf(String.format(Locale.US, "%.1f", selectedObject.lengthMeters))
    }
    var heightInput by remember(selectedObject.id) {
        mutableStateOf(String.format(Locale.US, "%.1f", selectedObject.heightMeters))
    }
    var rotInput by remember(selectedObject.id) {
        mutableStateOf("${selectedObject.rotationDegrees.toInt()}")
    }

    LaunchedEffect(selectedObject.x) {
        if (xInput.toFloatOrNull() != selectedObject.x) {
            xInput = String.format(Locale.US, "%.1f", selectedObject.x)
        }
    }
    LaunchedEffect(selectedObject.z) {
        if (zInput.toFloatOrNull() != selectedObject.z) {
            zInput = String.format(Locale.US, "%.1f", selectedObject.z)
        }
    }
    LaunchedEffect(selectedObject.widthMeters) {
        if (widthInput.toFloatOrNull() != selectedObject.widthMeters) {
            widthInput = String.format(Locale.US, "%.1f", selectedObject.widthMeters)
        }
    }
    LaunchedEffect(selectedObject.lengthMeters) {
        if (lengthInput.toFloatOrNull() != selectedObject.lengthMeters) {
            lengthInput = String.format(Locale.US, "%.1f", selectedObject.lengthMeters)
        }
    }
    LaunchedEffect(selectedObject.heightMeters) {
        if (heightInput.toFloatOrNull() != selectedObject.heightMeters) {
            heightInput = String.format(Locale.US, "%.1f", selectedObject.heightMeters)
        }
    }
    LaunchedEffect(selectedObject.rotationDegrees) {
        if (rotInput.toFloatOrNull() != selectedObject.rotationDegrees) {
            rotInput = "${selectedObject.rotationDegrees.toInt()}"
        }
    }

    fun commitPosition() {
        val parsedX = xInput.toFloatOrNull() ?: selectedObject.x
        val parsedZ = zInput.toFloatOrNull() ?: selectedObject.z
        onUpdatePosition(parsedX, parsedZ)
        xInput = String.format(Locale.US, "%.1f", parsedX)
        zInput = String.format(Locale.US, "%.1f", parsedZ)
        focusManager.clearFocus()
    }

    fun commitDimensions() {
        val parsedW = widthInput.toFloatOrNull()
        val parsedL = lengthInput.toFloatOrNull()
        val parsedH = heightInput.toFloatOrNull()
        if (parsedW != null && parsedW > 0f &&
            parsedL != null && parsedL > 0f &&
            parsedH != null && parsedH > 0f
        ) {
            onUpdateDimensions(parsedW, parsedL, parsedH)
            widthInput = String.format(Locale.US, "%.1f", parsedW)
            lengthInput = String.format(Locale.US, "%.1f", parsedL)
            heightInput = String.format(Locale.US, "%.1f", parsedH)
        } else {
            widthInput = String.format(Locale.US, "%.1f", selectedObject.widthMeters)
            lengthInput = String.format(Locale.US, "%.1f", selectedObject.lengthMeters)
            heightInput = String.format(Locale.US, "%.1f", selectedObject.heightMeters)
        }
        focusManager.clearFocus()
    }

    fun commitRotation() {
        val parsedRot = rotInput.toFloatOrNull() ?: selectedObject.rotationDegrees
        val normalized = LayoutObject.normalizeDegrees(parsedRot)
        onSetRotation(normalized)
        rotInput = "${normalized.toInt()}"
        focusManager.clearFocus()
    }

    val distanceMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObject)
    val formattedDist = SpatialDistanceCalculator.formatDistance(distanceMeters)

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            PrecisionBlue.copy(alpha = 0.45f)
        ),
        modifier = modifier.semantics { contentDescription = "Properties for ${selectedObject.name}" }
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = 12.dp, vertical = 10.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrecisionBlue.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = selectedObject.assetType.toIcon(),
                            contentDescription = selectedObject.assetType.displayName,
                            tint = PrecisionBlue,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedObject.name,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = selectedObject.assetType.displayName.uppercase(),
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrecisionBlue
                            )
                        )
                    }
                }

                Row {
                    IconButton(
                        onClick = onMinimize,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Minimize properties panel",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            SectionLabel("POSITION")
            PropertyRow {
                CoordinateInputField("X", xInput, "m", { xInput = it }, { commitPosition() }, Modifier.weight(1f))
                CoordinateInputField("Z", zInput, "m", { zInput = it }, { commitPosition() }, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(6.dp))
            SectionLabel("ROTATION")
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                RotationStepButton("−", onClick = { onRotate(-15.0f) }, modifier = Modifier.width(36.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                        .border(1.dp, PrecisionBlue.copy(alpha = 0.55f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicTextField(
                            value = rotInput,
                            onValueChange = {
                                rotInput = it
                                it.toFloatOrNull()?.let { num ->
                                    onSetRotation(LayoutObject.normalizeDegrees(num))
                                }
                            },
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center
                            ),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done
                            ),
                            keyboardActions = KeyboardActions(onDone = { commitRotation() }),
                            cursorBrush = SolidColor(PrecisionBlue),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "°",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrecisionBlue
                            )
                        )
                    }
                }
                RotationStepButton("+", onClick = { onRotate(+15.0f) }, modifier = Modifier.width(36.dp))
                RotationStepButton("90°", onClick = { onRotate(+90.0f) }, modifier = Modifier.width(40.dp))
            }

            Spacer(modifier = Modifier.height(6.dp))
            SectionLabel("DIMENSIONS")
            PropertyRow {
                CoordinateInputField("W", widthInput, "m", { widthInput = it }, { commitDimensions() }, Modifier.weight(1f))
                CoordinateInputField("L", lengthInput, "m", { lengthInput = it }, { commitDimensions() }, Modifier.weight(1f))
                CoordinateInputField("H", heightInput, "m", { heightInput = it }, { commitDimensions() }, Modifier.weight(1f))
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "FROM ANCHOR",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
                Text(
                    text = formattedDist,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = AnchorOrange
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDuplicate,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrecisionBlue.copy(alpha = 0.14f),
                        contentColor = PrecisionBlue
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = "Duplicate", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626).copy(alpha = 0.12f),
                        contentColor = Color(0xFFDC2626)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                ) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                }
            }

            TextButton(
                onClick = onDeselect,
                modifier = Modifier.align(Alignment.End)
            ) {
                Text(
                    text = "Deselect",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall.copy(
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp,
            color = MaterialTheme.colorScheme.secondary
        ),
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun PropertyRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.40f))
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        content = content
    )
}

@Composable
private fun CoordinateInputField(
    label: String,
    value: String,
    unit: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(30.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp)
    ) {
        Text(
            text = "$label:",
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        )
        Spacer(modifier = Modifier.width(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Start
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            cursorBrush = SolidColor(PrecisionBlue),
            modifier = Modifier.weight(1f)
        )
        Text(
            text = unit,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 8.5.sp,
                color = MaterialTheme.colorScheme.secondary
            )
        )
    }
}

@Composable
private fun RotationStepButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(6.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
            contentColor = PrecisionBlue
        ),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = modifier.height(32.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
