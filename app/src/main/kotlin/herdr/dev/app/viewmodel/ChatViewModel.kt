package herdr.dev.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.models.ChatMessage
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class ChatUiState(
    val paneId: String = "",
    val paneTitle: String = "",
    val agentName: String = "",
    val model: String = "",
    val modelShortname: String = "",
    val contextTokens: Int? = null,
    val activeAction: String? = null,
    val isAgentRunning: Boolean = false,
    val messages: List<ChatMessage> = emptyList(),
    val draft: String = "",
    val isLoadingHistory: Boolean = true,
)

@HiltViewModel
class ChatViewModel @Inject constructor(
    private val repository: HerdrSocketRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private var pollingIntervalMillis: Long = 2000L

    constructor(
        repository: HerdrSocketRepository,
        savedStateHandle: SavedStateHandle,
        pollingIntervalMillis: Long,
    ) : this(repository, savedStateHandle) {
        this.pollingIntervalMillis = pollingIntervalMillis
        startObservingPane(activePaneId)
    }

    private var activePaneId: String = savedStateHandle.get<String>("paneId") ?: ""
    private val _uiState = MutableStateFlow(ChatUiState(paneId = activePaneId))
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    private var lastObservedTerminalText: String? = null
    private var fetchOutputJob: Job? = null
    private var observeJob: Job? = null

    init {
        viewModelScope.launch {
            repository.connect()
        }
        startObservingPane(activePaneId)
    }

    fun setPaneId(newPaneId: String) {
        if (newPaneId == activePaneId) return
        activePaneId = newPaneId
        lastObservedTerminalText = null

        // Instant warm cache rehydration: immediately load known messages without blanking out
        val cached = repository.getCachedPaneTranscript(newPaneId)
        if (cached != null && cached.messages.isNotEmpty()) {
            _uiState.value = ChatUiState(
                paneId = newPaneId,
                paneTitle = cached.title ?: "",
                agentName = cached.agentName ?: "",
                model = cached.model ?: "",
                modelShortname = cached.modelShortname ?: "",
                contextTokens = cached.contextTokens,
                activeAction = cached.activeAction,
                messages = cached.messages,
                isLoadingHistory = false,
            )
        } else {
            _uiState.value = ChatUiState(paneId = newPaneId, isLoadingHistory = true)
        }
        startObservingPane(newPaneId)
    }

    private fun startObservingPane(targetId: String) {
        observeJob?.cancel()
        fetchOutputJob?.cancel()
        if (targetId.isBlank()) return

        // Initial warm hydration if state was empty
        if (_uiState.value.messages.isEmpty()) {
            val cached = repository.getCachedPaneTranscript(targetId)
            if (cached != null && cached.messages.isNotEmpty()) {
                _uiState.update { current ->
                    current.copy(
                        paneTitle = cached.title ?: current.paneTitle,
                        agentName = cached.agentName ?: current.agentName,
                        model = cached.model ?: current.model,
                        modelShortname = cached.modelShortname ?: current.modelShortname,
                        contextTokens = cached.contextTokens ?: current.contextTokens,
                        activeAction = cached.activeAction ?: current.activeAction,
                        messages = cached.messages,
                        isLoadingHistory = false,
                    )
                }
            }
        }

        observeJob = viewModelScope.launch {
            launch {
                repository.workspaces.collect { workspaces ->
                    val pane = workspaces.flatMap { it.tabs }.flatMap { it.panes }
                        .find { it.id == targetId }
                    pane?.let { p ->
                        _uiState.update { 
                            it.copy(
                                paneTitle = p.title, 
                                agentName = p.agentName,
                                isAgentRunning = (p.state == herdr.dev.app.data.models.AgentState.WORKING),
                            ) 
                        }
                    }
                }
            }
            launch {
                repository.readPaneOutput(targetId).collect { message ->
                    _uiState.update { current ->
                        // Deduplicate: avoid appending duplicate user echo if already present
                        val alreadyHas = current.messages.any { 
                            it.fromUser == message.fromUser && it.text.trim() == message.text.trim() 
                        }
                        if (alreadyHas) current
                        else current.copy(messages = current.messages + message)
                    }
                }
            }
        }

        if (pollingIntervalMillis > 0) {
            fetchOutputJob = viewModelScope.launch {
                while (isActive) {
                    fetchLatestTerminalOutput()
                    delay(pollingIntervalMillis)
                }
            }
        }
    }

    private suspend fun fetchLatestTerminalOutput() {
        if (activePaneId.isBlank()) return
        // Try structured harness transcript first (Codex, Claude, Under, Grok, Antigravity)
        val transcriptResult = repository.fetchPaneTranscript(activePaneId)
        if (transcriptResult != null && transcriptResult.messages.isNotEmpty()) {
            _uiState.update { current ->
                val transcriptUserTexts = transcriptResult.messages.filter { it.fromUser }.map { it.text.trim() }.toSet()
                val userSentUnconfirmed = current.messages.filter { 
                    it.fromUser && 
                    !it.id.startsWith("transcript-") && 
                    it.text.trim() !in transcriptUserTexts 
                }
                val newMessages = transcriptResult.messages + userSentUnconfirmed
                val newTitle = if (!transcriptResult.title.isNullOrBlank()) transcriptResult.title else current.paneTitle
                val newAgent = if (!transcriptResult.agentName.isNullOrBlank()) transcriptResult.agentName else current.agentName
                val newModel = transcriptResult.model ?: current.model
                val newShortname = transcriptResult.modelShortname ?: current.modelShortname
                val newTokens = transcriptResult.contextTokens ?: current.contextTokens
                val newAction = transcriptResult.activeAction ?: current.activeAction

                if (current.messages == newMessages &&
                    current.paneTitle == newTitle &&
                    current.agentName == newAgent &&
                    current.model == newModel &&
                    current.modelShortname == newShortname &&
                    current.contextTokens == newTokens &&
                    current.activeAction == newAction &&
                    !current.isLoadingHistory) {
                    current
                } else {
                    current.copy(
                        paneTitle = newTitle,
                        agentName = newAgent,
                        model = newModel,
                        modelShortname = newShortname,
                        contextTokens = newTokens,
                        activeAction = newAction,
                        messages = newMessages,
                        isLoadingHistory = false,
                    )
                }
            }
            return
        }

        // Fallback to raw terminal scrollback
        val text = repository.fetchPaneRecentText(activePaneId, lines = 200) ?: return
        if (text.isBlank() || text == lastObservedTerminalText) {
            _uiState.update { it.copy(isLoadingHistory = false) }
            return
        }
        lastObservedTerminalText = text
        _uiState.update { current ->
            val nonHistory = current.messages.filter { it.id != "history-$activePaneId" }
            val historyMsg = ChatMessage(
                id = "history-$activePaneId",
                paneId = activePaneId,
                text = text.trimEnd(),
                fromUser = false,
                createdAtMillis = System.currentTimeMillis(),
            )
            current.copy(
                messages = listOf(historyMsg) + nonHistory,
                isLoadingHistory = false,
            )
        }
    }

    fun onDraftChanged(value: String) {
        _uiState.update { it.copy(draft = value) }
    }

    fun sendDraft() {
        val value = _uiState.value.draft.trim()
        if (value.isBlank() || activePaneId.isBlank()) return

        // Optimistic UI update (instant user bubble & thinking indicator like t3.codes)
        val optimisticMsg = ChatMessage(
            id = "optimistic-${System.currentTimeMillis()}",
            paneId = activePaneId,
            text = value,
            fromUser = true,
            createdAtMillis = System.currentTimeMillis(),
        )
        _uiState.update { current ->
            current.copy(
                draft = "",
                isAgentRunning = true,
                messages = current.messages + optimisticMsg,
            )
        }

        viewModelScope.launch {
            repository.sendInput(activePaneId, value)
            delay(150)
            fetchLatestTerminalOutput()
        }
    }

    fun sendQuickAction(action: String) {
        if (activePaneId.isBlank()) return

        // Optimistically clear the pending question and show user reply immediately
        _uiState.update { current ->
            val updatedMessages = current.messages.map { msg ->
                if (msg.question != null) msg.copy(question = null) else msg
            }
            val optimisticReply = ChatMessage(
                id = "optimistic-quick-${System.currentTimeMillis()}",
                paneId = activePaneId,
                text = action,
                fromUser = true,
                createdAtMillis = System.currentTimeMillis(),
            )
            current.copy(
                isAgentRunning = true,
                messages = updatedMessages + optimisticReply,
            )
        }

        viewModelScope.launch {
            when (action.lowercase()) {
                "y" -> repository.sendInput(activePaneId, "y")
                "n" -> repository.sendInput(activePaneId, "n")
                "enter" -> repository.sendInput(activePaneId, "")
                "esc" -> repository.sendKeys(activePaneId, listOf("esc"))
                "ctrl+c" -> repository.sendKeys(activePaneId, listOf("ctrl+c"))
                else -> repository.sendInput(activePaneId, action)
            }
            delay(300)
            fetchLatestTerminalOutput()
        }
    }

    public override fun onCleared() {
        super.onCleared()
        observeJob?.cancel()
        fetchOutputJob?.cancel()
    }
}
