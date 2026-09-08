package herdr.dev.app.data

import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.ChatMessage
import herdr.dev.app.data.models.Workspace
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

data class PaneTranscriptResult(
    val title: String? = null,
    val agentName: String? = null,
    val model: String? = null,
    val modelShortname: String? = null,
    val contextTokens: Int? = null,
    val activeAction: String? = null,
    val messages: List<ChatMessage> = emptyList(),
    val question: herdr.dev.app.data.models.ChatQuestion? = null,
)

interface HerdrSocketRepository {
    val connectionState: StateFlow<ConnectionState>
    val workspaces: StateFlow<List<Workspace>>
    val events: Flow<HerdrEvent>

    suspend fun connect()
    suspend fun disconnect()
    suspend fun refreshWorkspaces()
    fun readPaneOutput(paneId: String): Flow<ChatMessage>
    suspend fun fetchPaneRecentText(paneId: String, lines: Int = 60): String?
    suspend fun fetchPaneTranscript(paneId: String): PaneTranscriptResult?
    fun getCachedPaneTranscript(paneId: String): PaneTranscriptResult?
    suspend fun sendKeys(paneId: String, keys: List<String>)
    suspend fun sendInput(paneId: String, input: String)
    suspend fun sendText(paneId: String, text: String)
    suspend fun startAgent(workspaceId: String, agentName: String)
}

enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

sealed interface HerdrEvent {
    data class AgentStateChanged(
        val paneId: String,
        val previous: AgentState,
        val current: AgentState,
    ) : HerdrEvent

    data class PaneOutputReceived(
        val paneId: String,
        val content: String,
    ) : HerdrEvent
}

