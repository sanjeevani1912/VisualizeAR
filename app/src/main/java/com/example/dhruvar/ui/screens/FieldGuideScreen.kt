package com.example.dhruvar.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.PinDrop
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.SquareFoot
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.theme.Moss
import com.example.dhruvar.ui.theme.Rust

/**
 * In-app Field Guide (geo planning + AR). Content mirrors `docs/FieldGuide.md`.
 * Each step is a rounded card with an icon so it can be scanned quickly.
 */
@Composable
fun FieldGuideScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            AppTopBar(
                title = "Field Guide",
                onNavigateBack = onNavigateBack
            )
        },
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Moss),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "GEOGRAPHIC PLANNING & AR",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.2.sp,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Operate layouts against real-world coordinates, then visualize on site.",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "7 steps: find location, set region, origin, place assets, save, view in AR.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
                    )
                }
            }

            GuideSection(
                number = "1",
                icon = Icons.Default.GpsFixed,
                title = "Find a location",
                body = "New Plan finds your position from GNSS satellites and centers the map on it. Tap Detect to retry, or search for another place. Continue is always available."
            )
            GuideSection(
                number = "2",
                icon = Icons.Default.Crop,
                title = "Set region",
                body = "Drag a box or use the visible area, then confirm. The origin is placed at the center of that box."
            )
            GuideSection(
                number = "3",
                icon = Icons.Default.PinDrop,
                title = "Origin at the center",
                body = "Confirming the region places the origin at the center of the box. " +
                    "Change origin to tap a different point or stamp GPS. A point you chose stays put while it is still inside the box. " +
                    "If a new box leaves that point outside, the origin returns to the new center and assets stay on the same ground."
            )
            GuideSection(
                number = "4",
                icon = Icons.Default.Edit,
                title = "Place assets",
                body = "Navigate, Place, and Edit. Taps store (x, z) meters from the origin. " +
                    "Change region or Change origin does not delete assets."
            )
            GuideSection(
                number = "5",
                icon = Icons.Default.Save,
                title = "Save",
                body = "Toolbar Save writes schema v2 JSON (region, origin, meters). An unfinished plan can be saved and reopened at the missing step."
            )
            GuideSection(
                number = "6",
                icon = Icons.Default.ViewInAr,
                title = "On-site AR",
                body = "AR opens only after region and origin are set. GEO BRIEFING shows distance and bearing. " +
                    "Set Planning Origin on a detected plane. GPS does not drag meshes."
            )
            GuideSection(
                number = "7",
                icon = Icons.Default.SquareFoot,
                title = "GPS accuracy vs relative precision",
                body = "Horizontal accuracy is uncertainty of the stamped WGS84 origin. " +
                    "Distances between assets are planning meters and remain precise relative to that origin.",
                accentAmber = true
            )

            Text(
                text = "Full markdown: docs/FieldGuide.md",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Moss
                ),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 16.dp)
            )
        }
    }
}

@Composable
private fun GuideSection(
    number: String,
    icon: ImageVector,
    title: String,
    body: String,
    accentAmber: Boolean = false
) {
    val accent = if (accentAmber) Rust else Moss
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Step $number",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = accent
                    )
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
