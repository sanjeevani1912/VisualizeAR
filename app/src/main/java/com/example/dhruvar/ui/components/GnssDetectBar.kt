package com.example.dhruvar.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.example.dhruvar.domain.location.DeviceLocationState
import com.example.dhruvar.domain.location.LocationAvailability

/** One-line summary of the satellite positioning state, shown beside the Detect button. */
fun gnssStatusText(state: DeviceLocationState): String {
    val fix = state.lastFix
    return when {
        state.availability == LocationAvailability.PERMISSION_MISSING ->
            "Allow precise location to use satellite positioning"
        state.availability == LocationAvailability.GPS_DISABLED ->
            "Location is turned off — turn on GPS"
        state.availability == LocationAvailability.PROVIDER_ERROR ->
            state.statusMessage ?: "GNSS receiver error"
        fix != null && !fix.satelliteDerived -> {
            val accuracy = fix.horizontalAccuracyMeters?.let { " ±${"%.0f".format(it)} m" }.orEmpty()
            "Approximate position$accuracy (network) · waiting for satellites"
        }
        fix != null -> {
            val accuracy = fix.horizontalAccuracyMeters?.let { " ±${"%.0f".format(it)} m" }.orEmpty()
            "GNSS fix$accuracy · ${state.satellitesInFix}/${state.satellitesInView} satellites"
        }
        state.isUpdating ->
            "Searching for satellites… ${state.satellitesInView} in view"
        else -> "Tap Detect to find your position"
    }
}

/**
 * Find-location chrome: live GNSS status and the button that centers the map on the device's
 * satellite position.
 */
@Composable
fun GnssDetectBar(
    state: DeviceLocationState,
    onDetect: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp,
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = gnssStatusText(state),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { contentDescription = "GNSS status" }
                )
                if (state.availability == LocationAvailability.GPS_DISABLED) {
                    TextButton(onClick = onOpenLocationSettings) {
                        Text("Open location settings")
                    }
                }
            }
            OutlinedButton(
                onClick = onDetect,
                modifier = Modifier.semantics { contentDescription = "Detect current location" }
            ) {
                Icon(
                    imageVector = Icons.Default.MyLocation,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(text = "Detect", modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}
