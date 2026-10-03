package com.example.dhruvar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.GeoOriginReadiness
import com.example.dhruvar.domain.location.LocationAvailability
import com.example.dhruvar.domain.model.Anchor
import com.example.dhruvar.ui.theme.Rust
import com.example.dhruvar.ui.theme.Moss
import com.example.dhruvar.viewmodel.PlanningViewModel
import java.util.Locale

/**
 * Compact geo-origin control: collapsed chip on the map; expands as an overlay sheet.
 */
@Composable
fun GeoOriginPanel(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    anchor: Anchor,
    locationState: DeviceLocationState,
    liveHeadingDegrees: Float,
    compassAvailable: Boolean,
    geoAccuracyWarning: String?,
    onRequestPermission: () -> Unit,
    onOpenAppSettings: (() -> Unit)? = null,
    permissionPermanentlyDenied: Boolean = false,
    onRefreshLocation: () -> Unit,
    onUseMyLocation: () -> Unit,
    onCaptureHeading: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readiness = locationState.readiness
    val chipLabel = originChipLabel(anchor.hasGeographicFix, readiness)

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 2.dp,
        shadowElevation = 3.dp,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Origin",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                StatusPill(text = chipLabel, readiness = readiness, stamped = anchor.hasGeographicFix)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse origin" else "Expand origin",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    StampedOriginSummary(anchor = anchor, liveHeadingDegrees = liveHeadingDegrees)
                    Spacer(modifier = Modifier.height(8.dp))
                    LiveFixSummary(
                        locationState = locationState,
                        liveHeadingDegrees = liveHeadingDegrees,
                        compassAvailable = compassAvailable
                    )

                    if (locationState.availability == LocationAvailability.PERMISSION_MISSING ||
                        !locationState.permissionGranted
                    ) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (permissionPermanentlyDenied) {
                                "Location permission permanently denied. Open settings to enable GNSS."
                            } else {
                                "Location permission is required to capture origin."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (permissionPermanentlyDenied && onOpenAppSettings != null) {
                            TextButton(onClick = onOpenAppSettings) {
                                Text("Open app settings", fontWeight = FontWeight.SemiBold)
                            }
                        } else {
                            TextButton(onClick = onRequestPermission) {
                                Text("Grant location permission", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    } else if (locationState.availability == LocationAvailability.GPS_DISABLED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = locationState.statusMessage
                                ?: "Location services disabled — enable GPS, then retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Rust
                        )
                        TextButton(onClick = onRefreshLocation) {
                            Text("Retry location", fontWeight = FontWeight.SemiBold)
                        }
                    } else if (locationState.availability == LocationAvailability.PROVIDER_ERROR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = locationState.statusMessage
                                ?: "Location provider error — retry or check Play Services.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Rust
                        )
                        TextButton(onClick = onRefreshLocation) {
                            Text("Retry location", fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if (geoAccuracyWarning != null || readiness == GeoOriginReadiness.ACCURACY_POOR) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = geoAccuracyWarning
                                ?: PlanningViewModel.SITE_LEVEL_ACCURACY_WARNING,
                            style = MaterialTheme.typography.bodySmall,
                            color = Rust
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onUseMyLocation,
                            enabled = locationState.hasFix && locationState.permissionGranted,
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use location", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onCaptureHeading,
                            enabled = compassAvailable,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Navigation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Heading", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onClear,
                            enabled = anchor.hasGeographicFix || anchor.headingDegrees != null,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(0.7f)
                        ) {
                            Icon(
                                Icons.Outlined.Clear,
                                contentDescription = "Clear geographic origin",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun originChipLabel(stamped: Boolean, readiness: GeoOriginReadiness): String {
    return when {
        stamped -> "Set"
        readiness == GeoOriginReadiness.READY -> "Ready"
        readiness == GeoOriginReadiness.ACCURACY_POOR -> "Poor GPS"
        else -> "No fix"
    }
}

@Composable
private fun StatusPill(text: String, readiness: GeoOriginReadiness, stamped: Boolean) {
    val color = when {
        stamped -> Moss
        readiness == GeoOriginReadiness.READY -> Moss
        readiness == GeoOriginReadiness.ACCURACY_POOR -> Rust
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
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
private fun StampedOriginSummary(anchor: Anchor, liveHeadingDegrees: Float) {
    Text(
        text = "Layout anchor",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    if (anchor.hasGeographicFix) {
        MonoLine(
            "Lat ${formatCoord(anchor.latitude!!)}   Lng ${formatCoord(anchor.longitude!!)}"
        )
        val acc = anchor.horizontalAccuracyMeters
        val heading = anchor.headingDegrees
        MonoLine(
            buildString {
                append("Acc ")
                append(if (acc != null) String.format(Locale.US, "±%.1f m", acc) else "—")
                append("   Hdg ")
                append(
                    if (heading != null) {
                        String.format(Locale.US, "%.1f°", heading)
                    } else {
                        String.format(Locale.US, "live %.1f°", liveHeadingDegrees)
                    }
                )
            }
        )
    } else {
        Text(
            text = "Not set — stamp from GNSS when Ready",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LiveFixSummary(
    locationState: DeviceLocationState,
    liveHeadingDegrees: Float,
    compassAvailable: Boolean
) {
    Text(
        text = "Device",
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(modifier = Modifier.height(4.dp))
    val fix = locationState.lastFix
    if (fix != null && fix.isFinite) {
        MonoLine("Lat ${formatCoord(fix.latitude)}   Lng ${formatCoord(fix.longitude)}")
        MonoLine(
            buildString {
                append("Acc ")
                append(
                    fix.horizontalAccuracyMeters?.let {
                        String.format(Locale.US, "±%.1f m", it)
                    } ?: "—"
                )
                append("   Hdg ")
                append(
                    if (compassAvailable) {
                        String.format(Locale.US, "%.1f°", liveHeadingDegrees)
                    } else {
                        "n/a"
                    }
                )
            }
        )
    } else {
        Text(
            text = locationState.statusMessage ?: "Waiting for GNSS fix…",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MonoLine(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall.copy(
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp
        ),
        color = MaterialTheme.colorScheme.onSurface
    )
}

private fun formatCoord(value: Double): String =
    String.format(Locale.US, "%.6f", value)
