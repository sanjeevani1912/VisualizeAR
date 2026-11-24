package com.example.dhruvar.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dhruvar.data.remote.SignedInUser

/**
 * Account icon for the Home top bar. Signed out it opens the sign-in screen; signed in it shows
 * who is signed in, a way to refresh the approval status of sent plans, and Sign out.
 */
@Composable
fun AccountMenuButton(
    user: SignedInUser?,
    onSignIn: () -> Unit,
    onRefresh: () -> Unit,
    onSignOut: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    IconButton(
        onClick = { if (user == null) onSignIn() else expanded = true },
        modifier = Modifier.semantics {
            contentDescription = if (user == null) "Sign in" else "Account: ${user.name}"
        }
    ) {
        if (user == null) {
            Icon(
                imageVector = Icons.Default.AccountCircle,
                contentDescription = "Sign in",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // Round avatar with the user's initials (no photo is stored for users).
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = userInitials(user.name, user.username),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
        if (user != null) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text(
                    text = listOf(user.rank, user.name).filter { it.isNotBlank() }.joinToString(" "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = user.role.replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            DropdownMenuItem(
                text = { Text("Refresh approval status") },
                onClick = {
                    expanded = false
                    onRefresh()
                },
                leadingIcon = { Icon(Icons.Default.Sync, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Sign out") },
                onClick = {
                    expanded = false
                    onSignOut()
                },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) }
            )
        }
    }
}

/** "Sanjay Kumar" -> "SK", "sanjay" -> "S". Falls back to [fallback] (username), then "?". */
fun userInitials(name: String, fallback: String = ""): String {
    val words = name.trim().split(" ").filter { it.isNotEmpty() }
        .ifEmpty { fallback.trim().split(" ").filter { it.isNotEmpty() } }
    val letters = when {
        words.isEmpty() -> ""
        words.size == 1 -> words[0].take(1)
        else -> words.first().take(1) + words.last().take(1)
    }
    return letters.uppercase().ifEmpty { "?" }
}
