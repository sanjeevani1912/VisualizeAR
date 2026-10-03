package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlanningSurface

/**
 * Which planning HUD controls are visible. Derived state, kept separate from the composables so
 * the visibility matrix can be asserted in plain unit tests.
 */
data class PlanningHudTools(
    val showSearch: Boolean,
    val showModeRail: Boolean,
    val showRegionTools: Boolean,
    val showAnchorHint: Boolean,
    val showPlaceHint: Boolean,
    val showEditHint: Boolean
) {
    /** True when a contextual strip sits above the mode rail and needs vertical room. */
    val hasModeContext: Boolean
        get() = showRegionTools || showAnchorHint || showPlaceHint || showEditHint
}

private val NOTHING = PlanningHudTools(
    showSearch = false,
    showModeRail = false,
    showRegionTools = false,
    showAnchorHint = false,
    showPlaceHint = false,
    showEditHint = false
)

/**
 * Contextual HUD rule: only the active mode's extra tools are on screen.
 *
 * The metric grid surface shows no mode rail and no search at all. Every [MapInteractionMode]
 * describes what a tap on the *map* does, and the grid is manipulated directly instead — drag to
 * move, tap to select — so a rail there would be five controls that change nothing. Place search
 * likewise moves the map camera, which the grid does not have.
 *
 * @param hasSelection suppresses the edit hint, because the selection itself is the feedback.
 * @param isEditorOpen clears the bottom edge for [SelectedObjectEditorOverlay], which is tall
 *   enough to cover the rail. Minimising or deselecting brings the rail back.
 * @param isOriginExpanded suppresses mode context while the origin sheet is open over the map.
 */
fun planningHudToolsFor(
    surface: PlanningSurface,
    mode: MapInteractionMode,
    hasSelection: Boolean = false,
    isEditorOpen: Boolean = false,
    isOriginExpanded: Boolean = false
): PlanningHudTools {
    if (surface != PlanningSurface.MAP) return NOTHING
    if (isEditorOpen) return NOTHING.copy(showSearch = true)
    if (isOriginExpanded) return NOTHING.copy(showSearch = true, showModeRail = true)
    return NOTHING.copy(
        showSearch = true,
        showModeRail = true,
        showRegionTools = mode == MapInteractionMode.SELECT_REGION,
        showAnchorHint = mode == MapInteractionMode.SET_ANCHOR,
        showPlaceHint = mode == MapInteractionMode.PLACE_ASSET,
        showEditHint = mode == MapInteractionMode.EDIT && !hasSelection
    )
}

/**
 * Icon-only mode selector. Sized to fit all five modes on a compact phone without horizontal
 * scrolling, which is why the labels from the old wide toolbar are gone.
 */
@Composable
fun PlanningModeRail(
    mode: MapInteractionMode,
    onModeChange: (MapInteractionMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ModeIcon("Navigate", Icons.Default.NearMe, MapInteractionMode.NAVIGATE, mode, onModeChange)
            ModeIcon("Region", Icons.Default.CropFree, MapInteractionMode.SELECT_REGION, mode, onModeChange)
            ModeIcon("Anchor", Icons.Default.MyLocation, MapInteractionMode.SET_ANCHOR, mode, onModeChange)
            ModeIcon("Place", Icons.Default.Place, MapInteractionMode.PLACE_ASSET, mode, onModeChange)
            ModeIcon("Edit", Icons.Default.Edit, MapInteractionMode.EDIT, mode, onModeChange)
        }
    }
}

@Composable
private fun ModeIcon(
    label: String,
    icon: ImageVector,
    value: MapInteractionMode,
    selected: MapInteractionMode,
    onModeChange: (MapInteractionMode) -> Unit
) {
    val isSelected = selected == value
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(
                if (isSelected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0f)
                }
            )
            .clickable { onModeChange(value) }
            .semantics { contentDescription = "$label mode" }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The single contextual strip for the active mode. Region keeps the viewport actions; the other
 * modes only need to say what a tap will do.
 */
@Composable
fun PlanningModeContext(
    tools: PlanningHudTools,
    hasRegion: Boolean,
    selectedAssetName: String,
    onConfirmViewportRegion: () -> Unit,
    onClearRegion: () -> Unit,
    modifier: Modifier = Modifier
) {
    when {
        tools.showRegionTools -> HudStrip(modifier) {
            Text(
                text = if (hasRegion) "Region set" else "Pan to the site, then confirm",
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

        tools.showAnchorHint -> HudHint("Tap the map to set the origin", modifier)
        tools.showPlaceHint -> HudHint("Tap the map to drop a $selectedAssetName", modifier)
        tools.showEditHint -> HudHint("Tap an asset to select it", modifier)
    }
}

@Composable
private fun HudHint(text: String, modifier: Modifier = Modifier) {
    HudStrip(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 7.dp)
        )
    }
}

@Composable
private fun HudStrip(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

/**
 * Top-of-map search control. UX-03 expands this into the live place-search field against the map
 * server; the position is fixed now so it does not move once it gains behaviour.
 */
@Composable
fun PlanningSearchButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = "Search places" }
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(10.dp)
                .size(20.dp)
        )
    }
}
