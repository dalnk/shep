package herdr.dev.app.data

import android.util.Log
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.ChatMessage
import herdr.dev.app.data.models.ChatQuestion
import herdr.dev.app.data.models.Pane
import herdr.dev.app.data.models.Tab
import herdr.dev.app.data.models.Workspace
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.Socket
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "HerdrSocketRepo"
private const val READ_TIMEOUT_MS = 30_000
private const val WRITE_TIMEOUT_MS = 5_000
private const val CONNECT_TIMEOUT_MS = 5_000

/**
 * The real client for the running herdr daemon, reached over TCP via the
 * herdr-bridge (bridge/herdr-bridge.py) which exposes the daemon's
 * Unix-socket protocol as a TCP port on the LAN.
 *
 * Wire protocol: NDJSON (newline-delimited JSON). One line per request,
 * one line per response or event. Methods/params match herdr's schema
 * exactly. See NOTES.md for the full protocol reference.
 */
@Singleton
class HerdrSocketRepositoryImpl @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : HerdrSocketRepository {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _workspaces = MutableStateFlow<List<Workspace>>(emptyList())
    override val workspaces: StateFlow<List<Workspace>> = _workspaces.asStateFlow()

    private val _events = MutableSharedFlow<HerdrEvent>(extraBufferCapacity = 64)
    override val events: Flow<HerdrEvent> = _events.asSharedFlow()

    // Per-pane event channels: readPaneOutput() returns a Flow for the
    // given pane. The reader loop feeds new pane.scroll_changed and
    private val paneChannels = ConcurrentHashMap<String, Channel<ChatMessage>>()
    private val transcriptCache = ConcurrentHashMap<String, PaneTranscriptResult>()

    // Connection lifecycle is single-threaded: the Activity and the
    // Service both call connect() on app start. We use a Mutex to
    // serialize the lifecycle so we don't open two sockets.
    private val connectionMutex = Mutex()

    // Request correlation. We use a monotonic counter for ids so
    // responses are easy to match even when many are in flight.
    private val nextId = AtomicLong(0)
    private val pending = ConcurrentHashMap<String, kotlinx.coroutines.CompletableDeferred<JsonObject>>()
    private val writeMutex = Mutex()

    private var readerJob: Job? = null
    private var socket: Socket? = null
    private var writer: OutputStream? = null
    private var currentHost = ""
    private var currentPort = 0

    init {
        // Track settings changes for diagnostics. The actual connect
        // call is explicit (user-driven from the home/settings screen).
        scope.launch {
            settingsRepository.serverHost.collect { currentHost = it }
        }
        scope.launch {
            settingsRepository.serverPort.collect { currentPort = it.toIntOrNull() ?: 8765 }
        }
    }

    override suspend fun connect() {
        connectionMutex.withLock {
            // Idempotent: if we are already connecting or connected, noop.
            if (_connectionState.value != ConnectionState.DISCONNECTED) return

            // Wait for settings to populate.
            if (currentHost.isBlank()) {
                currentHost = settingsRepository.serverHost.first()
            }
            if (currentPort == 0) {
                currentPort = settingsRepository.serverPort.first().toIntOrNull() ?: 8765
            }
            Log.i(TAG, "connecting to $currentHost:$currentPort")

            _connectionState.value = ConnectionState.CONNECTING
            try {
                withContext(Dispatchers.IO) { openSocket() }
                Log.i(TAG, "socket open, starting reader")
                startReader()
                // Subscribe to the events we care about. Only events that
                // do NOT require a specific pane_id go in the global sub
                // (pane-level events need a target pane, which we won't
                // know until after the first workspace.list).
                val subResult = request(
                    method = "events.subscribe",
                    params = buildJsonObject {
                        put(
                            "subscriptions",
                            kotlinx.serialization.json.buildJsonArray {
                                add(buildJsonObject {
                                    put("type", "pane.updated")
                                })
                                add(buildJsonObject {
                                    put("type", "pane.created")
                                })
                                add(buildJsonObject {
                                    put("type", "pane.closed")
                                })
                                add(buildJsonObject {
                                    put("type", "workspace.updated")
                                })
                            }
                        )
                    },
                )
                Log.i(TAG, "subscribe ack: $subResult")
                _connectionState.value = ConnectionState.CONNECTED
                refreshWorkspaces()
            } catch (e: Exception) {
                Log.e(TAG, "connect failed: ${e.message}", e)
                _connectionState.value = ConnectionState.DISCONNECTED
                closeSocket()
            }
        }
    }

    override suspend fun disconnect() {
        connectionMutex.withLock {
            closeSocket()
            _connectionState.value = ConnectionState.DISCONNECTED
        }
    }

    override suspend fun refreshWorkspaces() {
        try {
            val result = request("workspace.list", buildJsonObject {})
            val wsArray = result["workspaces"]?.jsonArray ?: return
            val workspacesList = mutableListOf<Workspace>()
            for (wsElement in wsArray) {
                val wsObj = wsElement.jsonObject
                val wsId = wsObj["workspace_id"]?.jsonPrimitive?.content ?: continue
                val label = wsObj["label"]?.jsonPrimitive?.content ?: "Workspace"
                val tabs = fetchTabsForWorkspace(wsId)
                workspacesList.add(Workspace(id = wsId, name = label, tabs = tabs))
            }
            _workspaces.value = workspacesList
        } catch (e: Exception) {
            Log.e(TAG, "refreshWorkspaces failed: ${e.message}", e)
        }
    }

    private suspend fun fetchTabsForWorkspace(workspaceId: String): List<Tab> {
        val result = try {
            request("tab.list", buildJsonObject { put("workspace_id", workspaceId) })
        } catch (e: Exception) {
            Log.e(TAG, "tab.list failed: ${e.message}")
            return emptyList()
        }
        val tabList = mutableListOf<Tab>()
        val tabsArray = result["tabs"]?.jsonArray ?: return emptyList()
        for (tabElement in tabsArray) {
            val tabObj = tabElement.jsonObject
            val tabId = tabObj["tab_id"]?.jsonPrimitive?.content ?: continue
            val label = tabObj["label"]?.jsonPrimitive?.content ?: "Tab"
            val panes = fetchPanesForTab(tabId)
            tabList.add(Tab(id = tabId, name = label, panes = panes))
        }
        return tabList
    }

    private suspend fun fetchPanesForTab(tabId: String): List<Pane> {
        val result = try {
            request("pane.list", buildJsonObject { put("tab_id", tabId) })
        } catch (e: Exception) {
            Log.e(TAG, "pane.list failed: ${e.message}")
            return emptyList()
        }
        val paneList = mutableListOf<Pane>()
        val panesArray = result["panes"]?.jsonArray ?: return emptyList()
        for (paneElement in panesArray) {
            val paneObj = paneElement.jsonObject
            val paneTabId = paneObj["tab_id"]?.jsonPrimitive?.contentOrNull()
            if (paneTabId != null && paneTabId != tabId) {
                continue
            }
            val paneId = paneObj["pane_id"]?.jsonPrimitive?.content ?: continue
            val rawAgent = paneObj["agent"]?.jsonPrimitive?.contentOrNull()
            val agent = if (rawAgent == "_") "under" else (rawAgent ?: "shell")
            val rawTitle = paneObj["title"]?.jsonPrimitive?.contentOrNull()
                ?: paneObj["name"]?.jsonPrimitive?.contentOrNull()
                ?: paneObj["terminal_title_stripped"]?.jsonPrimitive?.contentOrNull()
                ?: paneObj["terminal_title"]?.jsonPrimitive?.contentOrNull()
            val cwd = paneObj["cwd"]?.jsonPrimitive?.contentOrNull()
                ?: paneObj["foreground_cwd"]?.jsonPrimitive?.contentOrNull()
            val cwdBasename = cwd?.substringAfterLast('/')?.takeIf { it.isNotBlank() && it != "dalnk" && it != "Users" }

            val isRawPaneId = rawTitle.isNullOrBlank() || rawTitle == "-" || rawTitle == "_" ||
                rawTitle.matches(Regex("""^w\d+:p.*""")) || rawTitle.startsWith("Pane ")

            val title = if (!isRawPaneId && !rawTitle.isNullOrBlank()) {
                rawTitle
            } else if (cwdBasename != null) {
                "$cwdBasename · $agent"
            } else if (agent != "shell") {
                "${agent.replaceFirstChar { it.uppercase() }} session"
            } else {
                "Terminal"
            }
            val status = paneObj["agent_status"]?.jsonPrimitive?.contentOrNull()?.toAgentState()
                ?: AgentState.IDLE
            val waitingSince = paneObj["waiting_since_ms"]?.jsonPrimitive?.contentOrNull()?.toLongOrNull()
            val waitingDuration = paneObj["waiting_duration_seconds"]?.jsonPrimitive?.contentOrNull()?.toLongOrNull()
            paneList.add(
                Pane(
                    id = paneId,
                    title = title,
                    agentName = agent,
                    state = status,
                    waitingSinceMs = waitingSince,
                    waitingDurationSeconds = waitingDuration,
                )
            )
        }
        return paneList
    }

    override fun readPaneOutput(paneId: String): Flow<ChatMessage> {
        val channel = paneChannels.computeIfAbsent(paneId) {
            Channel(capacity = 32)
        }
        return channel.receiveAsFlow()
    }

    override suspend fun fetchPaneRecentText(paneId: String, lines: Int): String? {
        val result = try {
            request(
                method = "pane.read",
                params = buildJsonObject {
                    put("pane_id", paneId)
                    put("source", "recent")
                    put("lines", lines)
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchPaneRecentText failed for $paneId: ${e.message}")
            return null
        }
        return result["read"]?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull()
    }

    override suspend fun fetchPaneTranscript(paneId: String): PaneTranscriptResult? {
        val result = try {
            request(
                method = "pane.transcript",
                params = buildJsonObject {
                    put("pane_id", paneId)
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "fetchPaneTranscript failed for $paneId: ${e.message}")
            return null
        }
        val hasTranscript = result["has_transcript"]?.jsonPrimitive?.contentOrNull()?.toBooleanStrictOrNull() 
            ?: (result["has_transcript"]?.jsonPrimitive?.content == "true")
        if (!hasTranscript) return null

        val title = result["title"]?.jsonPrimitive?.contentOrNull()
        val agent = result["agent"]?.jsonPrimitive?.contentOrNull()
        val model = result["model"]?.jsonPrimitive?.contentOrNull()
        val modelShortname = result["model_shortname"]?.jsonPrimitive?.contentOrNull()
        val activeAction = result["active_action"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotBlank() && it != "null" }
        val contextTokens = result["context_tokens"]?.jsonPrimitive?.contentOrNull()?.toIntOrNull()
        val turnsArray = result["turns"]?.jsonArray ?: return null
        val questionElement = result["question"]
        val questionObj = if (questionElement is JsonObject) questionElement else null
        val chatQuestion = if (questionObj != null) {
            ChatQuestion(
                kind = questionObj["kind"]?.jsonPrimitive?.contentOrNull() ?: "choice",
                prompt = questionObj["prompt"]?.jsonPrimitive?.contentOrNull() ?: "",
                options = questionObj["options"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
            )
        } else null

        val messages = mutableListOf<ChatMessage>()
        for (i in 0 until turnsArray.size) {
            val turnElement = turnsArray[i]
            val turn = if (turnElement is JsonObject) turnElement else continue
            val role = turn["role"]?.jsonPrimitive?.contentOrNull() ?: "assistant"
            val text = turn["text"]?.jsonPrimitive?.contentOrNull() ?: ""
            val thinking = turn["thinking"]?.jsonPrimitive?.contentOrNull()
            val tools = turn["tools"]?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull() } ?: emptyList()
            val isLast = (i == turnsArray.size - 1)

            // Use a stable epoch offset based on turn index so ChatMessage equals/hash remains identical across poll cycles
            val stableTimestamp = i * 1000L
            messages.add(
                ChatMessage(
                    id = "transcript-$paneId-$i",
                    paneId = paneId,
                    text = text,
                    fromUser = (role == "user"),
                    createdAtMillis = stableTimestamp,
                    thinking = thinking,
                    tools = tools,
                    question = if (isLast) chatQuestion else null,
                )
            )
        }
        val res = PaneTranscriptResult(
            title = title,
            agentName = agent,
            model = model,
            modelShortname = modelShortname,
            contextTokens = contextTokens,
            activeAction = activeAction,
            messages = messages,
            question = chatQuestion,
        )
        transcriptCache[paneId] = res
        return res
    }

    override fun getCachedPaneTranscript(paneId: String): PaneTranscriptResult? {
        return transcriptCache[paneId]
    }

    override suspend fun sendKeys(paneId: String, keys: List<String>) {
        try {
            request(
                method = "pane.send_keys",
                params = buildJsonObject {
                    put("pane_id", paneId)
                    put("keys", buildJsonArray {
                        keys.forEach { add(it) }
                    })
                },
            )
        } catch (e: Exception) {
            Log.e(TAG, "sendKeys failed for $paneId: ${e.message}")
        }
    }

    override suspend fun sendInput(paneId: String, input: String) {
        // herdr terminal PTYs require explicit "enter" keypress for REPLs and agents
        // (like codex, underclass, agy, grok) to submit and trigger execution.
        val trimmed = input.trimEnd('\n', '\r')
        if (trimmed.isNotEmpty()) {
            sendText(paneId, trimmed)
        }
        sendKeys(paneId, listOf("enter"))
    }

    override suspend fun sendText(paneId: String, text: String) {
        request(
            method = "pane.send_text",
            params = buildJsonObject {
                put("pane_id", paneId)
                put("text", text)
            },
        )
    }

    override suspend fun startAgent(workspaceId: String, agentName: String) {
        request(
            method = "agent.start",
            params = buildJsonObject {
                put("workspace_id", workspaceId)
                put("agent", agentName)
                put("pane_id", workspaceId) // herdr requires pane_id; we use workspace as a placeholder
            },
        )
        refreshWorkspaces()
    }

    // ----- low-level socket I/O -----

    private fun openSocket() {
        val configuredHost = currentHost
        val port = currentPort
        if (configuredHost.isBlank() || port == 0) {
            throw IllegalStateException("host/port not configured")
        }

        // Build candidate host list: configured host first, then fallback to neo.local or 10.0.0.244
        val candidates = linkedSetOf<String>()
        candidates.add(configuredHost)
        if (configuredHost != "neo.local") {
            candidates.add("neo.local")
        }
        if (configuredHost != "10.0.0.244") {
            candidates.add("10.0.0.244")
        }

        var lastException: Exception? = null
        for (host in candidates) {
            val addresses = try {
                java.net.InetAddress.getAllByName(host).sortedBy { if (it is java.net.Inet4Address) 0 else 1 }
            } catch (e: Exception) {
                Log.w(TAG, "DNS resolution failed for $host: ${e.message}")
                listOf()
            }
            for (addr in addresses) {
                try {
                    Log.i(TAG, "attempting socket connection to $host ($addr):$port")
                    val s = Socket()
                    s.soTimeout = 0 // Keep-alive socket: do not time out while waiting for events/requests
                    s.connect(java.net.InetSocketAddress(addr, port), CONNECT_TIMEOUT_MS)
                    socket = s
                    writer = s.getOutputStream()
                    currentHost = host
                    Log.i(TAG, "successfully connected to $host ($addr):$port")
                    return
                } catch (e: Exception) {
                    Log.w(TAG, "failed connecting to $host ($addr):$port: ${e.message}")
                    lastException = e
                }
            }
        }
        throw lastException ?: IllegalStateException("Unable to connect to any candidate host")
    }

    private fun startReader() {
        readerJob?.cancel()
        readerJob = scope.launch {
            val s = socket ?: return@launch
            val reader = BufferedReader(InputStreamReader(s.getInputStream()))
            try {
                while (isActive) {
                    val line = reader.readLine() ?: break // EOF
                    if (line.isBlank()) continue
                    val frame = try {
                        json.parseToJsonElement(line).jsonObject
                    } catch (e: Exception) {
                        Log.w(TAG, "dropping malformed frame: ${e.message}")
                        continue
                    }
                    handleFrame(frame)
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "reader error: ${e.message}", e)
                }
            } finally {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        }
    }

    private fun closeSocket() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
        writer = null
        readerJob?.cancel()
        readerJob = null
    }

    private suspend fun request(method: String, params: JsonObject): JsonObject {
        val id = nextId.incrementAndGet().toString()
        val frame = buildJsonObject {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val deferred = kotlinx.coroutines.CompletableDeferred<JsonObject>()
        pending[id] = deferred
        try {
            writeFrame(frame)
            val response = withTimeoutOrNull(15_000) { deferred.await() }
                ?: throw RuntimeException("herdr timeout: $method (id=$id)")
            // Resolve result or throw on error.
            if (response["error"] != null) {
                throw RuntimeException("herdr error: ${response["error"]}")
            }
            return response["result"]?.jsonObject ?: buildJsonObject {}
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun writeFrame(frame: JsonElement) {
        val s = socket ?: throw IllegalStateException("not connected")
        val w = writer ?: throw IllegalStateException("no writer")
        val line = json.encodeToString(JsonElement.serializer(), frame) + "\n"
        withContext(Dispatchers.IO) {
            writeMutex.withLock {
                try {
                    w.write(line.toByteArray(Charsets.UTF_8))
                    w.flush()
                } catch (e: Exception) {
                    closeSocket()
                    throw e
                }
            }
        }
    }

    private fun handleFrame(frame: JsonObject) {
        val id = frame["id"]?.jsonPrimitive?.contentOrNull()?.takeIf { it.isNotEmpty() }
        if (id != null) {
            // Response to a pending request.
            val deferred = pending.remove(id)
            if (deferred != null) {
                deferred.complete(frame)
            } else {
                Log.w(TAG, "response with no matching request: id=$id")
            }
            return
        }
        // Event notification.
        val event = frame["event"]?.jsonPrimitive?.contentOrNull() ?: return
        val data = frame["data"]?.jsonObject ?: return
        when (event) {
            "pane.scroll_changed" -> {
                // We don't have a new text payload here, just a scroll
                // delta. The chat UI can use this to know something
                // happened. For now, treat it as a hint and ignore.
            }
            "pane.agent_status_changed" -> {
                val paneId = data["pane_id"]?.jsonPrimitive?.contentOrNull() ?: return
                val stateStr = data["state"]?.jsonPrimitive?.contentOrNull()
                    ?: data["agent_status"]?.jsonPrimitive?.contentOrNull()
                if (stateStr != null) {
                    _events.tryEmit(
                        HerdrEvent.AgentStateChanged(
                            paneId = paneId,
                            previous = AgentState.IDLE,
                            current = stateStr.toAgentState(),
                        )
                    )
                }
            }
            "pane.updated" -> {
                // Workspace state changed; refetch pane list.
                scope.launch { refreshWorkspaces() }
            }
            "workspace.updated" -> {
                scope.launch { refreshWorkspaces() }
            }
            "pane.output_matched", "pane.scroll_changed" -> { /* ignore */ }
            else -> {
                // Unknown event type — ignore.
            }
        }
    }

    private fun String.toAgentState(): AgentState = when (this.lowercase()) {
        "working" -> AgentState.WORKING
        "blocked" -> AgentState.BLOCKED
        "done" -> AgentState.DONE
        "idle", "unknown" -> AgentState.IDLE
        else -> AgentState.IDLE
    }
}

// Tiny helper: contentOrNull on jsonPrimitive so we can do chained null-safe access.
private fun kotlinx.serialization.json.JsonPrimitive.contentOrNull(): String? =
    try { this.content } catch (_: Exception) { null }
