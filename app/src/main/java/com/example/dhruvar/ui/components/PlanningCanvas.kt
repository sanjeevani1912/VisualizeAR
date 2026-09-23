package com.example.dhruvar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.domain.model.LayoutObject
import com.example.dhruvar.domain.spatial.CoordinateTransformer
import com.example.dhruvar.domain.spatial.ObjectHitTester
import com.example.dhruvar.domain.spatial.ScaleBarInfo
import com.example.dhruvar.domain.spatial.SpatialDistanceCalculator
import com.example.dhruvar.ui.theme.AnchorOrange
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.Slate300
import com.example.dhruvar.ui.theme.Slate400
import com.example.dhruvar.ui.theme.Slate700
import com.example.dhruvar.ui.theme.Slate800
import com.example.dhruvar.ui.theme.Slate900
import kotlin.math.roundToInt

/**
 * 2D Top-Down Spatial Planning Canvas.
 *
 * Implements:
 * - Direct object dragging with touch grab-offset preservation (no jump on pick up)
 * - Canvas panning when dragging empty areas (conflict-free gesture discrimination)
 * - Two-finger pinch-to-zoom and multi-touch panning
 * - Tap-to-select placed objects & tap-to-place active assets
 * - Distinct 2D architectural footprints for all 5 asset types
 * - Live coordinate readout HUD and dynamic scale bar
 */
@Composable
fun PlanningCanvas(
    layout: Layout,
    panOffset: Offset,
    zoom: Float,
    basePixelsPerMeter: Float,
    cursorCoordinatesMeters: Pair<Float, Float>?,
    onPan: (Offset) -> Unit,
    onZoom: (zoomMultiplier: Float, centroid: Offset) -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    onCursorMoved: (xMeters: Float, zMeters: Float) -> Unit,
    onCursorCleared: () -> Unit,
    onCanvasTapped: (xMeters: Float, zMeters: Float) -> Unit,
    onObjectSelected: (objectId: String) -> Unit,
    onMoveObject: (objectId: String, newX: Float, newZ: Float) -> Unit,
    onDeselect: () -> Unit,
    modifier: Modifier = Modifier,
    selectedObjectId: String? = null,
    showDistances: Boolean = true,
    onToggleShowDistances: () -> Unit = {}
) {
    val compassState by rememberCompassState()

    val isDark = MaterialTheme.colorScheme.background == Slate900
    val gridColor = if (isDark) Slate800 else Slate300
    val axisColor = if (isDark) Slate700 else Slate400
    val canvasBg = MaterialTheme.colorScheme.surface

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(canvasBg)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
    ) {
        val widthPx = constraints.maxWidth.toFloat()
        val heightPx = constraints.maxHeight.toFloat()

        val transformer = remember(widthPx, heightPx, panOffset, zoom, basePixelsPerMeter) {
            CoordinateTransformer(
                viewportWidth = widthPx,
                viewportHeight = heightPx,
                panOffset = panOffset,
                zoom = zoom,
                basePixelsPerMeter = basePixelsPerMeter
            )
        }

        val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()

        // Keep latest transform/objects in refs so pointerInput does NOT restart when
        // asset positions update mid-drag (restarting would cancel the gesture).
        val gestureRefs = remember {
            object {
                var transformer: CoordinateTransformer = transformer
                var objects: List<LayoutObject> = layout.objects
                var selectedObjectId: String? = selectedObjectId
            }
        }
        gestureRefs.transformer = transformer
        gestureRefs.objects = layout.objects
        gestureRefs.selectedObjectId = selectedObjectId

        // Unified Multi-Mode Gesture Handler
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isMultiTouch = false
                        var isDraggingObject = false
                        var draggedObjectId: String? = null
                        var grabOffsetX = 0f
                        var grabOffsetZ = 0f
                        var totalDragDistance = 0f
                        var lastSinglePointerPos = down.position

                        val startTransformer = gestureRefs.transformer
                        val startObjects = gestureRefs.objects

                        // Hit-test: asset under finger → drag; empty canvas → pan
                        val initialHit = ObjectHitTester.findHitObject(
                            tapOffset = down.position,
                            objects = startObjects,
                            transformer = startTransformer
                        )

                        if (initialHit != null) {
                            draggedObjectId = initialHit.id
                            isDraggingObject = true
                            onObjectSelected(initialHit.id)

                            // World-space grab offset so the touch point stays under the finger
                            val (touchWorldX, touchWorldZ) = startTransformer.canvasToWorld(down.position)
                            grabOffsetX = touchWorldX - initialHit.x
                            grabOffsetZ = touchWorldZ - initialHit.z
                        }

                        var prevSpan = 0f
                        var prevCentroid = Offset.Zero

                        do {
                            val event = awaitPointerEvent()
                            val activePointers = event.changes.filter { it.pressed }
                            val liveTransformer = gestureRefs.transformer

                            if (activePointers.size >= 2) {
                                // Multi-touch: pinch-zoom / two-finger pan (cancels asset drag)
                                isMultiTouch = true
                                isDraggingObject = false
                                draggedObjectId = null

                                val p1 = activePointers[0].position
                                val p2 = activePointers[1].position
                                val currentSpan = (p1 - p2).getDistance()
                                val currentCentroid = (p1 + p2) / 2f

                                if (prevSpan > 0f && currentSpan > 0f) {
                                    val zoomMultiplier = currentSpan / prevSpan
                                    val panDelta = currentCentroid - prevCentroid
                                    if (zoomMultiplier != 1.0f) {
                                        onZoom(zoomMultiplier, currentCentroid)
                                    }
                                    if (panDelta != Offset.Zero) {
                                        onPan(panDelta)
                                    }
                                }
                                prevSpan = currentSpan
                                prevCentroid = currentCentroid
                                event.changes.forEach { it.consume() }
                            } else if (activePointers.size == 1 && !isMultiTouch) {
                                val pointer = activePointers.first()
                                val moveDelta = pointer.position - lastSinglePointerPos
                                totalDragDistance += moveDelta.getDistance()
                                lastSinglePointerPos = pointer.position

                                val dragId = draggedObjectId
                                if (isDraggingObject && dragId != null) {
                                    // Screen → world via live transformer (respects current zoom/pan)
                                    val (curWorldX, curWorldZ) = liveTransformer.canvasToWorld(pointer.position)
                                    val newX = curWorldX - grabOffsetX
                                    val newZ = curWorldZ - grabOffsetZ
                                    onMoveObject(dragId, newX, newZ)
                                    onCursorMoved(newX, newZ)
                                    pointer.consume()
                                } else if (draggedObjectId == null) {
                                    // Empty-canvas pan
                                    if (totalDragDistance > 6f) {
                                        onPan(moveDelta)
                                        val (curX, curZ) = liveTransformer.canvasToWorld(pointer.position)
                                        onCursorMoved(curX, curZ)
                                        pointer.consume()
                                    }
                                }
                            }
                        } while (event.changes.any { it.pressed })

                        // Tap (minimal movement): select / deselect
                        if (!isMultiTouch && totalDragDistance < 8f) {
                            if (draggedObjectId != null) {
                                onObjectSelected(draggedObjectId)
                            } else {
                                val (tapX, tapZ) = gestureRefs.transformer.canvasToWorld(down.position)
                                if (gestureRefs.selectedObjectId != null) {
                                    onDeselect()
                                } else {
                                    onCanvasTapped(tapX, tapZ)
                                }
                            }
                        }
                    }
                }
        ) {
            // 1. Draw Adaptive Metric Grid
            drawMetricGrid(
                transformer = transformer,
                gridColor = gridColor,
                axisColor = axisColor
            )

            // 2. Draw Spatial Measurement Line between Anchor and Selected Object
            if (showDistances && selectedObjectId != null) {
                val selectedObj = layout.objects.firstOrNull { it.id == selectedObjectId }
                if (selectedObj != null) {
                    drawSpatialMeasurementLine(
                        selectedObject = selectedObj,
                        anchor = layout.anchor,
                        transformer = transformer,
                        textMeasurer = textMeasurer,
                        measurementColor = AnchorOrange,
                        badgeBgColor = canvasBg
                    )
                }
            }

            // 3. Draw Placed Layout Objects
            layout.objects.forEach { obj ->
                drawDistinctLayoutObject(
                    obj = obj,
                    transformer = transformer,
                    isSelected = (obj.id == selectedObjectId),
                    textMeasurer = textMeasurer,
                    primaryColor = PrecisionBlue
                )
            }

            // 3. Draw Anchor Marker at Logical Origin (0, 0)
            drawAnchorMarker(
                transformer = transformer,
                textMeasurer = textMeasurer
            )
        }

        // --- HUD OVERLAYS ---

        // Empty canvas guidance
        if (layout.objects.isEmpty()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "No objects placed",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
                    )
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap + to choose an asset and place it on the canvas.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Default
                    ),
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }

        // 1. Top-Left: Tactical Coordinate & Zoom Status Readout
        TacticalHudCard(
            transformer = transformer,
            cursorCoordinates = cursorCoordinatesMeters,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(10.dp)
        )

        // 2. Top-Right: Live magnetic compass (device orientation reference)
        // Does not rotate the planning canvas coordinate system.
        DeviceCompassWidget(
            compassState = compassState,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        )

        // 3. Bottom-Left: Dynamic Scale Bar
        val scaleBarInfo = remember(transformer.pixelsPerMeter) {
            transformer.calculateDynamicScaleBar(targetMaxBarWidthPx = 130f)
        }
        DynamicScaleBarWidget(
            scaleBarInfo = scaleBarInfo,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )

        // 4. Bottom-Left: Viewport Zoom & Reset Floating Controls
        // (Bottom-right reserved for the Add Asset FAB on PlanningScreen)
        ViewportControlsWidget(
            onZoomIn = onZoomIn,
            onZoomOut = onZoomOut,
            onResetView = onResetView,
            showDistances = showDistances,
            onToggleShowDistances = onToggleShowDistances,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(10.dp)
        )
    }
}

// -----------------------------------------------------------------------------
// CANVAS DRAWING EXTENSIONS
// -----------------------------------------------------------------------------

private fun DrawScope.drawMetricGrid(
    transformer: CoordinateTransformer,
    gridColor: Color,
    axisColor: Color
) {
    val stepMeters = when {
        transformer.zoom < 0.45f -> 5.0f
        transformer.zoom < 0.85f -> 2.0f
        else -> 1.0f
    }
    val stepPx = transformer.metersToPixels(stepMeters)
    if (stepPx <= 0f) return

    val originX = transformer.originScreenX
    val originY = transformer.originScreenY
    val strokeWidth = 1.dp.toPx()
    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f), 0f)

    // Vertical grid lines (aligned to originX)
    var x = originX % stepPx
    if (x < 0) x += stepPx
    while (x < size.width) {
        val meterIndex = ((x - originX) / stepPx).roundToInt()
        val isMajor = (meterIndex % 5 == 0)
        drawLine(
            color = if (isMajor) gridColor.copy(alpha = 0.85f) else gridColor.copy(alpha = 0.45f),
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = if (isMajor) strokeWidth * 1.2f else strokeWidth,
            pathEffect = if (isMajor) null else dashedEffect
        )
        x += stepPx
    }

    // Horizontal grid lines (aligned to originY)
    var y = originY % stepPx
    if (y < 0) y += stepPx
    while (y < size.height) {
        val meterIndex = ((originY - y) / stepPx).roundToInt()
        val isMajor = (meterIndex % 5 == 0)
        drawLine(
            color = if (isMajor) gridColor.copy(alpha = 0.85f) else gridColor.copy(alpha = 0.45f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = if (isMajor) strokeWidth * 1.2f else strokeWidth,
            pathEffect = if (isMajor) null else dashedEffect
        )
        y += stepPx
    }

    // Primary Axis Lines (Passing through Anchor Origin)
    // Horizontal X Axis (+X East)
    if (originY in 0f..size.height) {
        drawLine(
            color = axisColor,
            start = Offset(0f, originY),
            end = Offset(size.width, originY),
            strokeWidth = 1.8.dp.toPx()
        )
    }

    // Vertical Z Axis (+Z North)
    if (originX in 0f..size.width) {
        drawLine(
            color = axisColor,
            start = Offset(originX, 0f),
            end = Offset(originX, size.height),
            strokeWidth = 1.8.dp.toPx()
        )
    }
}

private fun DrawScope.drawAnchorMarker(
    transformer: CoordinateTransformer,
    textMeasurer: TextMeasurer
) {
    val anchorOffset = transformer.worldToCanvas(0.0f, 0.0f)
    val cx = anchorOffset.x
    val cy = anchorOffset.y

    val outerRadius = 16f
    val innerRadius = 5f
    val crosshairLength = 26f

    // 1. Radar circles
    drawCircle(
        color = AnchorOrange.copy(alpha = 0.18f),
        radius = outerRadius * 2.2f,
        center = Offset(cx, cy)
    )
    drawCircle(
        color = AnchorOrange,
        radius = outerRadius,
        center = Offset(cx, cy),
        style = Stroke(width = 2.dp.toPx())
    )

    // 2. Precision Crosshair ticks
    val tickStroke = 2.dp.toPx()
    drawLine(AnchorOrange, Offset(cx - crosshairLength, cy), Offset(cx - outerRadius - 2f, cy), strokeWidth = tickStroke)
    drawLine(AnchorOrange, Offset(cx + outerRadius + 2f, cy), Offset(cx + crosshairLength, cy), strokeWidth = tickStroke)
    drawLine(AnchorOrange, Offset(cx, cy - crosshairLength), Offset(cx, cy - outerRadius - 2f), strokeWidth = tickStroke)
    drawLine(AnchorOrange, Offset(cx, cy + outerRadius + 2f), Offset(cx, cy + crosshairLength), strokeWidth = tickStroke)

    // 3. Central Solid Bullseye (●)
    drawCircle(
        color = AnchorOrange,
        radius = innerRadius,
        center = Offset(cx, cy)
    )

    // 4. Anchor Datum Label
    val label = "ANCHOR (0.0, 0.0)"
    val textResult = textMeasurer.measure(
        text = label,
        style = TextStyle(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = AnchorOrange
        )
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(cx - (textResult.size.width / 2f), cy + outerRadius + 8f)
    )
}

/**
 * Renders an individual placed object with a distinct 2D architectural top-down symbol.
 */
private fun DrawScope.drawDistinctLayoutObject(
    obj: LayoutObject,
    transformer: CoordinateTransformer,
    isSelected: Boolean,
    textMeasurer: TextMeasurer,
    primaryColor: Color
) {
    val screenPos = transformer.worldToCanvas(obj.x, obj.z)
    val widthPx = transformer.metersToPixels(obj.widthMeters)
    val lengthPx = transformer.metersToPixels(obj.lengthMeters)

    rotate(degrees = obj.rotationDegrees, pivot = screenPos) {
        val rectTopLeft = Offset(screenPos.x - (widthPx / 2f), screenPos.y - (lengthPx / 2f))
        val rectSize = Size(widthPx, lengthPx)

        // 1. Base footprint fill & stroke
        val baseFillColor = if (isSelected) primaryColor.copy(alpha = 0.22f) else Color(0x32334155)
        val baseStrokeColor = if (isSelected) primaryColor else Color(0xFF475569)
        val strokeWidth = if (isSelected) 2.2.dp.toPx() else 1.2.dp.toPx()

        drawRoundRect(
            color = baseFillColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = CornerRadius(4f, 4f)
        )
        drawRoundRect(
            color = baseStrokeColor,
            topLeft = rectTopLeft,
            size = rectSize,
            cornerRadius = CornerRadius(4f, 4f),
            style = Stroke(width = strokeWidth)
        )

        // 2. Distinct 2D Architectural Symbol based on AssetType
        when (obj.assetType) {
            AssetType.TENT -> {
                // Ridge line down longitudinal center
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(screenPos.x, rectTopLeft.y),
                    end = Offset(screenPos.x, rectTopLeft.y + lengthPx),
                    strokeWidth = 1.5.dp.toPx()
                )
                // Triangular apex flaps at front and rear
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x, rectTopLeft.y),
                    end = Offset(screenPos.x, rectTopLeft.y + (lengthPx * 0.22f)),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x + widthPx, rectTopLeft.y),
                    end = Offset(screenPos.x, rectTopLeft.y + (lengthPx * 0.22f)),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x, rectTopLeft.y + lengthPx),
                    end = Offset(screenPos.x, rectTopLeft.y + (lengthPx * 0.78f)),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x + widthPx, rectTopLeft.y + lengthPx),
                    end = Offset(screenPos.x, rectTopLeft.y + (lengthPx * 0.78f)),
                    strokeWidth = 1.dp.toPx()
                )
            }

            AssetType.TRUCK -> {
                // Cab section line (front 35%)
                val cabY = rectTopLeft.y + (lengthPx * 0.35f)
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x, cabY),
                    end = Offset(rectTopLeft.x + widthPx, cabY),
                    strokeWidth = 1.8.dp.toPx()
                )
                // Windshield band
                drawRoundRect(
                    color = baseStrokeColor.copy(alpha = 0.5f),
                    topLeft = Offset(rectTopLeft.x + (widthPx * 0.15f), rectTopLeft.y + (lengthPx * 0.08f)),
                    size = Size(widthPx * 0.7f, lengthPx * 0.12f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Rear cargo bed cross / divider
                drawLine(
                    color = baseStrokeColor.copy(alpha = 0.6f),
                    start = Offset(screenPos.x, cabY),
                    end = Offset(screenPos.x, rectTopLeft.y + lengthPx),
                    strokeWidth = 1.dp.toPx()
                )
            }

            AssetType.CAR -> {
                // Front hood line (front 28%)
                val hoodY = rectTopLeft.y + (lengthPx * 0.28f)
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x, hoodY),
                    end = Offset(rectTopLeft.x + widthPx, hoodY),
                    strokeWidth = 1.2.dp.toPx()
                )
                // Passenger cabin / roof outline
                drawRoundRect(
                    color = baseStrokeColor.copy(alpha = 0.45f),
                    topLeft = Offset(rectTopLeft.x + (widthPx * 0.15f), hoodY),
                    size = Size(widthPx * 0.7f, lengthPx * 0.45f),
                    cornerRadius = CornerRadius(2f, 2f)
                )
                // Rear trunk line
                val trunkY = rectTopLeft.y + (lengthPx * 0.73f)
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(rectTopLeft.x, trunkY),
                    end = Offset(rectTopLeft.x + widthPx, trunkY),
                    strokeWidth = 1.2.dp.toPx()
                )
            }

            AssetType.ANTENNA -> {
                // Central transmitter mast
                drawCircle(
                    color = baseStrokeColor,
                    radius = (widthPx * 0.22f).coerceAtLeast(3f),
                    center = screenPos
                )
                // Cross mast base
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(screenPos.x - (widthPx / 2f), screenPos.y),
                    end = Offset(screenPos.x + (widthPx / 2f), screenPos.y),
                    strokeWidth = 1.2.dp.toPx()
                )
                drawLine(
                    color = baseStrokeColor,
                    start = Offset(screenPos.x, screenPos.y - (lengthPx / 2f)),
                    end = Offset(screenPos.x, screenPos.y + (lengthPx / 2f)),
                    strokeWidth = 1.2.dp.toPx()
                )
                // Radio broadcast wave rings
                drawCircle(
                    color = baseStrokeColor.copy(alpha = 0.5f),
                    radius = (widthPx * 0.45f).coerceAtLeast(6f),
                    center = screenPos,
                    style = Stroke(width = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f), 0f))
                )
            }

            AssetType.TRENCH -> {
                // Elongated trench center conduit
                val insetX = widthPx * 0.2f
                drawRect(
                    color = Color(0x600F172A),
                    topLeft = Offset(rectTopLeft.x + insetX, rectTopLeft.y),
                    size = Size(widthPx - (insetX * 2f), lengthPx)
                )
                // Revetment hatch marks along the walls
                val hatchStep = (lengthPx / 6f).coerceAtLeast(8f)
                var hy = rectTopLeft.y + 4f
                while (hy < rectTopLeft.y + lengthPx - 2f) {
                    drawLine(
                        color = baseStrokeColor,
                        start = Offset(rectTopLeft.x, hy),
                        end = Offset(rectTopLeft.x + insetX, hy),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = baseStrokeColor,
                        start = Offset(rectTopLeft.x + widthPx - insetX, hy),
                        end = Offset(rectTopLeft.x + widthPx, hy),
                        strokeWidth = 1.dp.toPx()
                    )
                    hy += hatchStep
                }
            }
        }

        // Direction orientation tick pointing North / forward
        drawLine(
            color = baseStrokeColor,
            start = Offset(screenPos.x, rectTopLeft.y),
            end = Offset(screenPos.x, rectTopLeft.y - 8f),
            strokeWidth = 2.dp.toPx()
        )

        // 3. Selection Reticle Brackets
        if (isSelected) {
            val bracketLen = 8f
            val bStroke = 2.dp.toPx()
            val pad = 3f

            // Top-Left
            drawLine(primaryColor, Offset(rectTopLeft.x - pad, rectTopLeft.y - pad), Offset(rectTopLeft.x - pad + bracketLen, rectTopLeft.y - pad), strokeWidth = bStroke)
            drawLine(primaryColor, Offset(rectTopLeft.x - pad, rectTopLeft.y - pad), Offset(rectTopLeft.x - pad, rectTopLeft.y - pad + bracketLen), strokeWidth = bStroke)
            // Top-Right
            drawLine(primaryColor, Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y - pad), Offset(rectTopLeft.x + widthPx + pad - bracketLen, rectTopLeft.y - pad), strokeWidth = bStroke)
            drawLine(primaryColor, Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y - pad), Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y - pad + bracketLen), strokeWidth = bStroke)
            // Bottom-Left
            drawLine(primaryColor, Offset(rectTopLeft.x - pad, rectTopLeft.y + lengthPx + pad), Offset(rectTopLeft.x - pad + bracketLen, rectTopLeft.y + lengthPx + pad), strokeWidth = bStroke)
            drawLine(primaryColor, Offset(rectTopLeft.x - pad, rectTopLeft.y + lengthPx + pad), Offset(rectTopLeft.x - pad, rectTopLeft.y + lengthPx + pad - bracketLen), strokeWidth = bStroke)
            // Bottom-Right
            drawLine(primaryColor, Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y + lengthPx + pad), Offset(rectTopLeft.x + widthPx + pad - bracketLen, rectTopLeft.y + lengthPx + pad), strokeWidth = bStroke)
            drawLine(primaryColor, Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y + lengthPx + pad), Offset(rectTopLeft.x + widthPx + pad, rectTopLeft.y + lengthPx + pad - bracketLen), strokeWidth = bStroke)
        }
    }

    // Object Identification Label with dynamic clearance for rotated bounding box
    val rad = Math.toRadians(obj.rotationDegrees.toDouble())
    val sinAngle = kotlin.math.abs(kotlin.math.sin(rad)).toFloat()
    val cosAngle = kotlin.math.abs(kotlin.math.cos(rad)).toFloat()
    val halfBoundingHeightPx = (widthPx * sinAngle + lengthPx * cosAngle) / 2f

    val label = if (isSelected) {
        "● ${obj.name} (${obj.x.toInt()}m, ${obj.z.toInt()}m, ${obj.rotationDegrees.toInt()}°) [SELECTED]"
    } else {
        "${obj.name} (${obj.x.toInt()}m, ${obj.z.toInt()}m)"
    }
    val textResult = textMeasurer.measure(
        text = label,
        style = TextStyle(
            fontSize = if (isSelected) 10.sp else 9.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) primaryColor else Color(0xFF64748B)
        )
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(screenPos.x - (textResult.size.width / 2f), screenPos.y + halfBoundingHeightPx + 6f)
    )
}

// -----------------------------------------------------------------------------
// HUD COMPOSABLES
// -----------------------------------------------------------------------------

@Composable
private fun TacticalHudCard(
    transformer: CoordinateTransformer,
    cursorCoordinates: Pair<Float, Float>?,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)) {
            Text(
                text = "DATUM: ORIGIN (0.0, 0.0)",
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnchorOrange
                )
            )
            Spacer(modifier = Modifier.height(2.dp))
            if (cursorCoordinates != null) {
                val (cx, cz) = cursorCoordinates
                Text(
                    text = String.format("CURSOR: X: %+.2fm  Z: %+.2fm", cx, cz),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PrecisionBlue
                    )
                )
            } else {
                val (vx, vz) = transformer.canvasToWorld(
                    Offset(transformer.viewportWidth / 2f, transformer.viewportHeight / 2f)
                )
                Text(
                    text = String.format("CENTER: X: %+.1fm  Z: %+.1fm", vx, vz),
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
            Text(
                text = String.format("ZOOM: %.2fx (1m = %.0fpx)", transformer.zoom, transformer.pixelsPerMeter),
                style = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            )
        }
    }
}

@Composable
private fun DynamicScaleBarWidget(
    scaleBarInfo: ScaleBarInfo,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.width(scaleBarInfo.widthPx.coerceAtLeast(40f).dp.value.dp)
            ) {
                Text(
                    text = "0",
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
                Text(
                    text = scaleBarInfo.label,
                    style = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            Canvas(
                modifier = Modifier
                    .width(scaleBarInfo.widthPx.coerceAtLeast(40f).dp.value.dp)
                    .height(6.dp)
            ) {
                val barW = size.width
                val tickH = 6.dp.toPx()
                val lineStroke = 1.5.dp.toPx()
                val color = Color(0xFF475569)

                drawLine(color, Offset(0f, tickH / 2f), Offset(barW, tickH / 2f), strokeWidth = lineStroke)
                drawLine(color, Offset(0f, 0f), Offset(0f, tickH), strokeWidth = lineStroke)
                drawLine(color, Offset(barW, 0f), Offset(barW, tickH), strokeWidth = lineStroke)
                drawLine(color, Offset(barW / 2f, 2f), Offset(barW / 2f, tickH - 2f), strokeWidth = 1.dp.toPx())
            }
        }
    }
}

@Composable
private fun ViewportControlsWidget(
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    onResetView: () -> Unit,
    showDistances: Boolean,
    onToggleShowDistances: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.95f),
        shape = RoundedCornerShape(8.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = modifier
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(2.dp)
        ) {
            IconButton(
                onClick = onZoomIn,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Zoom In",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onZoomOut,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Zoom Out",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onResetView,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = "Reset View",
                    tint = PrecisionBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onToggleShowDistances,
                modifier = Modifier.size(34.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Straighten,
                    contentDescription = if (showDistances) "Hide distance measurement" else "Show distance measurement",
                    tint = if (showDistances) AnchorOrange else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Renders an authoritative dashed measurement line and tactical distance badge between
 * the spatial Anchor datum and the currently selected LayoutObject.
 */
private fun DrawScope.drawSpatialMeasurementLine(
    selectedObject: LayoutObject,
    anchor: com.example.dhruvar.domain.model.Anchor,
    transformer: CoordinateTransformer,
    textMeasurer: TextMeasurer,
    measurementColor: Color,
    badgeBgColor: Color
) {
    val distanceMeters = SpatialDistanceCalculator.calculateDistanceToAnchor(selectedObject, anchor)
    val distanceLabel = SpatialDistanceCalculator.formatDistance(distanceMeters)

    val anchorPos = transformer.worldToCanvas(anchor.x, anchor.z)
    val objPos = transformer.worldToCanvas(selectedObject.x, selectedObject.z)

    // 1. Dashed connecting line
    val dashEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
    drawLine(
        color = measurementColor.copy(alpha = 0.85f),
        start = anchorPos,
        end = objPos,
        strokeWidth = 2.dp.toPx(),
        pathEffect = dashEffect
    )

    // 2. Perpendicular end-cap ticks on the anchor and object ends
    val dx = objPos.x - anchorPos.x
    val dy = objPos.y - anchorPos.y
    val length = kotlin.math.hypot(dx.toDouble(), dy.toDouble()).toFloat()

    if (length > 24f) {
        val perpX = (-dy / length) * 8f
        val perpY = (dx / length) * 8f

        // Anchor end-cap tick
        drawLine(
            color = measurementColor,
            start = Offset(anchorPos.x - perpX, anchorPos.y - perpY),
            end = Offset(anchorPos.x + perpX, anchorPos.y + perpY),
            strokeWidth = 1.5.dp.toPx()
        )
        // Object end-cap tick
        drawLine(
            color = measurementColor,
            start = Offset(objPos.x - perpX, objPos.y - perpY),
            end = Offset(objPos.x + perpX, objPos.y + perpY),
            strokeWidth = 1.5.dp.toPx()
        )
    }

    // 3. Midpoint Tactical Distance Badge
    val midX = (anchorPos.x + objPos.x) / 2f
    val midY = (anchorPos.y + objPos.y) / 2f

    val textResult = textMeasurer.measure(
        text = distanceLabel,
        style = TextStyle(
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            color = measurementColor
        )
    )

    val padH = 6.dp.toPx()
    val padV = 3.dp.toPx()
    val badgeW = textResult.size.width + (padH * 2f)
    val badgeH = textResult.size.height + (padV * 2f)

    // Position badge centered at line midpoint
    val badgeTopLeft = Offset(midX - (badgeW / 2f), midY - (badgeH / 2f))

    // Badge solid surface & border
    drawRoundRect(
        color = badgeBgColor,
        topLeft = badgeTopLeft,
        size = Size(badgeW, badgeH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    )
    drawRoundRect(
        color = measurementColor.copy(alpha = 0.85f),
        topLeft = badgeTopLeft,
        size = Size(badgeW, badgeH),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
        style = Stroke(width = 1.2.dp.toPx())
    )
    drawText(
        textLayoutResult = textResult,
        topLeft = Offset(badgeTopLeft.x + padH, badgeTopLeft.y + padV)
    )
}
