package herdr.dev.app.data

import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.ChatMessage
import herdr.dev.app.data.models.Pane
import herdr.dev.app.data.models.Tab
import herdr.dev.app.data.models.Workspace
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

@Singleton
class MockHerdrSocketRepository @Inject constructor() : HerdrSocketRepository {
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    private val _workspaces = MutableStateFlow(seedWorkspaces())
    private val _events = MutableSharedFlow<HerdrEvent>(extraBufferCapacity = 64)
    private val paneMessages = MutableSharedFlow<ChatMessage>(replay = 100, extraBufferCapacity = 100)
    @Volatile
    private var simulationStarted = false

    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()
    override val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()
    
    init {
        android.util.Log.d("MockRepo", "Initialized with ${seedWorkspaces().size} workspaces")
    }
    override val events: Flow<HerdrEvent> = _events.asSharedFlow()

    override suspend fun connect() {
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.CONNECTING) return
        _connectionState.value = ConnectionState.CONNECTING
        delay(400)
        _connectionState.value = ConnectionState.CONNECTED
        refreshWorkspaces()
        if (!simulationStarted) {
            simulationStarted = true
            startSimulationLoop()
        }
    }

    override suspend fun disconnect() {
        _connectionState.value = ConnectionState.DISCONNECTED
    }

    override suspend fun refreshWorkspaces() {
        _workspaces.value = _workspaces.value.sortedBy { workspace ->
            workspace.tabs.flatMap { it.panes }.minOfOrNull { pane ->
                when (pane.state) {
                    AgentState.BLOCKED -> 0
                    AgentState.DONE -> 1
                    AgentState.WORKING -> 2
                    AgentState.IDLE -> 3
                }
            } ?: 10
        }
    }

    override fun readPaneOutput(paneId: String): Flow<ChatMessage> {
        val seed = listOf(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                paneId = paneId,
                text = "Connected to pane `$paneId`.\n\n```bash\nherdr pane.read $paneId\n```",
                fromUser = false,
                createdAtMillis = System.currentTimeMillis() - 180_000,
            ),
        )
        return paneMessages
            .filter { it.paneId == paneId }
            .onStart { seed.forEach { emit(it) } }
    }

    override suspend fun fetchPaneRecentText(paneId: String, lines: Int): String? {
        return "Recent output for $paneId\nReady for input."
    }

    override suspend fun fetchPaneTranscript(paneId: String): PaneTranscriptResult? {
        return null
    }

    override fun getCachedPaneTranscript(paneId: String): PaneTranscriptResult? {
        return null
    }

    override suspend fun sendKeys(paneId: String, keys: List<String>) {
        sendInput(paneId, keys.joinToString(" "))
    }

    override suspend fun sendInput(paneId: String, input: String) {
        paneMessages.emit(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                paneId = paneId,
                text = input,
                fromUser = true,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )

        val response = buildString {
            append("Acknowledged input for `$paneId`.\n\n")
            append("```text\n")
            append(input.trim())
            append("\n```\n\n")
            append("Continuing execution…")
        }
        paneMessages.emit(
            ChatMessage(
                id = UUID.randomUUID().toString(),
                paneId = paneId,
                text = response,
                fromUser = false,
                createdAtMillis = System.currentTimeMillis() + 350,
            ),
        )
        _events.emit(HerdrEvent.PaneOutputReceived(paneId = paneId, content = response))
    }

    override suspend fun sendText(paneId: String, text: String) = sendInput(paneId, text)

    override suspend fun startAgent(workspaceId: String, agentName: String) {
        delay(1000)
        val newPaneId = "pane-${UUID.randomUUID().toString().take(4)}"
        val newPane = Pane(
            id = newPaneId,
            title = "New $agentName Agent",
            agentName = agentName,
            state = AgentState.WORKING
        )
        
        val updated = _workspaces.value.map { workspace ->
            if (workspace.id == workspaceId) {
                workspace.copy(
                    tabs = if (workspace.tabs.isEmpty()) {
                        listOf(Tab(id = "tab-default", name = "Default", panes = listOf(newPane)))
                    } else {
                        workspace.tabs.mapIndexed { index, tab ->
                            if (index == 0) tab.copy(panes = tab.panes + newPane) else tab
                        }
                    }
                )
            } else {
                workspace
            }
        }
        _workspaces.value = updated
        _events.emit(HerdrEvent.AgentStateChanged(paneId = newPaneId, previous = AgentState.IDLE, current = AgentState.WORKING))
    }

    private fun startSimulationLoop() {
        appScope.launch {
            while (_connectionState.value == ConnectionState.CONNECTED) {
                delay(12.seconds)
                val trackedPane = "pane-auth"
                updatePaneState(trackedPane, AgentState.BLOCKED)
                paneMessages.emit(
                    ChatMessage(
                        id = UUID.randomUUID().toString(),
                        paneId = trackedPane,
                        text = "I need a decision on auth flow before continuing.",
                        fromUser = false,
                        createdAtMillis = System.currentTimeMillis(),
                    ),
                )
                delay(10.seconds)
                updatePaneState(trackedPane, AgentState.WORKING)
                delay(10.seconds)
                updatePaneState(trackedPane, AgentState.DONE)
            }
        }
    }

    private suspend fun updatePaneState(paneId: String, targetState: AgentState) {
        val current = _workspaces.value
        var previousState: AgentState? = null
        val updated = current.map { workspace ->
            workspace.copy(
                tabs = workspace.tabs.map { tab ->
                    tab.copy(
                        panes = tab.panes.map { pane ->
                            if (pane.id == paneId) {
                                previousState = pane.state
                                pane.copy(state = targetState)
                            } else {
                                pane
                            }
                        },
                    )
                },
            )
        }
        _workspaces.value = updated
        val previous = previousState ?: return
        _events.emit(HerdrEvent.AgentStateChanged(paneId = paneId, previous = previous, current = targetState))
    }

    private fun seedWorkspaces(): List<Workspace> = listOf(
        Workspace(
            id = "ws-mobile",
            name = "herdr-android",
            tabs = listOf(
                Tab(
                    id = "tab-ui",
                    name = "UI",
                    panes = listOf(
                        Pane(
                            id = "pane-auth",
                            title = "Authentication flow",
                            agentName = "OpenCode",
                            state = AgentState.WORKING,
                        ),
                        Pane(
                            id = "pane-dashboard",
                            title = "Dashboard polishing",
                            agentName = "OpenCode",
                            state = AgentState.IDLE,
                        ),
                    ),
                ),
            ),
        ),
        Workspace(
            id = "ws-backend",
            name = "herdr-server",
            tabs = listOf(
                Tab(
                    id = "tab-socket",
                    name = "Socket integration",
                    panes = listOf(
                        Pane(
                            id = "pane-events",
                            title = "events.subscribe wire-up",
                            agentName = "OpenCode",
                            state = AgentState.BLOCKED,
                        ),
                        Pane(
                            id = "pane-tests",
                            title = "Contract tests",
                            agentName = "OpenCode",
                            state = AgentState.DONE,
                        ),
                    ),
                ),
            ),
        ),
    )
}
