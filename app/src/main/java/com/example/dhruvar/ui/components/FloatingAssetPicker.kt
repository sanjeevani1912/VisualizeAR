package com.example.dhruvar.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.AssetCatalog
import com.example.dhruvar.domain.model.AssetCatalogEntry
import com.example.dhruvar.domain.model.AssetCategory

/**
 * Add button that opens a drill-down asset drawer over the planning map.
 *
 * The first page is category tiles. A tile opens that category's assets, with
 * a name search. Back returns to the tiles. Choosing an asset reports the entry
 * and the caller closes the drawer.
 */
@Composable
fun BoxScope.FloatingAssetPickerOverlay(
    isOpen: Boolean,
    onToggle: () -> Unit,
    onAssetChosen: (AssetCatalogEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var drilledCategory by remember { mutableStateOf<AssetCategory?>(null) }
    var query by remember { mutableStateOf("") }

    LaunchedEffect(isOpen) {
        if (!isOpen) {
            drilledCategory = null
            query = ""
        }
    }

    if (isOpen) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss
                )
                .semantics { contentDescription = "Dismiss asset menu" }
        )
    }

    Column(
        modifier = modifier
            .align(Alignment.BottomCenter)
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(180)) +
                scaleIn(initialScale = 0.96f, animationSpec = tween(180)) +
                slideInVertically(tween(180)) { it / 6 },
            exit = fadeOut(tween(120)) +
                scaleOut(targetScale = 0.98f, animationSpec = tween(120)) +
                slideOutVertically(tween(120)) { it / 8 }
        ) {
            AssetDrillDownSheet(
                category = drilledCategory,
                query = query,
                onQueryChange = { query = it },
                onCategorySelected = {
                    query = ""
                    drilledCategory = it
                },
                onBack = {
                    query = ""
                    drilledCategory = null
                },
                onAssetChosen = onAssetChosen,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            )
        }

        FloatingActionButton(
            onClick = onToggle,
            shape = CircleShape,
            containerColor = if (isOpen) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.primary
            },
            contentColor = if (isOpen) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onPrimary
            },
            elevation = FloatingActionButtonDefaults.elevation(
                defaultElevation = 4.dp,
                pressedElevation = 6.dp
            ),
            modifier = Modifier
                .size(56.dp)
                .semantics {
                    contentDescription = if (isOpen) "Close asset menu" else "Add asset"
                }
        ) {
            Icon(
                imageVector = if (isOpen) Icons.Default.Close else Icons.Default.Add,
                contentDescription = null,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

@Composable
private fun AssetDrillDownSheet(
    category: AssetCategory?,
    query: String,
    onQueryChange: (String) -> Unit,
    onCategorySelected: (AssetCategory) -> Unit,
    onBack: () -> Unit,
    onAssetChosen: (AssetCatalogEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
        ),
        tonalElevation = 3.dp,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            DrillHeader(
                category = category,
                onBack = onBack
            )
            AnimatedContent(
                targetState = category,
                transitionSpec = {
                    if (targetState != null) {
                        (slideInHorizontally { it / 4 } + fadeIn()).togetherWith(
                            slideOutHorizontally { -it / 4 } + fadeOut()
                        )
                    } else {
                        (slideInHorizontally { -it / 4 } + fadeIn()).togetherWith(
                            slideOutHorizontally { it / 4 } + fadeOut()
                        )
                    }
                },
                label = "asset-drill"
            ) { drilled ->
                if (drilled == null) {
                    CategoryTileGrid(onCategorySelected = onCategorySelected)
                } else {
                    CategoryAssetList(
                        category = drilled,
                        query = query,
                        onQueryChange = onQueryChange,
                        onAssetChosen = onAssetChosen
                    )
                }
            }
        }
    }
}

@Composable
private fun DrillHeader(
    category: AssetCategory?,
    onBack: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (category != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.semantics { contentDescription = "Back to categories" }
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            text = category?.displayName ?: "Assets",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun CategoryTileGrid(
    onCategorySelected: (AssetCategory) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssetCatalog.categories.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { category ->
                    CategoryTile(
                        category = category,
                        onClick = { onCategorySelected(category) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun CategoryTile(
    category: AssetCategory,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val count = AssetCatalog.count(category)
    Column(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 12.dp)
            .semantics { contentDescription = "${category.displayName}, $count assets" },
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = category.icon(),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = category.displayName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (count == 1) "1 asset" else "$count assets",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryAssetList(
    category: AssetCategory,
    query: String,
    onQueryChange: (String) -> Unit,
    onAssetChosen: (AssetCatalogEntry) -> Unit
) {
    val matches = AssetCatalog.inCategory(category, query)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AssetSearchField(
            query = query,
            onQueryChange = onQueryChange,
            categoryName = category.displayName
        )
        if (matches.isEmpty()) {
            Text(
                text = "No asset matches that name.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(matches, key = { it.id }) { entry ->
                    AssetDrillRow(
                        entry = entry,
                        onClick = { onAssetChosen(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    categoryName: String
) {
    val textColor = MaterialTheme.colorScheme.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                shape = RoundedCornerShape(10.dp)
            )
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Search,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = textColor),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier
                .weight(1f)
                .semantics { contentDescription = "Search $categoryName" },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) {
                        Text(
                            text = categoryName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    inner()
                }
            }
        )
    }
}

@Composable
private fun AssetDrillRow(
    entry: AssetCatalogEntry,
    onClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 12.dp)
                .semantics { contentDescription = "Add ${entry.displayName}" },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = entry.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = entry.footprintLabel(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    }
}

private fun AssetCategory.icon(): ImageVector = when (this) {
    AssetCategory.AIRCRAFT -> Icons.Default.Flight
    AssetCategory.VEHICLES -> Icons.Default.LocalShipping
    AssetCategory.SYSTEMS -> Icons.Default.Shield
}
