package com.example.dhruvar.ui.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.ui.theme.PrecisionBlue

@Composable
fun PlanningSurfaceSwitcher(
    surface: PlanningSurface,
    onSurfaceChange: (PlanningSurface) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        FilterChip(
            selected = surface == PlanningSurface.MAP,
            onClick = { onSurfaceChange(PlanningSurface.MAP) },
            label = { Text("Map", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrecisionBlue.copy(alpha = 0.22f),
                selectedLabelColor = PrecisionBlue
            ),
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = surface == PlanningSurface.METRIC_GRID,
            onClick = { onSurfaceChange(PlanningSurface.METRIC_GRID) },
            label = { Text("Metric grid", fontWeight = FontWeight.SemiBold, fontSize = 13.sp) },
            shape = RoundedCornerShape(8.dp),
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = PrecisionBlue.copy(alpha = 0.22f),
                selectedLabelColor = PrecisionBlue
            ),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun MapModeToolbar(
    mode: MapInteractionMode,
    onModeChange: (MapInteractionMode) -> Unit,
    hasRegion: Boolean,
    onConfirmViewportRegion: () -> Unit,
    onClearRegion: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            MapModeChip("Nav", MapInteractionMode.NAVIGATE, mode, onModeChange)
            MapModeChip("Region", MapInteractionMode.SELECT_REGION, mode, onModeChange)
            MapModeChip("Anchor", MapInteractionMode.SET_ANCHOR, mode, onModeChange)
            MapModeChip("Place", MapInteractionMode.PLACE_ASSET, mode, onModeChange)
            MapModeChip("Edit", MapInteractionMode.EDIT, mode, onModeChange)
        }
            Text(
                text = if (hasRegion) {
                    "Region active — placements outside bounds are rejected."
                } else {
                    "Region optional — without bounds, map placement is allowed site-wide."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
            if (mode == MapInteractionMode.SELECT_REGION) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TextButton(onClick = onConfirmViewportRegion) {
                        Text("Use visible area", fontWeight = FontWeight.SemiBold, color = PrecisionBlue)
                    }
                    if (hasRegion) {
                        TextButton(onClick = onClearRegion) {
                            Text("Clear region", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Text(
                    text = "Pan/zoom to the site, then confirm the visible map as region bounds.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
            }
    }
}

@Composable
private fun MapModeChip(
    label: String,
    value: MapInteractionMode,
    selected: MapInteractionMode,
    onModeChange: (MapInteractionMode) -> Unit
) {
    FilterChip(
        selected = selected == value,
        onClick = { onModeChange(value) },
        label = { Text(label, fontSize = 12.sp) },
        shape = RoundedCornerShape(8.dp),
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = PrecisionBlue.copy(alpha = 0.22f),
            selectedLabelColor = PrecisionBlue
        )
    )
}
