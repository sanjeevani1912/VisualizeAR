package com.example.dhruvar.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.theme.Moss
import com.example.dhruvar.ui.theme.Rust

/**
 * In-app Field Guide (geo planning + AR). Content mirrors `docs/FieldGuide.md`.
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
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "GEOGRAPHIC PLANNING & AR",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                    color = MaterialTheme.colorScheme.secondary
                )
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Operate layouts against real-world coordinates, then visualize on site.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            GuideSection(
                number = "01",
                title = "Find a location",
                body = "New Plan opens on search. Select a result, then Continue. Panning alone does not advance."
            )
            GuideSection(
                number = "02",
                title = "Set region",
                body = "Drag a box or use the visible area, then confirm. The origin is placed at the center of that box."
            )
            GuideSection(
                number = "03",
                title = "Origin at the center",
                body = "Confirming the region places the origin at the center of the box. " +
                    "Change origin to tap a different point or stamp GPS. A point you chose stays put while it is still inside the box. " +
                    "If a new box leaves that point outside, the origin returns to the new center and assets stay on the same ground."
            )
            GuideSection(
                number = "04",
                title = "Place assets",
                body = "Navigate, Place, and Edit. Taps store (x, z) meters from the origin. " +
                    "Change region or Change origin does not delete assets."
            )
            GuideSection(
                number = "05",
                title = "Save",
                body = "Toolbar Save writes schema v2 JSON (region, origin, meters). An unfinished plan can be saved and reopened at the missing step."
            )
            GuideSection(
                number = "06",
                title = "On-site AR",
                body = "AR opens only after region and origin are set. GEO BRIEFING shows distance and bearing. " +
                    "Set Planning Origin on a detected plane. GPS does not drag meshes."
            )
            GuideSection(
                number = "07",
                title = "GPS accuracy vs relative precision",
                body = "Horizontal accuracy is uncertainty of the stamped WGS84 origin. " +
                    "Distances between assets are planning meters and remain precise relative to that origin.",
                accentAmber = true
            )

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Full markdown: docs/FieldGuide.md",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontFamily = FontFamily.Monospace,
                    color = Moss
                )
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun GuideSection(
    number: String,
    title: String,
    body: String,
    accentAmber: Boolean = false
) {
    Spacer(modifier = Modifier.height(18.dp))
    Text(
        text = "$number  ${title.uppercase()}",
        style = MaterialTheme.typography.titleSmall.copy(
            fontWeight = FontWeight.Bold,
            color = if (accentAmber) Rust else Moss
        ),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(modifier = Modifier.height(6.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
