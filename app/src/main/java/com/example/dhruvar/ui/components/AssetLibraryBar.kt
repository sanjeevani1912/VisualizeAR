package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cabin
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.model.AssetType
import com.example.dhruvar.ui.theme.PrecisionBlue

/**
 * Maps each domain [AssetType] to a representative Material icon.
 */
fun AssetType.toIcon(): ImageVector {
    return when (this) {
        AssetType.TENT -> Icons.Default.Cabin
        AssetType.TRUCK -> Icons.Default.LocalShipping
        AssetType.CAR -> Icons.Default.DirectionsCar
        AssetType.ANTENNA -> Icons.Default.SettingsInputAntenna
        AssetType.TRENCH -> Icons.Default.Straighten
    }
}

/**
 * Bottom docked Asset Library bar allowing the user to select asset templates.
 */
@Composable
fun AssetLibraryBar(
    selectedAssetType: AssetType,
    onAssetSelected: (AssetType) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ASSET LIBRARY",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            )
            Text(
                text = "Tap asset to select tool",
                style = MaterialTheme.typography.labelSmall.copy(
                    color = MaterialTheme.colorScheme.outline
                )
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(AssetType.entries) { asset ->
                val isSelected = (asset == selectedAssetType)
                AssetChip(
                    assetType = asset,
                    isSelected = isSelected,
                    onClick = { onAssetSelected(asset) }
                )
            }
        }
    }
}

@Composable
private fun AssetChip(
    assetType: AssetType,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (isSelected) PrecisionBlue else MaterialTheme.colorScheme.outline
    val bgColor = if (isSelected) {
        PrecisionBlue.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(76.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 6.dp)
    ) {
        Icon(
            imageVector = assetType.toIcon(),
            contentDescription = assetType.displayName,
            tint = if (isSelected) PrecisionBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = assetType.displayName,
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) PrecisionBlue else MaterialTheme.colorScheme.onSurface
            ),
            maxLines = 1
        )
        val spec = assetType.defaultSpecification
        val dimText = if (spec.widthMeters % 1f == 0f && spec.lengthMeters % 1f == 0f) {
            "${spec.widthMeters.toInt()}×${spec.lengthMeters.toInt()}m"
        } else {
            "${spec.widthMeters}×${spec.lengthMeters}m"
        }
        Text(
            text = dimText,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                color = MaterialTheme.colorScheme.secondary
            )
        )
    }
}
