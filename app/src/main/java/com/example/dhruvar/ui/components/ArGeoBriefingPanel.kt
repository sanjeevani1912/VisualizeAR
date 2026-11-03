package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.ar.ArGeoGuidance
import com.example.dhruvar.domain.ar.ArGeoProximityStatus
import com.example.dhruvar.domain.ar.ArAssetSize
import com.example.dhruvar.domain.ar.ArVisualizationScale
import com.example.dhruvar.ui.theme.Rust
import com.example.dhruvar.ui.theme.Moss
import java.util.Locale

/**
 * Compact pre-AR / calibration briefing: saved geographic anchor + distance guidance.
 */
@Composable
fun ArGeoBriefingPanel(
    guidance: ArGeoGuidance,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val chipColor = when (guidance.status) {
        ArGeoProximityStatus.NEAR_ORIGIN -> Moss
        ArGeoProximityStatus.TOO_FAR -> Rust
        ArGeoProximityStatus.NO_DEVICE_FIX -> Rust
        ArGeoProximityStatus.NO_GEO -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) }
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    tint = chipColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = guidance.compactChipLabel(),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse geo briefing" else "Expand geo briefing",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (!expanded) return@Column

            Spacer(modifier = Modifier.height(8.dp))
            StatusChip(label = guidance.statusLabel, color = chipColor)
            Spacer(modifier = Modifier.height(8.dp))

            when (guidance.status) {
                ArGeoProximityStatus.NO_GEO -> {
                    Text(
                        text = "No geographic anchor — local AR mode (meter offsets from placed origin).",
                        style = monoBody(),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    val lat = guidance.anchorLatitude
                    val lng = guidance.anchorLongitude
                    if (lat != null && lng != null) {
                        Text(
                            text = String.format(Locale.US, "Anchor  Lat %.6f  Lng %.6f", lat, lng),
                            style = monoBody(),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    val heading = guidance.anchorHeadingDegrees
                    if (heading != null) {
                        Text(
                            text = String.format(Locale.US, "Heading  %.1f° (saved)", heading),
                            style = monoBody(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Text(
                            text = "Heading  (use live compass at calibration)",
                            style = monoBody(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    when (guidance.status) {
                        ArGeoProximityStatus.NO_DEVICE_FIX -> Text(
                            text = "Enable location to see distance to origin. You may still calibrate.",
                            style = monoBody(),
                            color = Rust
                        )
                        ArGeoProximityStatus.TOO_FAR -> {
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Distance ≈ %.0f m  ·  bearing %.0f°",
                                    guidance.distanceMeters ?: 0.0,
                                    guidance.bearingDegrees ?: 0f
                                ),
                                style = monoBody(),
                                color = Rust
                            )
                            Text(
                                text = "Too far — walk closer if possible; proceed allowed.",
                                style = monoBody(),
                                color = Rust
                            )
                        }
                        ArGeoProximityStatus.NEAR_ORIGIN -> Text(
                            text = String.format(
                                Locale.US,
                                "Distance ≈ %.1f m  ·  bearing %.0f°  ·  acc %s",
                                guidance.distanceMeters ?: 0.0,
                                guidance.bearingDegrees ?: 0f,
                                guidance.deviceAccuracyMeters?.let {
                                    String.format(Locale.US, "±%.0f m", it)
                                } ?: "—"
                            ),
                            style = monoBody(),
                            color = Moss
                        )
                        else -> Unit
                    }
                }
            }
        }
    }
}

@Composable
fun ArScaleModeRow(
    selected: ArVisualizationScale,
    onSelected: (ArVisualizationScale) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Scale",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            ArVisualizationScale.entries.forEach { mode ->
                val active = mode == selected
                Text(
                    text = mode.ratioLabel,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    ),
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (active) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            }
                        )
                        .clickable { onSelected(mode) }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun ArAssetSizeRow(
    factor: Float,
    onFactorChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Asset size",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            AssetSizeStepButton(
                icon = Icons.Default.Remove,
                description = "Decrease asset size",
                enabled = factor > ArAssetSize.MIN,
                onClick = { onFactorChange(ArAssetSize.step(factor, increase = false)) }
            )
            Text(
                text = ArAssetSize.label(factor),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(44.dp),
                textAlign = TextAlign.Center
            )
            AssetSizeStepButton(
                icon = Icons.Default.Add,
                description = "Increase asset size",
                enabled = factor < ArAssetSize.MAX,
                onClick = { onFactorChange(ArAssetSize.step(factor, increase = true)) }
            )
        }
    }
}

@Composable
private fun AssetSizeStepButton(
    icon: ImageVector,
    description: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(
                MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 0.16f else 0.06f)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .semantics { contentDescription = description }
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun StatusChip(label: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        color = color,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    )
}

@Composable
private fun monoBody() = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 11.sp
)
