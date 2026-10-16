package com.example.dhruvar.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.GeoBounds
import com.example.dhruvar.domain.spatial.RegionDraftGeometry
import com.example.dhruvar.ui.theme.Coal
import com.example.dhruvar.ui.theme.Meadow
import com.example.dhruvar.ui.theme.Mist
import com.example.dhruvar.ui.theme.Moss

/**
 * A rectangle being dragged out. Screen coordinates, because the basemap is frozen for the
 * duration of the drag and projecting every frame would only add error.
 */
data class RegionDraft(
    val start: Offset,
    val current: Offset,
    /** Null while the geographic corners are still unknown or invalid. */
    val bounds: GeoBounds? = null
) {
    val rect: Rect
        get() = Rect(
            left = minOf(start.x, current.x),
            top = minOf(start.y, current.y),
            right = maxOf(start.x, current.x),
            bottom = maxOf(start.y, current.y)
        )
}

/**
 * Corner-drag region selection, drawn and handled entirely in Compose above the map.
 *
 * Owning the gesture here rather than through MapLibre's own touch listeners means there is no
 * contest with pan and pinch: the overlay consumes the pointer from the first down, so a drag can
 * never be interpreted as a pan. The cost is that the map does not pan while Region mode is
 * active — panning belongs to Navigate mode.
 */
@Composable
fun RegionDraftOverlay(
    draft: RegionDraft?,
    onDraftChange: (RegionDraft?) -> Unit,
    onDraftFinished: (RegionDraft) -> Unit,
    modifier: Modifier = Modifier
) {
    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitEachGesture {
                    // Initial pass, so the map view below never receives the sequence at all.
                    val down = awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial
                    )
                    down.consume()
                    MapViewportBoundsBridge.setGesturesEnabled(false)

                    var latest = RegionDraft(down.position, down.position)
                    var released = false
                    onDraftChange(latest)

                    try {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: continue
                            latest = latest.copy(current = change.position)
                            onDraftChange(latest)
                            // `pressed` stays reliable after consume. changedToUp() does not:
                            // consuming the change makes it report "still down", so the draft
                            // never reached Confirm on a device.
                            val lifted = !change.pressed
                            change.consume()
                            if (lifted) {
                                released = true
                                break
                            }
                        }
                    } finally {
                        MapViewportBoundsBridge.setGesturesEnabled(true)
                        if (!released) onDraftChange(null)
                    }

                    if (released) onDraftFinished(latest)
                }
            }
    ) {
        val rect = draft?.rect ?: return@Canvas
        drawRect(
            color = Meadow.copy(alpha = 0.28f),
            topLeft = rect.topLeft,
            size = rect.size
        )
        drawRect(
            color = Moss,
            topLeft = rect.topLeft,
            size = rect.size,
            style = Stroke(width = 2.dp.toPx())
        )
        // Corner pips, so a thin box is still visible against busy tiles.
        val pip = 5.dp.toPx()
        listOf(rect.topLeft, rect.topRight, rect.bottomLeft, rect.bottomRight).forEach { corner ->
            drawRect(
                color = Mist,
                topLeft = Offset(corner.x - pip, corner.y - pip),
                size = Size(pip * 2, pip * 2)
            )
            drawRect(
                color = Coal,
                topLeft = Offset(corner.x - pip, corner.y - pip),
                size = Size(pip * 2, pip * 2),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/** Resolves a screen-space [RegionDraft] into geographic corners via the map projection. */
fun RegionDraft.withResolvedBounds(): RegionDraft {
    val first = MapViewportBoundsBridge.toLatLng(start.x, start.y) ?: return copy(bounds = null)
    val second = MapViewportBoundsBridge.toLatLng(current.x, current.y)
        ?: return copy(bounds = null)
    return copy(
        bounds = RegionDraftGeometry.boundsBetween(
            firstLatitude = first.first,
            firstLongitude = first.second,
            secondLatitude = second.first,
            secondLongitude = second.second
        )
    )
}
