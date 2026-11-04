package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.domain.model.Layout

/** True when a plan has what the dashboard needs: a region and an origin fix. */
fun Layout.isReadyToSend(): Boolean = regionBounds != null && anchor.hasGeographicFix

private fun statusLabel(status: String): String = when (status) {
    "SUBMITTED" -> "Sent"
    "UNDER_REVIEW" -> "Under review"
    "APPROVED" -> "Approved"
    "REJECTED" -> "Rejected"
    "CHANGES_REQUESTED" -> "Changes requested"
    "WITHDRAWN" -> "Withdrawn"
    else -> status.lowercase().replaceFirstChar { it.uppercase() }
}

/**
 * The approval line on a Home plan card: a Send button, or the plan's dashboard code and its
 * current decision once it has been sent.
 */
@Composable
fun PlanSubmissionRow(
    layout: Layout,
    sent: SentPlan?,
    isSending: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier
) {
    val ready = layout.isReadyToSend()
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        when {
            isSending -> {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Text(
                    text = "Sending…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            sent != null -> {
                ApprovalChip(status = sent.status)
                Text(
                    text = "${sent.code} · v${sent.version}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
                if (sent.canResubmit && ready) {
                    TextButton(onClick = onSend) { Text("Resubmit", fontWeight = FontWeight.SemiBold) }
                }
            }

            ready -> {
                TextButton(onClick = onSend) {
                    Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Send for approval", fontWeight = FontWeight.SemiBold)
                }
            }

            else -> Text(
                text = "Set a region and an origin to send",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ApprovalChip(status: String) {
    val scheme = MaterialTheme.colorScheme
    val colors: Pair<Color, Color> = when (status) {
        "APPROVED" -> scheme.primary.copy(alpha = 0.16f) to scheme.primary
        "REJECTED", "CHANGES_REQUESTED" -> scheme.error.copy(alpha = 0.12f) to scheme.error
        else -> scheme.surfaceVariant to scheme.onSurfaceVariant
    }
    val background = colors.first
    val content = colors.second
    Text(
        text = statusLabel(status),
        color = content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

/** Confirms the send and lets the planner add a short note for the reviewer. */
@Composable
fun SendPlanDialog(
    layout: Layout,
    isResubmit: Boolean,
    signedIn: Boolean,
    onConfirm: (note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var note by remember { mutableStateOf("") }
    val assets = if (layout.objects.size == 1) "1 asset" else "${layout.objects.size} assets"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isResubmit) "Resubmit for approval?" else "Send for approval?",
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column {
                Text(
                    text = "'${layout.name}' ($assets) will go to the reviewers on the dashboard. " +
                        "Edits you make afterwards stay on this phone until you resubmit.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
                if (!signedIn) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "You will be asked to sign in first.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { if (it.length <= 500) note = it },
                    label = { Text("Note to the reviewer (optional)") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(note.trim()) }) {
                Text(if (signedIn) "Send" else "Continue", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
