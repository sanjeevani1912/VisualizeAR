package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.NearMe
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.MapInteractionMode
import com.example.dhruvar.domain.model.PlaceSearchHit
import com.example.dhruvar.domain.model.PlanningSetupStep
import com.example.dhruvar.domain.model.PlanningSurface
import com.example.dhruvar.viewmodel.PlaceSearchUiState

/**
 * Which planning HUD controls are visible. Derived state, kept separate from the composables so
 * the visibility matrix can be asserted in plain unit tests.
 */
data class PlanningHudTools(
    val showSearch: Boolean,
    val showOriginChip: Boolean,
    val showSurfaceSwitcher: Boolean,
    val showModeRail: Boolean,
    val showRegionTools: Boolean,
    val showAnchorHint: Boolean,
    val showPlaceHint: Boolean,
    val showEditHint: Boolean,
    val showAssetPicker: Boolean = false,
    val showArAction: Boolean = false,
    val showSetupContinue: Boolean = false
) {
    /** True when a contextual strip sits above the mode rail and needs vertical room. */
    val hasModeContext: Boolean
        get() = showRegionTools || showAnchorHint || showPlaceHint || showEditHint
}

private val NOTHING = PlanningHudTools(
    showSearch = false,
    showOriginChip = false,
    showSurfaceSwitcher = false,
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
 * @param isSearchExpanded gives the search field the whole top row, since the field plus its
 *   results need the width that the chip and switcher would otherwise take.
 * @param setupStep hides chrome the current guided-setup step does not own. Defaults to
 *   [PlanningSetupStep.PLACE_ASSETS] so existing mode-only call sites keep today's HUD.
 * @param hasGeographicFix shows SET_ORIGIN Continue when an origin already exists.
 */
fun planningHudToolsFor(
    surface: PlanningSurface,
    mode: MapInteractionMode,
    hasSelection: Boolean = false,
    isEditorOpen: Boolean = false,
    isOriginExpanded: Boolean = false,
    isSearchExpanded: Boolean = false,
    setupStep: PlanningSetupStep = PlanningSetupStep.PLACE_ASSETS,
    hasGeographicFix: Boolean = false
): PlanningHudTools {
    val isMap = surface == PlanningSurface.MAP
    when (setupStep) {
        PlanningSetupStep.FIND_LOCATION -> {
            if (!isMap) return NOTHING
            // Search stays the only chrome; the field is composed expanded by the screen.
            return NOTHING.copy(showSearch = true, showSetupContinue = true)
        }
        PlanningSetupStep.SET_REGION -> {
            return NOTHING.copy(showRegionTools = isMap)
        }
        PlanningSetupStep.SET_ORIGIN -> {
            return NOTHING.copy(
                showOriginChip = true,
                showAnchorHint = isMap && mode == MapInteractionMode.SET_ANCHOR,
                showSetupContinue = hasGeographicFix && mode != MapInteractionMode.SET_ANCHOR
            )
        }
        PlanningSetupStep.PLACE_ASSETS -> Unit
    }

    val workspace = NOTHING.copy(showAssetPicker = true, showArAction = true)
    if (isMap && isSearchExpanded) return workspace.copy(showModeRail = true)

    // The grid keeps the switcher — it is the only way back to the map.
    val topRow = workspace.copy(
        showSearch = isMap,
        showOriginChip = true,
        showSurfaceSwitcher = true
    )
    if (!isMap || isEditorOpen) return topRow
    if (isOriginExpanded) return topRow.copy(showModeRail = true)
    return topRow.copy(
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
    regionDraftLabel: String?,
    isRegionDraftActive: Boolean,
    isRegionDraftCommittable: Boolean,
    onConfirmDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onConfirmViewportRegion: () -> Unit,
    onClearRegion: () -> Unit,
    modifier: Modifier = Modifier,
    guidedSetup: Boolean = false,
    onCancelAnchorTap: (() -> Unit)? = null
) {
    when {
        tools.showRegionTools -> RegionTools(
            hasRegion = hasRegion,
            draftLabel = regionDraftLabel,
            isDraftActive = isRegionDraftActive,
            isDraftCommittable = isRegionDraftCommittable,
            onConfirmDraft = onConfirmDraft,
            onCancelDraft = onCancelDraft,
            onConfirmViewportRegion = onConfirmViewportRegion,
            onClearRegion = onClearRegion,
            guidedSetup = guidedSetup,
            modifier = modifier
        )

        tools.showAnchorHint -> HudHint(
            text = "Tap inside the region to set origin",
            modifier = modifier,
            onCancel = onCancelAnchorTap
        )
        tools.showPlaceHint -> HudHint("Tap the map to drop a $selectedAssetName", modifier)
        tools.showEditHint -> HudHint("Drag an asset to move it", modifier)
    }
}

/**
 * Drag-a-box is the primary path, so the actions on offer switch to Confirm and Cancel the moment
 * a draft exists. Confirming the whole viewport stays available as the quick fallback.
 */
@Composable
private fun RegionTools(
    hasRegion: Boolean,
    draftLabel: String?,
    isDraftActive: Boolean,
    isDraftCommittable: Boolean,
    onConfirmDraft: () -> Unit,
    onCancelDraft: () -> Unit,
    onConfirmViewportRegion: () -> Unit,
    onClearRegion: () -> Unit,
    modifier: Modifier = Modifier,
    guidedSetup: Boolean = false
) {
    val hint = draftLabel ?: when {
        guidedSetup && hasRegion -> "Region set · drag a new box to replace it"
        guidedSetup -> "Drag a box on the map, or use the visible area."
        hasRegion -> "Region set · drag a new box to replace it"
        else -> "Drag a box on the map"
    }
    val showConfirm = if (guidedSetup) {
        isDraftCommittable || (hasRegion && !isDraftActive)
    } else {
        isDraftActive
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showConfirm) {
                    TextButton(
                        onClick = onConfirmDraft,
                        enabled = isDraftCommittable || (guidedSetup && hasRegion && !isDraftActive)
                    ) {
                        Text(
                            text = when {
                                isDraftActive -> if (guidedSetup) "Confirm region" else "Confirm"
                                guidedSetup && hasRegion -> "Continue with current region"
                                else -> "Confirm"
                            },
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                if (isDraftActive) {
                    TextButton(onClick = onCancelDraft) {
                        Text("Cancel")
                    }
                }
                TextButton(onClick = onConfirmViewportRegion) {
                    Text(
                        "Use visible area",
                        fontWeight = if (showConfirm) FontWeight.Normal else FontWeight.SemiBold
                    )
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

@Composable
private fun HudHint(
    text: String,
    modifier: Modifier = Modifier,
    onCancel: (() -> Unit)? = null
) {
    HudStrip(modifier) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 7.dp)
        )
        if (onCancel != null) {
            TextButton(onClick = onCancel) {
                Text("Cancel")
            }
        }
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

/** Collapsed search affordance in the top HUD row. */
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

/**
 * Expanded place search: field plus results, taking the whole top row.
 *
 * The result list is height-capped so the lower map stays visible and usable — you need to see
 * where you are flying to.
 */
@Composable
fun PlanningSearchField(
    state: PlaceSearchUiState,
    onQueryChange: (String) -> Unit,
    onCollapse: () -> Unit,
    onHitSelected: (PlaceSearchHit) -> Unit,
    modifier: Modifier = Modifier,
    showCollapseControl: Boolean = true
) {
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
            tonalElevation = 3.dp,
            shadowElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (showCollapseControl) {
                    IconButton(onClick = onCollapse) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(20.dp)
                    )
                }
                BasicTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester),
                    decorationBox = { field ->
                        if (state.query.isEmpty()) {
                            Text(
                                // Street names are not indexed and Latin script matches best.
                                text = "Search place (English)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        field()
                    }
                )
                if (state.isSearching) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 12.dp)
                            .size(18.dp)
                    )
                } else if (state.query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear query",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        val message = state.error ?: "No matches".takeIf { state.isEmptyResult }
        if (message != null) {
            HudStrip(Modifier.padding(top = 6.dp)) {
                Text(
                    text = message,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (state.error != null) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(vertical = 7.dp)
                )
            }
        }

        if (state.results.isNotEmpty()) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
                tonalElevation = 3.dp,
                shadowElevation = 4.dp,
                modifier = Modifier
                    .padding(top = 6.dp)
                    .fillMaxWidth()
            ) {
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(state.results, key = { it.id }) { hit ->
                        PlaceSearchRow(hit = hit, onClick = { onHitSelected(hit) })
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceSearchRow(hit: PlaceSearchHit, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Place,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.padding(start = 10.dp)) {
            Text(
                text = hit.displayName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            hit.displayCategory?.let { category ->
                Text(
                    text = category,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun SelectedPlaceChip(
    displayName: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        modifier = modifier.semantics { contentDescription = "Selected: $displayName" }
    ) {
        Text(
            text = "Selected: $displayName",
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

@Composable
fun FindLocationContinueBar(
    enabled: Boolean,
    onContinue: () -> Unit,
    modifier: Modifier = Modifier,
    buttonText: String = "Continue",
    disabledHint: String? = "Search and select a place to continue"
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (!enabled && disabledHint != null) {
            Text(
                text = disabledHint,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        PrimaryButton(
            text = buttonText,
            onClick = onContinue,
            enabled = enabled
        )
    }
}
