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
import com.example.dhruvar.ui.theme.PrecisionBlue
import com.example.dhruvar.ui.theme.TacticalAmber

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
                title = "Set geographic origin",
                body = "Expand GEO ORIGIN → grant location → Use my location + Heading. " +
                    "ACCURACY POOR (>15 m) is site-level only; relative meter placement stays precise. " +
                    "If permission is permanently denied, open app settings and re-enable Location."
            )
            GuideSection(
                number = "02",
                title = "Select region on map",
                body = "Mode Region → pan/zoom → Use visible area. Region is optional: " +
                    "without bounds, placement is site-wide; with bounds, outside taps are rejected."
            )
            GuideSection(
                number = "03",
                title = "Place assets",
                body = "Geographic origin required for map Place. Taps store (x, z) meters relative to the anchor. " +
                    "Use Metric grid for fine relative edits — same layout document."
            )
            GuideSection(
                number = "04",
                title = "Save",
                body = "Toolbar Save writes schema v2 JSON (geo + meters). Legacy v1 files still load."
            )
            GuideSection(
                number = "05",
                title = "On-site AR",
                body = "GEO BRIEFING shows distance/bearing. Set Planning Origin on a detected plane " +
                    "(prefers saved heading). GPS does not drag meshes — AR tracking owns the session. " +
                    "Layouts without geo use local-only mode."
            )
            GuideSection(
                number = "06",
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
                    color = PrecisionBlue
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
            color = if (accentAmber) TacticalAmber else PrecisionBlue
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
