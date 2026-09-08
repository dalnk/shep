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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
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

            // 2. Structured Tools (or parsed terminal tools)
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
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                        .clickable { toolsExpanded = !toolsExpanded }
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚡ ${allTools.size} tool call${if (allTools.size > 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = if (toolsExpanded) "▾ hide" else "▸ show details",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (toolsExpanded) {
                            Spacer(modifier = Modifier.height(6.dp))
                            for (tool in allTools.takeLast(10)) {
                                Text(
                                    text = tool,
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )
                            }
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
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            when (block) {
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
        val line = lines[i].trim()

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
        paraBuffer.add(lines[i])
        i++
    }
    flushPara()
}

private fun inlineMarkdownToAnnotatedString(text: String) = buildAnnotatedString {
    var i = 0

    while (i < text.length) {
        when {
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

            text.startsWith("`", i) -> {
                val end = text.indexOf('`', i + 1)
                if (end > i) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = androidx.compose.ui.graphics.Color.LightGray.copy(alpha = 0.35f),
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

            text.startsWith("[", i) -> {
                val match = INLINE_LINK_REGEX.find(text, i)
                if (match != null && match.range.first == i) {
                    val label = match.groupValues[1]
                    withStyle(
                        SpanStyle(
                            color = androidx.compose.ui.graphics.Color(0xFF1E88E5),
                            fontWeight = FontWeight.SemiBold
                        )
                    ) {
                        append("📄 $label")
                    }
                    i = match.range.last + 1
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

