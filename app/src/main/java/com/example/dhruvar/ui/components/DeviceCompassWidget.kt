package com.example.dhruvar.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.dhruvar.domain.sensors.CompassHeadingController
import com.example.dhruvar.domain.sensors.CompassState
import com.example.dhruvar.ui.theme.AnchorOrange
import com.example.dhruvar.ui.theme.PrecisionBlue
import kotlin.math.cos
import kotlin.math.sin

/**
 * Starts a lifecycle-bound [CompassHeadingController] and exposes its state to Compose.
 */
@Composable
fun rememberCompassState(): State<CompassState> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(context.applicationContext) {
        CompassHeadingController(context.applicationContext)
    }

    DisposableEffect(lifecycleOwner, controller) {
        lifecycleOwner.lifecycle.addObserver(controller)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(controller)
            controller.stop()
        }
    }

    return controller.state.collectAsState()
}

/**
 * Compact magnetic-north compass overlay for the planning canvas.
 * Dial rotates with device heading; fixed tip marker shows phone-forward.
 * Does not affect canvas pan/zoom/coordinates.
 */
@Composable
fun DeviceCompassWidget(
    compassState: CompassState,
    modifier: Modifier = Modifier
) {
    val heading = compassState.magneticHeadingDegrees
    val description = if (compassState.isAvailable) {
        "Magnetic compass ${heading.toInt()} degrees ${compassState.cardinalLabel}"
    } else {
        "Compass unavailable on this device"
    }

    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        ),
        modifier = modifier.semantics { contentDescription = description }
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                Canvas(modifier = Modifier.size(72.dp)) {
                    val cx = size.width / 2f
                    val cy = size.height / 2f
                    val radius = size.minDimension / 2f - 3.dp.toPx()

                    // Outer ring
                    drawCircle(
                        color = Color(0xFF334155),
                        radius = radius,
                        center = Offset(cx, cy),
                        style = Stroke(width = 1.5.dp.toPx())
                    )
                    drawCircle(
                        color = Color(0x22334155),
                        radius = radius,
                        center = Offset(cx, cy)
                    )

                    // Rotating dial: when heading=0 (pointing north), N sits at top
                    rotate(degrees = -heading, pivot = Offset(cx, cy)) {
                        drawCardinalLabels(
                            cx = cx,
                            cy = cy,
                            radius = radius - 10.dp.toPx()
                        )
                        // North needle (red)
                        val needleLen = radius - 14.dp.toPx()
                        drawLine(
                            color = AnchorOrange,
                            start = Offset(cx, cy + 6.dp.toPx()),
                            end = Offset(cx, cy - needleLen),
                            strokeWidth = 2.5.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                        // South stub
                        drawLine(
                            color = Color(0xFF94A3B8),
                            start = Offset(cx, cy),
                            end = Offset(cx, cy + needleLen * 0.55f),
                            strokeWidth = 2.dp.toPx(),
                            cap = StrokeCap.Round
                        )
                    }

                    // Fixed device-forward marker (top of widget = phone top)
                    val tipY = 4.dp.toPx()
                    drawLine(
                        color = PrecisionBlue,
                        start = Offset(cx - 5.dp.toPx(), tipY + 7.dp.toPx()),
                        end = Offset(cx, tipY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = PrecisionBlue,
                        start = Offset(cx + 5.dp.toPx(), tipY + 7.dp.toPx()),
                        end = Offset(cx, tipY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawCircle(
                        color = PrecisionBlue,
                        radius = 2.5.dp.toPx(),
                        center = Offset(cx, cy)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "${heading.toInt()}° ${compassState.cardinalLabel}",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = PrecisionBlue
                )
            )
            Text(
                text = if (!compassState.isAvailable) {
                    "NO SENSOR"
                } else if (compassState.needsCalibration) {
                    "Calibrate compass"
                } else {
                    "Magnetic N"
                },
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 8.sp,
                    color = if (compassState.needsCalibration) {
                        AnchorOrange
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawCardinalLabels(
    cx: Float,
    cy: Float,
    radius: Float
) {
    val paint = android.graphics.Paint().apply {
        isAntiAlias = true
        textAlign = android.graphics.Paint.Align.CENTER
        textSize = 11.sp.toPx()
        typeface = android.graphics.Typeface.MONOSPACE
        isFakeBoldText = true
    }

    val labels = listOf(
        "N" to 0f,
        "E" to 90f,
        "S" to 180f,
        "W" to 270f
    )
    for ((label, deg) in labels) {
        val rad = Math.toRadians(deg.toDouble() - 90.0) // 0° at top
        val x = cx + cos(rad).toFloat() * radius
        val y = cy + sin(rad).toFloat() * radius + 4.dp.toPx()
        paint.color = if (label == "N") {
            android.graphics.Color.rgb(217, 119, 6) // AnchorOrange
        } else {
            android.graphics.Color.rgb(100, 116, 139)
        }
        drawContext.canvas.nativeCanvas.drawText(label, x, y, paint)
    }
}
