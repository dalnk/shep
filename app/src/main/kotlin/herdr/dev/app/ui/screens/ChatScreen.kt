package herdr.dev.app.ui.screens

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import herdr.dev.app.data.models.ChatMessage
import herdr.dev.app.viewmodel.ChatViewModel
import kotlinx.coroutines.flow.collect

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    onNavigateBack: () -> Unit,
    paneId: String? = null,
    initialMessage: String? = null,
    showBackButton: Boolean = true,
    onToggleSidebar: (() -> Unit)? = null,
    viewModel: ChatViewModel = hiltViewModel(),
) {
    LaunchedEffect(paneId) {
        if (!paneId.isNullOrBlank()) {
            viewModel.setPaneId(paneId)
        }
    }

    val state = viewModel.uiState.collectAsStateWithLifecycle().value
    val listState = rememberLazyListState()

    // Handle initial message
    LaunchedEffect(initialMessage) {
        if (!initialMessage.isNullOrBlank()) {
            viewModel.onDraftChanged(initialMessage)
            viewModel.sendDraft()
        }
    }

    if (showBackButton) {
        PredictiveBackHandler(enabled = true) {
            it.collect { }
            onNavigateBack()
        }
    }

    var hasInitiallyScrolled by remember(state.paneTitle) { mutableStateOf(false) }
    var wasAtBottom by remember { mutableStateOf(true) }

    // Track whether user was near the bottom before state update
    val isNearBottom = remember {
        derivedStateOf {
            val totalItems = listState.layoutInfo.totalItemsCount
            if (totalItems == 0) true
            else {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                lastVisibleIndex >= totalItems - 2
            }
        }
    }

    LaunchedEffect(isNearBottom.value) {
        wasAtBottom = isNearBottom.value
    }

    // React to new messages or active action / output changes
    val lastMessageText = state.messages.lastOrNull()?.text ?: ""
    val lastAction = state.activeAction ?: ""
    LaunchedEffect(state.messages.size, lastMessageText, lastAction) {
        if (state.messages.isNotEmpty()) {
            val targetIndex = state.messages.lastIndex
            if (!hasInitiallyScrolled) {
                listState.scrollToItem(targetIndex)
                hasInitiallyScrolled = true
            } else if (wasAtBottom) {
                // Sticky scroll with live output
                listState.scrollToItem(targetIndex)
            }
        }
    }

    val modelShortname = state.modelShortname.ifBlank {
        deriveModelShortname(state.model, state.agentName)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.paneTitle.ifBlank { modelShortname },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (state.agentName.isNotBlank() && !state.paneTitle.contains(state.agentName, ignoreCase = true)) {
                            Text(
                                text = state.agentName,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    if (onToggleSidebar != null) {
                        IconButton(onClick = onToggleSidebar) {
                            Icon(Icons.Default.Menu, contentDescription = "Show Sidebar")
                        }
                    } else if (showBackButton) {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
            )
        },
        bottomBar = {
            ChatInputBar(
                draft = state.draft,
                model = state.model,
                modelShortname = modelShortname,
                contextTokens = state.contextTokens,
                activeAction = state.activeAction,
                isAgentRunning = state.isAgentRunning,
                onDraftChanged = viewModel::onDraftChanged,
                onSend = viewModel::sendDraft,
                onQuickAction = viewModel::sendQuickAction,
            )
        },
    ) { paddingValues ->
        val visibleMessages = remember(state.messages) {
            state.messages.filter { msg ->
                if (!msg.fromUser) true
                else {
                    val trimmed = msg.text.trim().lowercase()
                    trimmed != "p" && trimmed != "y"
                }
            }
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 12.dp),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(
                items = visibleMessages,
                key = { it.id },
                contentType = { if (it.fromUser) "user" else "agent" }
            ) { message ->
                ChatBubble(
                    message = message,
                    modelShortname = modelShortname,
                    onQuickAction = viewModel::sendQuickAction,
                )
            }
        }
    }
}

@Composable
private fun ChatBubble(
    message: ChatMessage,
    modelShortname: String = "Agent",
    onQuickAction: (String) -> Unit = {},
) {
    val isTerminal = message.id.startsWith("history-")

    if (message.fromUser) {
        // User query: sleek, minimal, distinct bubble/card aligned to end
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            contentAlignment = Alignment.CenterEnd,
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(16.dp),
                tonalElevation = 1.dp,
                modifier = Modifier.widthIn(max = 560.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    MarkdownText(
                        markdown = message.text,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Normal
                        ),
                    )
                }
            }
        }
    } else {
        // Assistant / Agent response: Full-width clean canvas layout (no speech bubble card wrapper)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp)
        ) {
            // Subtle agent/model header with badge
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    shape = CircleShape,
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = "✦",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Text(
                    text = modelShortname,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // 1. Structured Thinking (from underclass, grok, etc.)
            if (!message.thinking.isNullOrBlank()) {
                var thinkingExpanded by remember { mutableStateOf(false) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { thinkingExpanded = !thinkingExpanded }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "💭 Thought process",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            Text(
                                text = if (thinkingExpanded) "▾ hide" else "▸ expand",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (thinkingExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = message.thinking,
                                style = MaterialTheme.typography.bodySmall.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // 2. Structured Tools (or parsed terminal tools) - Claude Desktop style summary
            val allTools = if (isTerminal) {
                val parsed = remember(message.text) {
                    herdr.dev.app.ui.util.TerminalOutputParser.parse(message.text)
                }
                parsed.tools
            } else {
                message.tools
            }

            if (allTools.isNotEmpty()) {
                var toolsExpanded by remember { mutableStateOf(false) }

                // Summarize tool calls like Claude Desktop ("Ran 4 commands", "Viewed 3 files, edited 1 file", etc.)
                val summaryText = remember(allTools) {
                    val counts = mutableMapOf<String, Int>()
                    for (tool in allTools) {
                        val t = tool.trim().removePrefix("●").removePrefix("•").removePrefix("*").trim()
                        val category = when {
                            t.startsWith("run_command", ignoreCase = true) || t.startsWith("Running command", ignoreCase = true) -> "command"
                            t.startsWith("view_file", ignoreCase = true) || t.startsWith("Viewing file", ignoreCase = true) -> "read file"
                            t.startsWith("replace_file_content", ignoreCase = true) || t.startsWith("Editing file", ignoreCase = true) || t.startsWith("write_to_file", ignoreCase = true) -> "edit file"
                            t.startsWith("grep_search", ignoreCase = true) || t.startsWith("find_by_name", ignoreCase = true) || t.startsWith("list_dir", ignoreCase = true) -> "search"
                            t.startsWith("search_web", ignoreCase = true) || t.startsWith("read_url", ignoreCase = true) -> "web search"
                            else -> {
                                val firstWord = t.substringBefore('(').substringBefore(':').substringBefore(' ').trim().lowercase()
                                if (firstWord.isNotBlank() && firstWord.length < 25) firstWord else "tool"
                            }
                        }
                        counts[category] = (counts[category] ?: 0) + 1
                    }

                    if (counts.size == 1) {
                        val (cat, count) = counts.entries.first()
                        when (cat) {
                            "command" -> "Ran $count command${if (count > 1) "s" else ""}"
                            "read file" -> "Read $count file${if (count > 1) "s" else ""}"
                            "edit file" -> "Edited $count file${if (count > 1) "s" else ""}"
                            "search" -> "Searched $count time${if (count > 1) "s" else ""}"
                            "web search" -> "Searched web $count time${if (count > 1) "s" else ""}"
                            else -> "Used $cat $count time${if (count > 1) "s" else ""}"
                        }
                    } else {
                        // Multi-action summary
                        val parts = counts.entries.map { (cat, count) ->
                            when (cat) {
                                "command" -> "$count cmd${if (count > 1) "s" else ""}"
                                "read file" -> "$count read${if (count > 1) "s" else ""}"
                                "edit file" -> "$count edit${if (count > 1) "s" else ""}"
                                "search" -> "$count search${if (count > 1) "es" else ""}"
                                else -> "$count $cat"
                            }
                        }
                        "${allTools.size} tool calls (${parts.take(3).joinToString(", ")})"
                    }
                }

                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { toolsExpanded = !toolsExpanded }
                        .padding(vertical = 3.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Bolt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = summaryText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                    Text(
                        text = if (toolsExpanded) "▾" else "▸",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                if (toolsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 8.dp, bottom = 8.dp)
                    ) {
                        for (tool in allTools.takeLast(15)) {
                            Text(
                                text = tool,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }

            // 3. Clean Text response directly on canvas
            val displayMarkdown = if (isTerminal) {
                val parsed = remember(message.text) {
                    herdr.dev.app.ui.util.TerminalOutputParser.parse(message.text)
                }
                parsed.response
            } else {
                message.text
            }

            if (displayMarkdown.isNotBlank()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp)) {
                    MarkdownText(
                        markdown = displayMarkdown,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface,
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight
                        ),
                    )
                }
            }

            // 4. Paused Questions & Action Approvals
            if (message.question != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "❓ Action Required",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        if (message.question.prompt.isNotBlank()) {
                            Text(
                                text = message.question.prompt,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                        if (message.question.options.isNotEmpty()) {
                            val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (opt in message.question.options) {
                                    val displayLabel = when (opt.lowercase()) {
                                        "y" -> "✓ Approve (y)"
                                        "n" -> "✕ Deny (n)"
                                        "p" -> "♾ Remember (p)"
                                        else -> opt
                                    }
                                    AssistChip(
                                        onClick = {
                                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                                            onQuickAction(opt)
                                        },
                                        label = { Text(displayLabel, fontWeight = FontWeight.Bold) }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Subtle divider between messages
            Spacer(modifier = Modifier.height(12.dp))
            Divider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                thickness = 0.5.dp
            )
        }
    }
}

@Composable
private fun ThinkingIndicator(
    actionText: String? = null,
    modifier: Modifier = Modifier,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 750, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val label = if (!actionText.isNullOrBlank() && actionText != "null") actionText else "Thinking…"

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(
                    MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha),
                    CircleShape
                )
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.primary.copy(alpha = pulseAlpha)
        )
    }
}

@Composable
private fun ChatInputBar(
    draft: String,
    model: String,
    modelShortname: String = "Agent",
    contextTokens: Int?,
    activeAction: String? = null,
    isAgentRunning: Boolean,
    onDraftChanged: (String) -> Unit,
    onSend: () -> Unit,
    onQuickAction: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .imePadding()
    ) {
        // Sleek status indicator when agent is working or model/ctx is available
        if (isAgentRunning || model.isNotBlank() || (contextTokens != null && contextTokens > 0)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAgentRunning) {
                        ThinkingIndicator(actionText = activeAction)
                    }

                    if (model.isNotBlank()) {
                        val shortModel = model.substringAfterLast("/")
                        Text(
                            text = shortModel,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                        )
                    }
                }

                if (contextTokens != null && contextTokens > 0) {
                    val tokenText = if (contextTokens >= 1_000_000) {
                        "%.1fM".format(contextTokens / 1_000_000.0)
                    } else if (contextTokens >= 1_000) {
                        "%.0fk".format(contextTokens / 1_000.0)
                    } else {
                        "$contextTokens"
                    }
                    Text(
                        text = "$tokenText tokens",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }

        // Minimal integrated input field with inline send button
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { newText ->
                    // Catch soft keyboards or ime that insert \n on Enter
                    if (newText.endsWith("\n") && !newText.endsWith("\n\n") && draft.isNotBlank() && !newText.contains("\n\n")) {
                        val trimmed = newText.trimEnd('\n')
                        if (trimmed.isNotBlank()) {
                            onDraftChanged(trimmed)
                            onSend()
                            return@OutlinedTextField
                        }
                    }
                    onDraftChanged(newText)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("chat_input")
                    .onKeyEvent { keyEvent ->
                        if (keyEvent.type == KeyEventType.KeyDown &&
                            (keyEvent.key == Key.Enter || keyEvent.key == Key.NumPadEnter)
                        ) {
                            if (keyEvent.isShiftPressed) {
                                // Allow newline on Shift+Enter
                                false
                            } else {
                                if (draft.isNotBlank()) {
                                    onSend()
                                }
                                true
                            }
                        } else {
                            false
                        }
                    },
                shape = RoundedCornerShape(26.dp),
                minLines = 1,
                maxLines = 6,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Send
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSend = {
                        if (draft.isNotBlank()) onSend()
                    }
                ),
                placeholder = { 
                    Text(
                        "Message $modelShortname…", 
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    ) 
                },
                trailingIcon = {
                    val canSend = draft.isNotBlank()
                    Surface(
                        onClick = { if (canSend) onSend() },
                        enabled = canSend,
                        shape = CircleShape,
                        color = if (canSend) MaterialTheme.colorScheme.primary else Color.Transparent,
                        contentColor = if (canSend) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .size(36.dp)
                            .testTag("chat_send"),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send, 
                                contentDescription = "Send",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data object Divider : MarkdownBlock
    data class Bullet(val indent: Int, val text: String) : MarkdownBlock
    data class NumberedItem(val number: String, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Code(val language: String?, val code: String) : MarkdownBlock
    data class Table(val headers: List<String>, val rows: List<List<String>>) : MarkdownBlock
    data class FileLink(val title: String, val path: String) : MarkdownBlock
}

private val FILE_LINK_REGEX = Regex("""^\[([^\]]+)\]\((file:///[^\)]+|/[^\)]+)\)$""")
private val INLINE_LINK_REGEX = Regex("""\[([^\]]+)\]\(([^\)]+)\)""")

@Composable
private fun MarkdownText(markdown: String, textStyle: TextStyle) {
    val blocks = remember(markdown) { parseMarkdown(markdown) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Heading -> {
                    val headingStyle = when (block.level) {
                        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    }
                    val annotated = remember(block.text) { inlineMarkdownToAnnotatedString(block.text) }
                    Text(
                        text = annotated,
                        style = headingStyle,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(top = if (block.level <= 2) 8.dp else 4.dp, bottom = 2.dp)
                    )
                }

                is MarkdownBlock.Divider -> {
                    Divider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }

                is MarkdownBlock.Bullet -> {
                    val annotated = remember(block.text) { inlineMarkdownToAnnotatedString(block.text) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = (block.indent * 16 + 4).dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "•",
                            style = textStyle.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = annotated,
                            style = textStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.NumberedItem -> {
                    val annotated = remember(block.text) { inlineMarkdownToAnnotatedString(block.text) }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${block.number}.",
                            style = textStyle.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = annotated,
                            style = textStyle,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                is MarkdownBlock.Paragraph -> {
                    val annotated = remember(block.text) { inlineMarkdownToAnnotatedString(block.text) }
                    Text(
                        text = annotated,
                        style = textStyle,
                    )
                }

                is MarkdownBlock.FileLink -> {
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    val context = androidx.compose.ui.platform.LocalContext.current
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                clipboard.setText(androidx.compose.ui.text.AnnotatedString(block.path))
                                android.widget.Toast.makeText(context, "Path copied: ${block.title}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = CircleShape,
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text("📄", style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = block.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    text = block.path.removePrefix("file://"),
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            Text(
                                text = "📋 Copy path",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                is MarkdownBlock.Code -> {
                    val label = block.language?.takeIf { it.isNotBlank() } ?: "code"
                    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = label,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (copied) "✓ Copied" else "📋 Copy",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.clickable {
                                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(block.code))
                                        copied = true
                                    }
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = block.code.trimEnd(),
                                style = textStyle.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                    }
                }

                is MarkdownBlock.Table -> {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp)
                        ) {
                            // Headers
                            Row(
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                for (h in block.headers) {
                                    Text(
                                        text = inlineMarkdownToAnnotatedString(h),
                                        style = textStyle.copy(fontWeight = FontWeight.Bold),
                                        modifier = Modifier.padding(horizontal = 8.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            // Rows
                            block.rows.forEachIndexed { idx, row ->
                                Row(
                                    modifier = Modifier
                                        .background(
                                            if (idx % 2 == 0) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                                            else androidx.compose.ui.graphics.Color.Transparent
                                        )
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    for (cell in row) {
                                        Text(
                                            text = inlineMarkdownToAnnotatedString(cell),
                                            style = textStyle,
                                            modifier = Modifier.padding(horizontal = 8.dp)
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
}

private fun parseMarkdown(source: String): List<MarkdownBlock> {
    val result = mutableListOf<MarkdownBlock>()
    val chunks = source.split("```")
    chunks.forEachIndexed { index, chunk ->
        if (index % 2 == 0) {
            if (chunk.isNotBlank()) {
                parseNonCodeChunk(chunk.trim(), result)
            }
        } else {
            val firstLineBreak = chunk.indexOf('\n')
            if (firstLineBreak >= 0) {
                val language = chunk.substring(0, firstLineBreak).trim().ifBlank { null }
                val code = chunk.substring(firstLineBreak + 1)
                result.add(MarkdownBlock.Code(language = language, code = code))
            } else {
                result.add(MarkdownBlock.Code(language = null, code = chunk))
            }
        }
    }
    if (result.isEmpty()) result += MarkdownBlock.Paragraph(source)
    return result
}

private val HEADING_REGEX = Regex("""^(#{1,6})\s+(.+)$""")
private val HR_REGEX = Regex("""^(?:-{3,}|\*{3,}|_{3,})$""")
private val BULLET_REGEX = Regex("""^(\s*)[*+-]\s+(.+)$""")
private val NUMBERED_REGEX = Regex("""^(\s*)(\d+)[.)]\s+(.+)$""")

private fun parseNonCodeChunk(text: String, out: MutableList<MarkdownBlock>) {
    val lines = text.lines()
    var i = 0
    val paraBuffer = mutableListOf<String>()

    fun flushPara() {
        if (paraBuffer.isNotEmpty()) {
            val p = paraBuffer.joinToString("\n").trim()
            if (p.isNotBlank()) {
                out.add(MarkdownBlock.Paragraph(p))
            }
            paraBuffer.clear()
        }
    }

    while (i < lines.size) {
        val rawLine = lines[i]
        val line = rawLine.trim()

        if (line.isEmpty()) {
            flushPara()
            i++
            continue
        }

        // Check horizontal rule (--- or *** or ___)
        if (HR_REGEX.matches(line)) {
            flushPara()
            out.add(MarkdownBlock.Divider)
            i++
            continue
        }

        // Check standalone file link
        val linkMatch = FILE_LINK_REGEX.matchEntire(line)
        if (linkMatch != null) {
            flushPara()
            val label = linkMatch.groupValues[1]
            val path = linkMatch.groupValues[2]
            out.add(MarkdownBlock.FileLink(title = label, path = path))
            i++
            continue
        }

        // Check heading (# H1, ## H2, ### H3, etc.)
        val headingMatch = HEADING_REGEX.matchEntire(line)
        if (headingMatch != null) {
            flushPara()
            val level = headingMatch.groupValues[1].length
            val title = headingMatch.groupValues[2].trim()
            out.add(MarkdownBlock.Heading(level = level, text = title))
            i++
            continue
        }

        // Check bullet lists (* item, - item, + item)
        val bulletMatch = BULLET_REGEX.matchEntire(rawLine)
        if (bulletMatch != null) {
            flushPara()
            val indentSpaces = bulletMatch.groupValues[1].length
            val indentLevel = (indentSpaces / 2).coerceAtMost(4)
            val content = bulletMatch.groupValues[2].trim()
            out.add(MarkdownBlock.Bullet(indent = indentLevel, text = content))
            i++
            continue
        }

        // Check numbered lists (1. item, 2. item)
        val numberedMatch = NUMBERED_REGEX.matchEntire(rawLine)
        if (numberedMatch != null) {
            flushPara()
            val num = numberedMatch.groupValues[2]
            val content = numberedMatch.groupValues[3].trim()
            out.add(MarkdownBlock.NumberedItem(number = num, text = content))
            i++
            continue
        }

        // Check if this line looks like a markdown table row (starts and ends with |)
        if (line.startsWith("|") && line.endsWith("|")) {
            // Case 1: Standard header row followed by separator row (e.g. |---|---|)
            if (i + 1 < lines.size && lines[i + 1].trim().startsWith("|") && lines[i + 1].trim().contains("---")) {
                flushPara()
                val headers = line.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val rows = mutableListOf<List<String>>()
                i += 2 // skip header and separator
                while (i < lines.size) {
                    val rowLine = lines[i].trim()
                    if (rowLine.startsWith("|") && rowLine.endsWith("|") && !rowLine.contains("---")) {
                        val cells = rowLine.split("|").map { it.trim() }.filterIndexed { idx, _ ->
                            idx > 0 && idx <= headers.size
                        }
                        rows.add(cells)
                        i++
                    } else {
                        break
                    }
                }
                out.add(MarkdownBlock.Table(headers, rows))
                continue
            }
            // Case 2: Separator row without preceding header (truncated scrollback)
            if (line.contains("---") && i + 1 < lines.size && lines[i + 1].trim().startsWith("|")) {
                flushPara()
                val nextLine = lines[i + 1].trim()
                val headers = nextLine.split("|").map { it.trim() }.filter { it.isNotEmpty() }
                val rows = mutableListOf<List<String>>()
                i += 2 // skip separator and first row as header
                while (i < lines.size) {
                    val rowLine = lines[i].trim()
                    if (rowLine.startsWith("|") && rowLine.endsWith("|") && !rowLine.contains("---")) {
                        val cells = rowLine.split("|").map { it.trim() }.filterIndexed { idx, _ ->
                            idx > 0 && idx <= headers.size
                        }
                        rows.add(cells)
                        i++
                    } else {
                        break
                    }
                }
                out.add(MarkdownBlock.Table(headers, rows))
                continue
            }
        }
        paraBuffer.add(rawLine)
        i++
    }
    flushPara()
}

private fun findBalancedLink(text: String, start: Int): Triple<String, String, Int>? {
    if (start >= text.length || text[start] != '[') return null

    // 1. Find closing bracket for [label]
    var depth = 0
    var closeBracket = -1
    var i = start
    while (i < text.length) {
        val c = text[i]
        if (c == '[') depth++
        else if (c == ']') {
            depth--
            if (depth == 0) {
                closeBracket = i
                break
            }
        }
        i++
    }
    if (closeBracket == -1 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') return null

    // 2. Find closing paren for (url) allowing nested parens in paths e.g. file:///path/(sub)
    val openParen = closeBracket + 1
    var parenDepth = 0
    var closeParen = -1
    var j = openParen
    while (j < text.length) {
        val c = text[j]
        if (c == '\n') break // Markdown links never span multiple lines
        if (c == '(') parenDepth++
        else if (c == ')') {
            parenDepth--
            if (parenDepth == 0) {
                closeParen = j
                break
            }
        }
        j++
    }
    if (closeParen == -1) return null

    val label = text.substring(start + 1, closeBracket)
    val url = text.substring(openParen + 1, closeParen)
    return Triple(label, url, closeParen + 1)
}

private fun inlineMarkdownToAnnotatedString(text: String) = buildAnnotatedString {
    var i = 0

    while (i < text.length) {
        when {
            // Markdown link: [text](url) or [`text`](url)
            text.startsWith("[", i) -> {
                val link = findBalancedLink(text, i)
                if (link != null) {
                    var label = link.first.trim()
                    val target = link.second.trim()
                    // Strip enclosing backticks inside label if present e.g. [`filename`](url) -> filename
                    var wasCode = false
                    if (label.startsWith("`") && label.endsWith("`") && label.length > 2) {
                        label = label.substring(1, label.length - 1)
                        wasCode = true
                    }
                    val isFile = target.startsWith("file:") || target.startsWith("/") || target.contains(".") && !target.startsWith("http")
                    withStyle(
                        SpanStyle(
                            color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = if (isFile || wasCode) FontFamily.Monospace else FontFamily.Default,
                            background = if (wasCode) androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.2f) else androidx.compose.ui.graphics.Color.Transparent
                        )
                    ) {
                        if (isFile) {
                            append("📄 $label")
                        } else {
                            append(label)
                        }
                    }
                    i = link.third
                } else {
                    append(text[i])
                    i += 1
                }
            }

            // Bold + Italic: ***text***
            text.startsWith("***", i) -> {
                val end = text.indexOf("***", i + 3)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(text.substring(i + 3, end))
                    }
                    i = end + 3
                } else {
                    append(text[i])
                    i += 1
                }
            }

            // Bold: **text**
            text.startsWith("**", i) -> {
                val end = text.indexOf("**", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i += 1
                }
            }

            // Italic: *text* (single asterisk)
            text.startsWith("*", i) -> {
                val end = text.indexOf('*', i + 1)
                if (end > i && end > i + 1 && !text[i + 1].isWhitespace()) {
                    withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i += 1
                }
            }

            // Inline code: `text`
            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.25f),
                        ),
                    ) {
                        append(text.substring(i + 1, end))
                    }
                    i = end + 1
                } else {
                    append(text[i])
                    i += 1
                }
            }

            // Strikethrough: ~~text~~
            text.startsWith("~~", i) -> {
                val end = text.indexOf("~~", i + 2)
                if (end > i) {
                    withStyle(SpanStyle(textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough)) {
                        append(text.substring(i + 2, end))
                    }
                    i = end + 2
                } else {
                    append(text[i])
                    i += 1
                }
            }

            else -> {
                append(text[i])
                i += 1
            }
        }
    }
}

fun deriveModelShortname(model: String, agentName: String = ""): String {
    val m = model.trim().substringAfterLast("/").substringBefore(":")
    val mLower = m.lowercase()
    return when {
        "astra" in mLower -> "Astra"
        "fable" in mLower -> "Fable"
        "nemotron" in mLower && "ultra" in mLower -> "Nemotron Ultra"
        "gpt" in mLower || "chatgpt" in mLower -> "GPT"
        "claude" in mLower -> "Claude"
        "gemini" in mLower -> "Gemini"
        "grok" in mLower -> "Grok"
        "qwen" in mLower -> "Qwen"
        "llama" in mLower -> "Llama"
        "mistral" in mLower || "mixtral" in mLower || "codestral" in mLower -> "Mistral"
        "deepseek" in mLower -> "DeepSeek"
        "nemotron" in mLower -> "Nemotron"
        "gemma" in mLower -> "Gemma"
        "sonnet" in mLower -> "Sonnet"
        "opus" in mLower -> "Opus"
        "haiku" in mLower -> "Haiku"
        "phi" in mLower -> "Phi"
        "command" in mLower -> "Command"
        "lfm" in mLower || "liquid" in mLower -> "Liquid"
        else -> {
            val token = m.replace('_', '-').split('-').firstOrNull { part -> part.any { it.isLetter() } }
            val letters = token?.filter { it.isLetter() } ?: ""
            if (letters.isNotBlank()) {
                letters.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            } else {
                when (agentName.trim().lowercase()) {
                    "agy", "gemini" -> "Gemini"
                    "codex" -> "GPT"
                    "claude" -> "Claude"
                    "grok" -> "Grok"
                    "under", "_" -> "Under"
                    "copilot" -> "Copilot"
                    else -> agentName.ifBlank { "Agent" }.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
            }
        }
    }
}

