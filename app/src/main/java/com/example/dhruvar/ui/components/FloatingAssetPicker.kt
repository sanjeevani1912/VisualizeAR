package com.example.dhruvar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.model.AssetType

/**
 * Bottom-right FAB that toggles a translucent floating asset picker over the planning canvas.
 *
 * When closed: only the FAB column is composed (aligned BottomEnd) so canvas drag/pan works.
 * When open: a full-screen scrim intercepts outside taps to dismiss.
 */
@Composable
fun BoxScope.FloatingAssetPickerOverlay(
    isOpen: Boolean,
    onToggle: () -> Unit,
    onAssetChosen: (AssetType) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            .align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = 12.dp),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AnimatedVisibility(
            visible = isOpen,
            enter = fadeIn(tween(180)) +
                scaleIn(initialScale = 0.92f, animationSpec = tween(180)) +
                slideInVertically(tween(180)) { it / 6 },
            exit = fadeOut(tween(120)) +
                scaleOut(targetScale = 0.94f, animationSpec = tween(120)) +
                slideOutVertically(tween(120)) { it / 8 }
        ) {
            FloatingAssetMenuPanel(
                onAssetChosen = onAssetChosen,
                modifier = Modifier
                    .widthIn(min = 200.dp, max = 280.dp)
                    .heightIn(max = 360.dp)
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
private fun FloatingAssetMenuPanel(
    onAssetChosen: (AssetType) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Add asset",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                contentPadding = PaddingValues(bottom = 4.dp)
            ) {
                items(AssetType.entries.toList(), key = { it.name }) { type ->
                    AssetMenuRow(
                        assetType = type,
                        onClick = { onAssetChosen(type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun AssetMenuRow(
    assetType: AssetType,
    onClick: () -> Unit
) {
    val dims = "${assetType.defaultWidthMeters}×${assetType.defaultLengthMeters} m"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .semantics { contentDescription = "Add ${assetType.displayName}" },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = assetType.displayName.take(1),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = assetType.displayName,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = dims,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
