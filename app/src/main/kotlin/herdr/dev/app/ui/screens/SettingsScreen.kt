package herdr.dev.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsEthernet
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import herdr.dev.app.viewmodel.SettingsViewModel

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import herdr.dev.app.ui.ThemeAccentOptions
import herdr.dev.app.ui.ThemeAccentNames
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    var darkMode by remember { mutableStateOf(true) }
    var notifications by remember { mutableStateOf(true) }
    val context = LocalContext.current

    val isConnected = state.connectionState == herdr.dev.app.data.ConnectionState.CONNECTED
    var showManualEndpoint by remember { mutableStateOf(!isConnected) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        "Settings", 
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    ) 
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.Default.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                },
                colors = androidx.compose.material3.TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            val isWideLayout = maxWidth >= 700.dp

            if (isWideLayout) {
                // Wide layout: Left-biased settings (56%), right subtle anonymized work traces (44%)
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(0.56f)
                            .fillMaxHeight()
                            .padding(horizontal = 24.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        SettingsContent(
                            state = state,
                            viewModel = viewModel,
                            darkMode = darkMode,
                            onDarkModeChange = { darkMode = it },
                            notifications = notifications,
                            onNotificationsChange = { notifications = it },
                            showManualEndpoint = showManualEndpoint,
                            onToggleManualEndpoint = { showManualEndpoint = !showManualEndpoint },
                            context = context,
                            isConnected = isConnected
                        )
                    }

                    // Elegant subtle vertical hairline separator
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    )

                    // Right side: Subtle Agent Work Traces (Matrix-like stream, refined & tasteful)
                    Box(
                        modifier = Modifier
                            .weight(0.44f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f))
                            .padding(20.dp)
                    ) {
                        AgentWorkTracesPanel(activeAgents = state.activeAgents)
                    }
                }
            } else {
                // Mobile layout: Clean, compact Dieter Rams settings without traces
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    SettingsContent(
                        state = state,
                        viewModel = viewModel,
                        darkMode = darkMode,
                        onDarkModeChange = { darkMode = it },
                        notifications = notifications,
                        onNotificationsChange = { notifications = it },
                        showManualEndpoint = showManualEndpoint,
                        onToggleManualEndpoint = { showManualEndpoint = !showManualEndpoint },
                        context = context,
                        isConnected = isConnected
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsContent(
    state: herdr.dev.app.viewmodel.SettingsUiState,
    viewModel: SettingsViewModel,
    darkMode: Boolean,
    onDarkModeChange: (Boolean) -> Unit,
    notifications: Boolean,
    onNotificationsChange: (Boolean) -> Unit,
    showManualEndpoint: Boolean,
    onToggleManualEndpoint: () -> Unit,
    context: android.content.Context,
    isConnected: Boolean
) {
    Column {
        // Dieter Rams Section: SWARM STATUS
        RamsSectionHeader(title = "Swarm Status")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(
                                    if (isConnected) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    CircleShape
                                )
                        )
                        Text(
                            text = if (isConnected) "Online" else "Disconnected",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "·  ${state.serverHost}:${state.serverPort}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = { viewModel.reconnect() },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Reconnect", style = MaterialTheme.typography.labelSmall)
                    }
                }

                // Compact metrics strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RamsStatItem(label = "Active", count = state.activePanesCount, color = MaterialTheme.colorScheme.primary)
                    RamsStatItem(label = "Attention", count = state.waitingPanesCount, color = if (state.waitingPanesCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                    RamsStatItem(label = "Completed", count = state.donePanesCount, color = MaterialTheme.colorScheme.onSurface)
                    RamsStatItem(label = "Total", count = state.totalPanes, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (state.activeAgents.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Engines:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        state.activeAgents.forEach { agent ->
                            Text(
                                text = agent,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .background(
                                        MaterialTheme.colorScheme.surfaceContainerHighest,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }

        // Dieter Rams Section: PAIRING
        Spacer(modifier = Modifier.height(14.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    android.widget.Toast.makeText(context, "Pairing camera ready for 'herdr pair' token", android.widget.Toast.LENGTH_SHORT).show()
                },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Pair New Machine",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "Scan QR code generated by 'herdr pair'",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    text = "Scan QR ▸",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Dieter Rams Section: AUTONOMY & DANGER LEVEL (Compact Segmented 1/3-height)
        Spacer(modifier = Modifier.height(18.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            RamsSectionHeader(title = "Swarm Autonomy")
            Text(
                text = when (state.dangerLevel) {
                    herdr.dev.app.data.DangerLevel.ZERO_DANGER -> "Zero risk · manual only"
                    herdr.dev.app.data.DangerLevel.NORMAL -> "Interactive review"
                    herdr.dev.app.data.DangerLevel.DANGERMAXXING -> "Auto-proceed unlocked"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (state.dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                CompactAutonomySegment(
                    title = "0 Danger",
                    isSelected = state.dangerLevel == herdr.dev.app.data.DangerLevel.ZERO_DANGER,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateDangerLevel(herdr.dev.app.data.DangerLevel.ZERO_DANGER) }
                )
                CompactAutonomySegment(
                    title = "Normal",
                    isSelected = state.dangerLevel == herdr.dev.app.data.DangerLevel.NORMAL,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateDangerLevel(herdr.dev.app.data.DangerLevel.NORMAL) }
                )
                CompactAutonomySegment(
                    title = "Dangermaxx",
                    isSelected = state.dangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.updateDangerLevel(herdr.dev.app.data.DangerLevel.DANGERMAXXING) }
                )
            }
        }

        // Dieter Rams Section: PREFERENCES & THEME
        Spacer(modifier = Modifier.height(18.dp))
        RamsSectionHeader(title = "Preferences")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column {
                // Theme color picker (5 inline swatches)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Theme Accent",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = ThemeAccentNames.getOrElse(state.accentColorIndex) { "Clay" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // 5 inline color swatches
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ThemeAccentOptions.forEachIndexed { index, color ->
                            val isSelected = state.accentColorIndex == index
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(color)
                                    .clickable { viewModel.updateAccentColorIndex(index) }
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(2.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                        } else {
                                            Modifier
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .background(Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                RamsDivider()

                // Live updates on Android render
                RamsToggleRow(
                    title = "Live updates render",
                    description = "Stream token output and active agent diffs live",
                    checked = state.liveUpdatesRender,
                    onCheckedChange = { viewModel.updateLiveUpdatesRender(it) }
                )

                RamsDivider()

                RamsToggleRow(
                    title = "Dark Theme",
                    description = "Optimized for OLED & E-ink Paper displays",
                    checked = darkMode,
                    onCheckedChange = onDarkModeChange
                )

                RamsDivider()

                RamsToggleRow(
                    title = "Notifications",
                    description = "Alert when agents pause for review or finish",
                    checked = notifications,
                    onCheckedChange = onNotificationsChange
                )
            }
        }

        // Dieter Rams Section: BRIDGE ENDPOINT
        Spacer(modifier = Modifier.height(18.dp))
        RamsSectionHeader(title = "Network Endpoint")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            border = androidx.compose.foundation.BorderStroke(
                1.dp, 
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleManualEndpoint() },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Daemon Host & Port",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "${state.serverHost}:${state.serverPort}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = if (showManualEndpoint) "Hide" else "Edit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                AnimatedVisibility(visible = showManualEndpoint) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = state.serverHost,
                            onValueChange = { viewModel.updateServerHost(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Host", style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("neo.local or 10.0.0.244") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = state.serverPort,
                            onValueChange = { viewModel.updateServerPort(it) },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Port", style = MaterialTheme.typography.labelSmall) },
                            placeholder = { Text("8765") },
                            singleLine = true
                        )
                    }
                }
            }
        }

        // Version Footer
        Spacer(modifier = Modifier.height(20.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Work remote, shep  ·  v0.0.1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun RamsSectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 2.dp, bottom = 6.dp)
    )
}

@Composable
private fun RamsStatItem(label: String, count: Int, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = "$count",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun RamsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    )
}

@Composable
private fun RamsAutonomyRow(
    title: String,
    description: String,
    isSelected: Boolean,
    badge: String? = null,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (badge != null) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .background(
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                                RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = isSelected,
            onClick = onClick
        )
    }
}

@Composable
private fun RamsToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
private fun CompactAutonomySegment(
    title: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.surfaceContainerHighest
                else Color.Transparent
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// Tasteful, non-cringe anonymized agent work traces stream (wide layouts only)
private data class TraceLogItem(
    val timestamp: String,
    val agentTag: String,
    val action: String,
    val status: String = "ok"
)

@Composable
private fun AgentWorkTracesPanel(activeAgents: List<String>) {
    val sampleTraces = remember {
        listOf(
            TraceLogItem("01:54:12", "codex", "ast_grep: match function authenticate()"),
            TraceLogItem("01:54:15", "claude", "analyzing diff (+24, -8) in telemetry.rs"),
            TraceLogItem("01:54:21", "_", "tailscale ping --peer workstation.lan"),
            TraceLogItem("01:54:28", "gemini", "prompting contextual reasoning: multi-pane rehydration"),
            TraceLogItem("01:54:35", "codex", "cargo clippy --fix --allow-dirty"),
            TraceLogItem("01:54:40", "claude", "synthesizing unit test suite in domain/agent"),
            TraceLogItem("01:54:48", "_", "evaluating AST branch safety: 0 risk confirmed"),
            TraceLogItem("01:54:55", "gemini", "reindexing workspace symbol table (1,482 symbols)"),
            TraceLogItem("01:55:02", "codex", "git commit -m 'refactor(ui): dieter rams hairline borders'"),
            TraceLogItem("01:55:10", "claude", "stream buffer flushed: 128 tokens/s"),
            TraceLogItem("01:55:18", "_", "dispatching subagent worker-4 to branch test-eval"),
            TraceLogItem("01:55:25", "gemini", "verified zero latency socket frame over iroh")
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                RoundedCornerShape(8.dp)
            )
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
                Text(
                    text = "WORK TRACES",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "STREAMING",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sampleTraces) { trace ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = trace.timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Text(
                        text = "[${trace.agentTag}]",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = trace.action,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
