package herdr.dev.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.Pane
import herdr.dev.app.data.models.Workspace
import herdr.dev.app.ui.components.AgentStatusBadge
import herdr.dev.app.viewmodel.DashboardViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onPaneClick: (String) -> Unit,
    onNavigateBack: (() -> Unit)? = null,
    viewModel: DashboardViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    android.util.Log.d("DashboardScreen", "Workspaces: ${state.workspaces.size}")
    var showStartAgentDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workspaces") },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                }
            )
        },
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(state.workspaces, key = { it.id }) { workspace ->
                WorkspaceSection(
                    workspace = workspace, 
                    onPaneClick = onPaneClick,
                    viewModel = viewModel,
                    onStartAgentClick = { showStartAgentDialog = workspace.id }
                )
            }
        }
    }

    if (showStartAgentDialog != null) {
        var agentName by remember { mutableStateOf("OpenCode") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showStartAgentDialog = null },
            title = { Text("Start New Agent") },
            text = {
                OutlinedTextField(
                    value = agentName,
                    onValueChange = { agentName = it },
                    label = { Text("Agent Name") },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.startAgent(showStartAgentDialog!!, agentName)
                    showStartAgentDialog = null
                }) {
                    Text("Start")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartAgentDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun WorkspaceSection(
    workspace: Workspace,
    onPaneClick: (String) -> Unit,
    viewModel: DashboardViewModel,
    onStartAgentClick: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = workspace.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            androidx.compose.material3.IconButton(onClick = onStartAgentClick) {
                Icon(Icons.Default.Add, contentDescription = "Add Agent")
            }
        }

        workspace.tabs.forEach { tab ->
            Text(
                text = tab.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            tab.panes.sortedBy { paneAttentionScore(it.state) }.forEach { pane ->
                PaneCard(
                    pane = pane, 
                    onPaneClick = onPaneClick,
                    viewModel = viewModel
                )
            }
        }
    }
}

@Composable
private fun PaneCard(
    pane: Pane,
    onPaneClick: (String) -> Unit,
    viewModel: DashboardViewModel,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPaneClick(pane.id) },
        colors = CardDefaults.cardColors(
            containerColor = when (pane.state) {
                AgentState.BLOCKED -> MaterialTheme.colorScheme.errorContainer
                AgentState.DONE -> MaterialTheme.colorScheme.tertiaryContainer
                AgentState.WORKING -> MaterialTheme.colorScheme.surfaceContainerHigh
                AgentState.IDLE -> MaterialTheme.colorScheme.surface
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = pane.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = pane.agentName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (pane.state != AgentState.WORKING) {
                    androidx.compose.material3.IconButton(
                        onClick = { viewModel.sendQuickMessage(pane.id, "Nudge") },
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Default.Send, 
                            contentDescription = "Nudge Agent",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                AgentStatusBadge(state = pane.state)
            }
        }
    }
}

private fun paneAttentionScore(state: AgentState): Int = when (state) {
    AgentState.BLOCKED -> 0
    AgentState.DONE -> 1
    AgentState.WORKING -> 2
    AgentState.IDLE -> 3
}

