package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface

@Composable
fun PlanningSurfaceSwitcher(
    surface: PlanningSurface,
    onSurfaceChange: (PlanningSurface) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            CompactSegment(
                selected = surface == PlanningSurface.MAP,
                icon = Icons.Default.Map,
                label = "Map",
                onClick = { onSurfaceChange(PlanningSurface.MAP) }
            )
            CompactSegment(
                selected = surface == PlanningSurface.METRIC_GRID,
                icon = Icons.Default.GridOn,
                label = "Grid",
                onClick = { onSurfaceChange(PlanningSurface.METRIC_GRID) }
            )
        }
    }
}

@Composable
private fun CompactSegment(
    selected: Boolean,
    icon: ImageVector,
    label: String,
    onClick: () -> Unit
) {
    val bg = if (selected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val fg = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg
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
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MapModeItem("Nav", Icons.Default.NearMe, MapInteractionMode.NAVIGATE, mode, onModeChange)
                MapModeItem("Region", Icons.Default.CropFree, MapInteractionMode.SELECT_REGION, mode, onModeChange)
                MapModeItem("Anchor", Icons.Default.MyLocation, MapInteractionMode.SET_ANCHOR, mode, onModeChange)
                MapModeItem("Place", Icons.Default.Place, MapInteractionMode.PLACE_ASSET, mode, onModeChange)
                MapModeItem("Edit", Icons.Default.Edit, MapInteractionMode.EDIT, mode, onModeChange)
            }
        }

        if (mode == MapInteractionMode.SELECT_REGION) {
            Spacer(modifier = Modifier.height(6.dp))
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (hasRegion) "Region set" else "Pan to site, then confirm",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = onConfirmViewportRegion) {
                        Text("Use area", fontWeight = FontWeight.SemiBold)
                    }
                    if (hasRegion) {
                        TextButton(onClick = onClearRegion) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MapModeItem(
    label: String,
    icon: ImageVector,
    value: MapInteractionMode,
    selected: MapInteractionMode,
    onModeChange: (MapInteractionMode) -> Unit
) {
    val isSelected = selected == value
    val bg = if (isSelected) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.surface.copy(alpha = 0f)
    }
    val fg = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .clickable { onModeChange(value) }
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = fg,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
            color = fg
        )
    }
}
