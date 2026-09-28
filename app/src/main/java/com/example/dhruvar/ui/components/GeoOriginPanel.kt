package com.example.dhruvar.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.TacticalAmber
import com.example.dhruvar.ui.theme.TacticalGreen
import com.example.dhruvar.viewmodel.PlanningViewModel
import java.util.Locale

/**
 * Compact expandable Geo Origin panel for stamping GNSS + compass onto the layout anchor.
 * Collapsed by default so it does not clutter canvas interaction.
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
    onRefreshLocation: () -> Unit,
    onUseMyLocation: () -> Unit,
    onCaptureHeading: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    val readiness = locationState.readiness
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(10.dp),
        tonalElevation = 2.dp,
        shadowElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onExpandedChange(!expanded) },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    tint = PrecisionBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "GEO ORIGIN",
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(8.dp))
                ReadinessChip(readiness = readiness, hasStampedFix = anchor.hasGeographicFix)
                Spacer(modifier = Modifier.weight(1f))
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (expanded) "Collapse geo origin" else "Expand geo origin",
                    tint = MaterialTheme.colorScheme.secondary
                )
            }

            AnimatedVisibility(visible = expanded) {
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
                            text = "Location permission is required to capture GNSS origin.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        TextButton(onClick = onRequestPermission) {
                            Text("Grant location permission", fontWeight = FontWeight.SemiBold)
                        }
                    } else if (locationState.availability == LocationAvailability.GPS_DISABLED) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = locationState.statusMessage
                                ?: "Enable device location services, then retry.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TacticalAmber
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
                            color = TacticalAmber
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
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrecisionBlue),
                            modifier = Modifier.weight(1.2f)
                        ) {
                            Icon(
                                Icons.Default.MyLocation,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Use my location", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onCaptureHeading,
                            enabled = compassAvailable,
                            shape = RoundedCornerShape(8.dp),
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
                            shape = RoundedCornerShape(8.dp),
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

@Composable
private fun ReadinessChip(readiness: GeoOriginReadiness, hasStampedFix: Boolean) {
    val (label, color) = when {
        !hasStampedFix && readiness == GeoOriginReadiness.NO_FIX ->
            "NO FIX" to MaterialTheme.colorScheme.secondary
        readiness == GeoOriginReadiness.NO_FIX ->
            "NO FIX" to MaterialTheme.colorScheme.secondary
        readiness == GeoOriginReadiness.ACCURACY_POOR ->
            "ACCURACY POOR (>15m)" to TacticalAmber
        else ->
            "READY" to TacticalGreen
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                color = color
            )
        )
    }
}

@Composable
private fun StampedOriginSummary(anchor: Anchor, liveHeadingDegrees: Float) {
    Text(
        text = "LAYOUT ANCHOR",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.secondary
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
            text = "Not set — stamp from GNSS when READY",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.secondary
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
        text = "DEVICE",
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.secondary
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
            color = MaterialTheme.colorScheme.secondary
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
