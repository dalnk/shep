package herdr.dev.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import herdr.dev.app.data.ConnectionState
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.Pane
import herdr.dev.app.ui.components.AgentStatusBadge
import herdr.dev.app.viewmodel.HomeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onNavigateToDashboard: () -> Unit,
    onNavigateToChat: (String) -> Unit,
    onNavigateToSettings: () -> Unit,
    selectedPaneId: String? = null,
    isSplitLayout: Boolean = false,
    isSidebarCollapsed: Boolean = false,
    onToggleSidebar: (() -> Unit)? = null,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val workspaces by viewModel.workspaces.collectAsStateWithLifecycle()
    var askAnythingText by remember { mutableStateOf("") }

    val dangerLevel by viewModel.dangerLevel.collectAsStateWithLifecycle()
    val lastAutoProceedMessage by viewModel.lastAutoProceedMessage.collectAsStateWithLifecycle()

    LaunchedEffect(lastAutoProceedMessage) {
        lastAutoProceedMessage?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    val dismissedTaskIds by viewModel.dismissedTaskIds.collectAsStateWithLifecycle()

    val allPanes = remember(workspaces) {
        workspaces.flatMap { it.tabs }.flatMap { it.panes }.distinctBy { it.id }
    }

    val query = askAnythingText.trim().lowercase()
    val filteredPanes = remember(allPanes, query) {
        if (query.isBlank()) allPanes
        else allPanes.filter {
            it.title.lowercase().contains(query) ||
            it.agentName.lowercase().contains(query) ||
            it.id.lowercase().contains(query)
        }
    }

    var isAttentionInboxCollapsed by remember { mutableStateOf(false) }
    var isDoneInboxCollapsed by remember { mutableStateOf(false) }

    val attentionPanes = remember(filteredPanes, dismissedTaskIds) {
        filteredPanes.filter { it.state == AgentState.BLOCKED && !dismissedTaskIds.contains(it.id) }
    }

    val donePanes = remember(filteredPanes, dismissedTaskIds) {
        filteredPanes.filter { it.state == AgentState.DONE && !dismissedTaskIds.contains(it.id) }
    }

    val activePanes = remember(filteredPanes) {
        filteredPanes.filter { it.state != AgentState.BLOCKED && it.state != AgentState.DONE }
            .sortedByDescending { it.state == AgentState.WORKING }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    // Wifi connection status moved to the left
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            imageVector = if (connectionState == ConnectionState.CONNECTED) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = "Status",
                            tint = if (connectionState == ConnectionState.CONNECTED) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                },
                title = { Text("Shepard", fontWeight = FontWeight.ExtraBold) },
                actions = {
                    if (isSplitLayout && onToggleSidebar != null) {
                        IconButton(onClick = onToggleSidebar) {
                            Icon(
                                imageVector = Icons.Default.MenuOpen,
                                contentDescription = "Collapse Sidebar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Search & Filter agents or ask _
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextField(
                            value = askAnythingText,
                            onValueChange = { askAnythingText = it },
                            placeholder = { Text("Search agents, tasks, repos…") },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                disabledContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                            singleLine = true,
                        )
                        if (askAnythingText.isNotBlank()) {
                            IconButton(
                                onClick = { askAnythingText = "" },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) {
                item {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.4f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Dangermaxxing Active",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                                Text(
                                    text = "Auto-proceeding paused agents to maximize swarm velocity",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                    }
                }
            }

            // Inbox: Needs Attention (BLOCKED)
            if (attentionPanes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isAttentionInboxCollapsed = !isAttentionInboxCollapsed },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Inbox · Needs Attention",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Badge(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    text = "${attentionPanes.size}",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isAttentionInboxCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isAttentionInboxCollapsed) "Expand" else "Collapse",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isAttentionInboxCollapsed) {
                    items(attentionPanes, key = { "inbox-attn-${it.id}" }) { pane ->
                        PausedAgentInboxCard(
                            pane = pane,
                            dangerLevel = dangerLevel,
                            onOpenChat = { onNavigateToChat(pane.id) },
                            onQuickAction = { action ->
                                viewModel.sendQuickAction(pane.id, action)
                                Toast.makeText(context, "Sent '$action' to ${pane.agentName}", Toast.LENGTH_SHORT).show()
                            }
                        )
                    }
                }
            }

            // Inbox: Completed / Review (DONE)
            if (donePanes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isDoneInboxCollapsed = !isDoneInboxCollapsed },
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.tertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Inbox · Completed Tasks",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Badge(containerColor = MaterialTheme.colorScheme.tertiaryContainer) {
                                Text(
                                    text = "${donePanes.size}",
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                            Icon(
                                imageVector = if (isDoneInboxCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                contentDescription = if (isDoneInboxCollapsed) "Expand" else "Collapse",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (!isDoneInboxCollapsed) {
                    items(donePanes, key = { "inbox-done-${it.id}" }) { pane ->
                        DoneAgentInboxCard(
                            pane = pane,
                            onOpenChat = { onNavigateToChat(pane.id) },
                            onDismiss = { viewModel.dismissTask(pane.id) },
                        )
                    }
                }
            }

            item {
                // Quick Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    QuickAction(Icons.Default.Dashboard, "Workspaces", onNavigateToDashboard)
                    QuickAction(Icons.Default.Settings, "Settings", onNavigateToSettings)
                }
            }

            if (activePanes.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Agents (${activePanes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        TextButton(onClick = onNavigateToDashboard) {
                            Text("See all")
                        }
                    }
                }

                items(activePanes, key = { it.id }) { pane ->
                    ActiveAgentCard(
                        pane = pane, 
                        isSelected = (pane.id == selectedPaneId),
                        onClick = { onNavigateToChat(pane.id) }
                    )
                }
            } else if (attentionPanes.isEmpty() && donePanes.isEmpty()) {
                item {
                    EmptyStateCard(onNavigateToDashboard)
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun formatDuration(seconds: Long?): String {
    if (seconds == null || seconds <= 0) return ""
    val m = seconds / 60
    val h = m / 60
    val d = h / 24
    return when {
        d > 0 -> "${d}d ${h % 24}h"
        h > 0 -> "${h}h ${m % 60}m"
        m > 0 -> "${m}m"
        else -> "${seconds}s"
    }
}

@Composable
private fun PausedAgentInboxCard(
    pane: Pane,
    dangerLevel: herdr.dev.app.data.DangerLevel = herdr.dev.app.data.DangerLevel.NORMAL,
    onOpenChat: () -> Unit,
    onQuickAction: (String) -> Unit,
) {
    val waitingStr = formatDuration(pane.waitingDurationSeconds)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (dangerLevel) {
                herdr.dev.app.data.DangerLevel.DANGERMAXXING -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            }
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.size(36.dp),
                    shape = CircleShape,
                    color = when (dangerLevel) {
                        herdr.dev.app.data.DangerLevel.DANGERMAXXING -> MaterialTheme.colorScheme.tertiaryContainer
                        else -> MaterialTheme.colorScheme.errorContainer
                    }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) Icons.Default.Bolt else Icons.Default.PauseCircleFilled,
                            contentDescription = "Paused",
                            tint = if (dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pane.title.ifBlank { "Pane ${pane.id}" },
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = when (dangerLevel) {
                            herdr.dev.app.data.DangerLevel.DANGERMAXXING -> "${pane.agentName} · ⚡ Auto-proceeding via Dangermaxxing..."
                            herdr.dev.app.data.DangerLevel.ZERO_DANGER -> "${pane.agentName} · 🛡️ 0 Danger: Manual approval required"
                            herdr.dev.app.data.DangerLevel.NORMAL -> if (waitingStr.isNotBlank()) "${pane.agentName} · Waiting $waitingStr for approval" else "${pane.agentName} · Waiting for approval or input"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error
                    )
                }
                AgentStatusBadge(state = pane.state)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { onQuickAction("p") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Proceed (p)", style = MaterialTheme.typography.labelMedium)
                }
                Button(
                    onClick = { onQuickAction("y") },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Approve (y)", style = MaterialTheme.typography.labelMedium)
                }
                OutlinedButton(
                    onClick = { onQuickAction("n") },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel (n)", style = MaterialTheme.typography.labelMedium)
                }
                IconButton(onClick = onOpenChat) {
                    Icon(
                        Icons.Default.Chat,
                        contentDescription = "Chat with agent",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun DoneAgentInboxCard(
    pane: Pane,
    onOpenChat: () -> Unit,
    onDismiss: () -> Unit,
) {
    val waitingStr = formatDuration(pane.waitingDurationSeconds)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenChat),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                modifier = Modifier.size(36.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = "Completed",
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pane.title.ifBlank { "Pane ${pane.id}" },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (waitingStr.isNotBlank()) "${pane.agentName} · Finished · Idle for $waitingStr" else "${pane.agentName} · Finished",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // MD3 paper-outlined subtle X button in top corner
            Surface(
                onClick = onDismiss,
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "Dismiss task",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AgentAvatar(
    agentName: String,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
) {
    val a = agentName.trim().lowercase()
    val (icon, label) = when {
        a in listOf("agy", "gemini") -> Icons.Default.AutoAwesome to "Gemini"
        a == "codex" -> Icons.Default.Terminal to "Codex"
        a == "claude" -> Icons.Default.Stars to "Claude"
        a == "copilot" -> Icons.Default.Code to "Copilot"
        a == "grok" -> Icons.Default.Bolt to "Grok"
        a in listOf("under", "_") -> Icons.Default.Psychology to "Under"
        else -> Icons.Default.Computer to a.take(1).uppercase()
    }

    Surface(
        modifier = modifier.size(40.dp),
        shape = CircleShape,
        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(4.dp)
    ) {
        Surface(
            onClick = onClick,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = label, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(top = 8.dp)
        )
    }
}


@Composable
private fun ActiveAgentCard(
    pane: Pane, 
    isSelected: Boolean = false,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer 
                             else MaterialTheme.colorScheme.surfaceContainer
        ),
        border = if (isSelected) androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AgentAvatar(
                agentName = pane.agentName,
                isSelected = isSelected,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(pane.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(pane.agentName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AgentStatusBadge(state = pane.state)
        }
    }
}

@Composable
private fun EmptyStateCard(onNavigateToDashboard: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("No active agents", style = MaterialTheme.typography.bodyLarge)
            TextButton(onClick = onNavigateToDashboard) {
                Text("View all workspaces")
            }
        }
    }
}
