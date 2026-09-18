package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.theme.AnchorOrange
import com.example.dhruvar.ui.theme.PrecisionBlue
import java.util.Locale

/**
 * Tactical property inspection panel displayed when an object on the
 * planning canvas is selected.
 *
 * Provides:
 * - Real-time metric position readouts and direct coordinate editing (X, Z)
 * - Physical real-world dimensions and custom dimension editing (W, L, H)
 * - Distance from spatial Anchor datum
 * - Step rotation controls and direct numeric angle input
 * - Object duplication with dimension & rotation retention
 * - Object deletion
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
    onUpdateDimensions: (width: Float, length: Float) -> Unit = { _, _ -> }
) {
    val focusManager = LocalFocusManager.current

    // Local state for direct coordinate editing, synchronized with model
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
    var rotInput by remember(selectedObject.id) {
        mutableStateOf("${selectedObject.rotationDegrees.toInt()}")
    }

    // Keep inputs synced when object is moved, resized, or rotated
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
        if (parsedW != null && parsedW > 0f && parsedL != null && parsedL > 0f) {
            onUpdateDimensions(parsedW, parsedL)
            widthInput = String.format(Locale.US, "%.1f", parsedW)
            lengthInput = String.format(Locale.US, "%.1f", parsedL)
        } else {
            // Revert non-positive or invalid inputs
            widthInput = String.format(Locale.US, "%.1f", selectedObject.widthMeters)
            lengthInput = String.format(Locale.US, "%.1f", selectedObject.lengthMeters)
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

    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
            .fillMaxWidth()
            .border(1.5.dp, PrecisionBlue, RoundedCornerShape(8.dp))
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)) {
            // 1. Header: Asset Icon, Name, and Close Action
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(PrecisionBlue.copy(alpha = 0.14f))
                    ) {
                        Icon(
                            imageVector = selectedObject.assetType.toIcon(),
                            contentDescription = null,
                            tint = PrecisionBlue,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = selectedObject.name,
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "SELECTED ASSET • ${selectedObject.assetType.displayName.uppercase()}",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PrecisionBlue
                            )
                        )
                    }
                }

                IconButton(
                    onClick = onDeselect,
                    modifier = Modifier.size(26.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Deselect",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(5.dp))

            // 2. Direct Position Coordinates Row (X and Z in meters)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "POS:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )

                // Editable X Input
                CoordinateInputField(
                    label = "X",
                    value = xInput,
                    unit = "m",
                    onValueChange = { xInput = it },
                    onDone = { commitPosition() },
                    modifier = Modifier.weight(1f)
                )

                // Editable Z Input
                CoordinateInputField(
                    label = "Z",
                    value = zInput,
                    unit = "m",
                    onValueChange = { zInput = it },
                    onDone = { commitPosition() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 3. Physical Footprint Dimensions Row (Width & Length in meters)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "DIM:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )

                // Editable Width Input
                CoordinateInputField(
                    label = "W",
                    value = widthInput,
                    unit = "m",
                    onValueChange = { widthInput = it },
                    onDone = { commitDimensions() },
                    modifier = Modifier.weight(1f)
                )

                // Editable Length Input
                CoordinateInputField(
                    label = "L",
                    value = lengthInput,
                    unit = "m",
                    onValueChange = { lengthInput = it },
                    onDone = { commitDimensions() },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 4. Distance From Anchor & Height Clearance Readout
            val distanceMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObject)
            val formattedDist = SpatialDistanceCalculator.formatDistance(distanceMeters)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "DISTANCE FROM ANCHOR: ",
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    )
                    Text(
                        text = formattedDist,
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = AnchorOrange
                        )
                    )
                }

                Text(
                    text = "H: ${selectedObject.heightMeters}m",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // 5. Rotation Steppers & Direct Angle Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "ROT:",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary
                    )
                )

                // Quick Turn Left (-90°)
                RotationStepButton(
                    text = "-90°",
                    onClick = { onRotate(-90.0f) },
                    modifier = Modifier.weight(0.9f)
                )

                // Step Counterclockwise (-15°)
                RotationStepButton(
                    text = "-15°",
                    onClick = { onRotate(-15.0f) },
                    modifier = Modifier.weight(0.9f)
                )

                // Direct Editable Numeric Angle Field
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .height(28.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surface)
                        .border(1.dp, PrecisionBlue.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
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
                                fontSize = 11.sp,
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
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrecisionBlue
                            )
                        )
                    }
                }

                // Step Clockwise (+15°)
                RotationStepButton(
                    text = "+15°",
                    onClick = { onRotate(+15.0f) },
                    modifier = Modifier.weight(0.9f)
                )

                // Quick Turn Right (+90°)
                RotationStepButton(
                    text = "+90°",
                    onClick = { onRotate(+90.0f) },
                    modifier = Modifier.weight(0.9f)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 6. Actions: Duplicate & Delete
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Duplicate Action
                Button(
                    onClick = onDuplicate,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PrecisionBlue.copy(alpha = 0.12f),
                        contentColor = PrecisionBlue
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Duplicate asset",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DUPLICATE",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }

                // Delete Action
                Button(
                    onClick = onDelete,
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFDC2626).copy(alpha = 0.12f),
                        contentColor = Color(0xFFDC2626)
                    ),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 6.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(34.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.DeleteOutline,
                            contentDescription = "Delete asset",
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "DELETE",
                            style = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            }
        }
    }
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
            .height(26.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), RoundedCornerShape(4.dp))
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
        Spacer(modifier = Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
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
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = PrecisionBlue
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        modifier = modifier.height(28.dp)
    ) {
        Text(
            text = text,
            style = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
