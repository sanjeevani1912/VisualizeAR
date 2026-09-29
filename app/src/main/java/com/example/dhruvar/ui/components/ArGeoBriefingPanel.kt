package com.example.dhruvar.ui.components

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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.domain.ar.ArGeoGuidance
import com.example.dhruvar.domain.ar.ArGeoProximityStatus
import com.example.dhruvar.domain.ar.ArVisualizationScale
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.TacticalAmber
import com.example.dhruvar.ui.theme.TacticalGreen
import java.util.Locale

/**
 * Compact pre-AR / calibration briefing: saved geographic anchor + distance guidance.
 * GPS guides the user to the site; AR tracking owns local stability after origin set.
 */
@Composable
fun ArGeoBriefingPanel(
    guidance: ArGeoGuidance,
    modifier: Modifier = Modifier
) {
    val chipColor = when (guidance.status) {
        ArGeoProximityStatus.NEAR_ORIGIN -> TacticalGreen
        ArGeoProximityStatus.TOO_FAR -> TacticalAmber
        ArGeoProximityStatus.NO_DEVICE_FIX -> TacticalAmber
        ArGeoProximityStatus.NO_GEO -> Color(0xFF94A3B8)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xEE0F172A))
            .border(1.dp, chipColor.copy(alpha = 0.55f), RoundedCornerShape(10.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = PrecisionBlue,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "GEO BRIEFING",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp
                ),
                color = Color.White
            )
            Spacer(modifier = Modifier.weight(1f))
            StatusChip(label = guidance.statusLabel, color = chipColor)
        }

        Spacer(modifier = Modifier.height(8.dp))

        when (guidance.status) {
            ArGeoProximityStatus.NO_GEO -> {
                Text(
                    text = "Layout has no geographic anchor — local AR mode (meter offsets from placed origin).",
                    style = monoBody(),
                    color = Color(0xFFCBD5E1)
                )
            }
            else -> {
                val lat = guidance.anchorLatitude
                val lng = guidance.anchorLongitude
                if (lat != null && lng != null) {
                    Text(
                        text = String.format(Locale.US, "ANCHOR  Lat %.6f  Lng %.6f", lat, lng),
                        style = monoBody(),
                        color = Color(0xFFE2E8F0)
                    )
                }
                val heading = guidance.anchorHeadingDegrees
                if (heading != null) {
                    Text(
                        text = String.format(Locale.US, "HEADING  %.1f° (saved — preferred at calibrate)", heading),
                        style = monoBody(),
                        color = Color(0xFFCBD5E1)
                    )
                } else {
                    Text(
                        text = "HEADING  (use live compass at calibration)",
                        style = monoBody(),
                        color = Color(0xFF94A3B8)
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                when (guidance.status) {
                    ArGeoProximityStatus.NO_DEVICE_FIX -> Text(
                        text = "Enable location to see distance to origin. You may still calibrate.",
                        style = monoBody(),
                        color = TacticalAmber
                    )
                    ArGeoProximityStatus.TOO_FAR -> {
                        Text(
                            text = String.format(
                                Locale.US,
                                "Distance ≈ %.0f m  ·  bearing %.0f°  ·  warn >%.0f m",
                                guidance.distanceMeters ?: 0.0,
                                guidance.bearingDegrees ?: 0f,
                                guidance.nearOriginThresholdMeters
                            ),
                            style = monoBody(),
                            color = TacticalAmber
                        )
                        Text(
                            text = "TOO FAR — walk closer if possible; proceed allowed.",
                            style = monoBody(),
                            color = TacticalAmber
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
                        color = TacticalGreen
                    )
                    else -> Unit
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
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xCC0F172A))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "SCALE",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF94A3B8)
            )
        )
        ArVisualizationScale.entries.forEach { mode ->
            val active = mode == selected
            Text(
                text = mode.ratioLabel,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = if (active) Color.White else Color(0xFF94A3B8)
                ),
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (active) PrecisionBlue.copy(alpha = 0.35f) else Color.Transparent)
                    .border(
                        1.dp,
                        if (active) PrecisionBlue else Color(0xFF334155),
                        RoundedCornerShape(6.dp)
                    )
                    .clickable { onSelected(mode) }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun StatusChip(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.18f))
            .border(1.dp, color, RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 9.sp,
                fontFamily = FontFamily.Monospace,
                color = color
            )
        )
    }
}

@Composable
private fun monoBody() = MaterialTheme.typography.bodySmall.copy(
    fontFamily = FontFamily.Monospace,
    fontSize = 10.sp
)
