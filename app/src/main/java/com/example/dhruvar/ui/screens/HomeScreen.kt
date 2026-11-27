package com.example.dhruvar.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dhruvar.data.remote.SentPlan
import com.example.dhruvar.domain.model.Layout
import com.example.dhruvar.ui.components.AccountMenuButton
import com.example.dhruvar.ui.components.AppTopBar
import com.example.dhruvar.ui.components.PlanSubmissionRow
import com.example.dhruvar.ui.components.SendPlanDialog
import com.example.dhruvar.viewmodel.AccountViewModel
import com.example.dhruvar.viewmodel.PlanningViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    onCreateNewLayout: () -> Unit,
    onOpenLayout: (Layout) -> Unit,
    onOpenFieldGuide: () -> Unit = {},
    onOpenSignIn: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: PlanningViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val account by accountViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var layoutToRename by remember { mutableStateOf<Layout?>(null) }
    var renameInput by remember { mutableStateOf("") }
    var layoutToDelete by remember { mutableStateOf<Layout?>(null) }
    var layoutToSend by remember { mutableStateOf<Layout?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadSavedLayouts()
    }

    // Collapsed state of the "This device" section, remembered per user on this phone.
    val context = LocalContext.current
    val collapseKey = "device_collapsed_" + (uiState.signedInUserId ?: "signed_out")
    var deviceCollapsed by remember(collapseKey) {
        mutableStateOf(homePrefs(context).getBoolean(collapseKey, false))
    }
    fun toggleDeviceCollapsed() {
        deviceCollapsed = !deviceCollapsed
        homePrefs(context).edit().putBoolean(collapseKey, deviceCollapsed).apply()
    }

    // Pick up the latest approval decisions whenever Home opens or the user signs in.
    LaunchedEffect(account.user) {
        if (account.user != null) accountViewModel.refreshStatuses()
    }

    LaunchedEffect(account.message) {
        account.message?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            accountViewModel.clearMessage()
        }
    }

    layoutToSend?.let { target ->
        SendPlanDialog(
            layout = target,
            isResubmit = account.sent[target.id]?.canResubmit == true,
            signedIn = account.isSignedIn,
            onConfirm = { note ->
                layoutToSend = null
                if (account.isSignedIn) {
                    accountViewModel.send(target, note)
                } else {
                    accountViewModel.sendAfterSignIn(target, note)
                    onOpenSignIn()
                }
            },
            onDismiss = { layoutToSend = null }
        )
    }

    LaunchedEffect(uiState.userFeedbackMessage) {
        uiState.userFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserFeedback()
        }
    }

    layoutToRename?.let { targetLayout ->
        AlertDialog(
            onDismissRequest = { layoutToRename = null },
            title = {
                Text(
                    text = "Rename Layout",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter a new name for '${targetLayout.name}':",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = renameInput,
                        onValueChange = { renameInput = it },
                        label = { Text("Layout Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val newName = renameInput.trim().ifEmpty { targetLayout.name }
                        viewModel.renameLayout(targetLayout.id, newName)
                        layoutToRename = null
                    }
                ) {
                    Text("Rename", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { layoutToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    layoutToDelete?.let { targetLayout ->
        AlertDialog(
            onDismissRequest = { layoutToDelete = null },
            title = {
                Text(
                    text = "Delete Layout?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to permanently delete '${targetLayout.name}'? This action cannot be undone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteLayout(targetLayout.id)
                        layoutToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { layoutToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "DhruvAR",
                showArmyLogo = true,
                actions = {
                    AccountMenuButton(
                        user = account.user,
                        onSignIn = onOpenSignIn,
                        onRefresh = { accountViewModel.refreshStatuses() },
                        onSignOut = { accountViewModel.signOut() }
                    )
                    IconButton(onClick = onOpenFieldGuide) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = "Field Guide"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onCreateNewLayout,
                icon = {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null
                    )
                },
                text = { Text("New plan") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        when {
            uiState.isLoadingSavedLayouts -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        strokeWidth = 3.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            uiState.savedLayouts.isEmpty() && !account.isSignedIn -> {
                Column(modifier = Modifier.padding(innerPadding)) {
                    SignInHint(onOpenSignIn)
                    EmptyPlansState()
                }
            }

            else -> {
                val signedIn = account.isSignedIn
                val myPlans = uiState.myPlans
                val devicePlans = uiState.devicePlans
                // "Continue" highlights the newest plan the user can actually change.
                val featuredId = if (signedIn) myPlans.firstOrNull()?.id else devicePlans.firstOrNull()?.id
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 8.dp,
                        bottom = 88.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (signedIn) {
                        item(key = "my-header") {
                            SectionHeader(title = "My plans", count = myPlans.size)
                        }
                        if (myPlans.isEmpty()) {
                            item(key = "my-empty") {
                                Text(
                                    text = "No plans yet. Tap New plan to start one.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
                                )
                            }
                        }
                        items(items = myPlans, key = { "my-" + it.id }) { layout ->
                            PlanCard(
                                layout = layout,
                                featured = layout.id == featuredId,
                                onOpen = { onOpenLayout(layout) },
                                onRename = {
                                    layoutToRename = layout
                                    renameInput = layout.name
                                },
                                onDelete = { layoutToDelete = layout },
                                sent = account.sent[layout.id],
                                isSending = account.sendingLayoutId == layout.id,
                                onSend = { layoutToSend = layout }
                            )
                        }
                    } else {
                        item(key = "sign-in-hint") {
                            SignInHint(onOpenSignIn)
                        }
                    }

                    if (devicePlans.isNotEmpty()) {
                        item(key = "device-header") {
                            DeviceSectionHeader(
                                count = devicePlans.size,
                                collapsed = deviceCollapsed,
                                onToggle = { toggleDeviceCollapsed() }
                            )
                        }
                        if (!deviceCollapsed) {
                            items(items = devicePlans, key = { "device-" + it.id }) { layout ->
                                if (signedIn) {
                                    // View only: open on the map and in AR, or copy into my plans.
                                    PlanCard(
                                        layout = layout,
                                        featured = false,
                                        onOpen = { onOpenLayout(layout) },
                                        onRename = {},
                                        onDelete = {},
                                        sent = null,
                                        isSending = false,
                                        onSend = {},
                                        viewOnly = true,
                                        onCopyToMine = { viewModel.copyToMyPlans(layout.id) }
                                    )
                                } else {
                                    PlanCard(
                                        layout = layout,
                                        featured = layout.id == featuredId,
                                        onOpen = { onOpenLayout(layout) },
                                        onRename = {
                                            layoutToRename = layout
                                            renameInput = layout.name
                                        },
                                        onDelete = { layoutToDelete = layout },
                                        sent = null,
                                        isSending = false,
                                        onSend = { layoutToSend = layout }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun homePrefs(context: Context) =
    context.applicationContext.getSharedPreferences("dhruvar_home", Context.MODE_PRIVATE)

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 2.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        CountBubble(count)
    }
}

/** Small rounded count next to a section title, e.g. "My plans (3)". */
@Composable
private fun CountBubble(count: Int) {
    Text(
        text = count.toString(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
private fun DeviceSectionHeader(count: Int, collapsed: Boolean, onToggle: () -> Unit) {
    var showInfo by remember { mutableStateOf(false) }
    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            title = { Text("Plans on this phone", fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "These plans were saved on this phone without an account, so everyone who " +
                        "uses this phone can see them. When you are signed in you can view them " +
                        "on the map and in AR, or make a copy in your own plans to change or send it."
                )
            },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            }
        )
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onToggle)
            .padding(start = 4.dp, top = 12.dp, bottom = 2.dp)
    ) {
        Icon(
            imageVector = Icons.Default.Smartphone,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "On this phone",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.width(8.dp))
        CountBubble(count)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = { showInfo = true }, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "About plans on this phone",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
        Icon(
            imageVector = if (collapsed) Icons.Default.ExpandMore else Icons.Default.ExpandLess,
            contentDescription = if (collapsed) "Show plans on this phone" else "Hide plans on this phone",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp)
        )
    }
}

@Composable
private fun SignInHint(onOpenSignIn: () -> Unit) {
    Text(
        text = "Sign in to see your own plans",
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onOpenSignIn)
            .padding(horizontal = 20.dp, vertical = 10.dp)
    )
}

@Composable
private fun EmptyPlansState(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No plans yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Create a plan to stamp an origin and place assets.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PlanCard(
    layout: Layout,
    featured: Boolean,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    sent: SentPlan?,
    isSending: Boolean,
    onSend: () -> Unit,
    viewOnly: Boolean = false,
    onCopyToMine: () -> Unit = {}
) {
    val shape = RoundedCornerShape(12.dp)
    val primary = MaterialTheme.colorScheme.primary
    Surface(
        onClick = onOpen,
        shape = shape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = if (featured) 3.dp else 1.dp,
        shadowElevation = if (featured) 2.dp else 0.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (featured) primary else primary.copy(alpha = 0.12f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Layers,
                    contentDescription = null,
                    tint = if (featured) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        primary
                    },
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (featured) {
                    Text(
                        text = "Continue",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primary
                    )
                }
                Text(
                    text = layout.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${assetCountLabel(layout.objects.size)} · ${relativeUpdatedLabel(layout.updatedAtEpochMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    StatusChip(label = "Origin", done = layout.anchor.hasGeographicFix)
                    StatusChip(label = "Region", done = layout.regionBounds != null)
                    if (layout.regionBounds == null || !layout.anchor.hasGeographicFix) {
                        StatusChip(label = "Incomplete", done = false)
                    }
                    if (viewOnly) {
                        StatusChip(label = "View only", done = false)
                    }
                }
                if (!viewOnly) {
                    PlanSubmissionRow(
                        layout = layout,
                        sent = sent,
                        isSending = isSending,
                        onSend = onSend
                    )
                }
            }
            if (viewOnly) {
                CopyOnlyMenu(onCopyToMine = onCopyToMine)
            } else {
                PlanActionsMenu(onRename = onRename, onDelete = onDelete)
            }
        }
    }
}

@Composable
private fun StatusChip(label: String, done: Boolean) {
    val background = if (done) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (done) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = label,
        color = content,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Medium,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(background)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

@Composable
private fun PlanActionsMenu(
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Plan actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Rename") },
                onClick = {
                    menuExpanded = false
                    onRename()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DriveFileRenameOutline,
                        contentDescription = null
                    )
                }
            )
            DropdownMenuItem(
                text = { Text("Delete") },
                onClick = {
                    menuExpanded = false
                    onDelete()
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
private fun CopyOnlyMenu(onCopyToMine: () -> Unit) {
    var menuExpanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { menuExpanded = true }) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = "Plan actions",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("Make a copy in my plans") },
                onClick = {
                    menuExpanded = false
                    onCopyToMine()
                },
                leadingIcon = {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null)
                }
            )
        }
    }
}

private fun assetCountLabel(count: Int): String {
    return if (count == 1) "1 asset" else "$count assets"
}

private fun relativeUpdatedLabel(updatedAtEpochMs: Long): String {
    val deltaMs = (System.currentTimeMillis() - updatedAtEpochMs).coerceAtLeast(0L)
    val minutes = deltaMs / 60_000L
    val hours = deltaMs / 3_600_000L
    val days = deltaMs / 86_400_000L
    return when {
        minutes < 1L -> "Updated just now"
        minutes < 60L -> "Updated ${minutes}m ago"
        hours < 24L -> "Updated ${hours}h ago"
        days < 7L -> "Updated ${days}d ago"
        else -> {
            val formatted = SimpleDateFormat("MMM d", Locale.getDefault())
                .format(Date(updatedAtEpochMs))
            "Updated $formatted"
        }
    }
}
