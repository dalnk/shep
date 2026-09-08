package herdr.dev.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import herdr.dev.app.data.SettingsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.ConnectionState
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.Workspace

data class SettingsUiState(
    val serverHost: String = "",
    val serverPort: String = "",
    val connectionType: herdr.dev.app.data.ConnectionType = herdr.dev.app.data.ConnectionType.LOCAL,
    val dangerLevel: herdr.dev.app.data.DangerLevel = herdr.dev.app.data.DangerLevel.NORMAL,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val totalWorkspaces: Int = 0,
    val totalPanes: Int = 0,
    val activePanesCount: Int = 0,
    val waitingPanesCount: Int = 0,
    val donePanesCount: Int = 0,
    val activeAgents: List<String> = emptyList(),
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val socketRepository: HerdrSocketRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.serverHost.collect { host ->
                _uiState.update { it.copy(serverHost = host) }
            }
        }
        viewModelScope.launch {
            settingsRepository.serverPort.collect { port ->
                _uiState.update { it.copy(serverPort = port) }
            }
        }
        viewModelScope.launch {
            settingsRepository.connectionType.collect { type ->
                _uiState.update { it.copy(connectionType = type) }
            }
        }
        viewModelScope.launch {
            settingsRepository.dangerLevel.collect { level ->
                _uiState.update { it.copy(dangerLevel = level) }
            }
        }
        viewModelScope.launch {
            socketRepository.connectionState.collect { connState ->
                _uiState.update { it.copy(connectionState = connState) }
            }
        }
        viewModelScope.launch {
            socketRepository.workspaces.collect { workspaces ->
                val allPanes = workspaces.flatMap { it.tabs }.flatMap { it.panes }.distinctBy { it.id }
                val activeCount = allPanes.count { it.state == AgentState.WORKING }
                val waitingCount = allPanes.count { it.state == AgentState.BLOCKED }
                val doneCount = allPanes.count { it.state == AgentState.DONE }
                val agents = allPanes.map { it.agentName }.filter { it.isNotBlank() && it != "shell" }.distinct()

                _uiState.update {
                    it.copy(
                        totalWorkspaces = workspaces.size,
                        totalPanes = allPanes.size,
                        activePanesCount = activeCount,
                        waitingPanesCount = waitingCount,
                        donePanesCount = doneCount,
                        activeAgents = agents,
                    )
                }
            }
        }
    }

    fun updateServerHost(host: String) {
        viewModelScope.launch {
            settingsRepository.setServerHost(host)
        }
    }

    fun updateServerPort(port: String) {
        viewModelScope.launch {
            settingsRepository.setServerPort(port)
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            socketRepository.disconnect()
            socketRepository.connect()
            socketRepository.refreshWorkspaces()
        }
    }

    fun updateConnectionType(type: herdr.dev.app.data.ConnectionType) {
        viewModelScope.launch {
            settingsRepository.setConnectionType(type)
        }
    }

    fun updateDangerLevel(level: herdr.dev.app.data.DangerLevel) {
        viewModelScope.launch {
            settingsRepository.setDangerLevel(level)
        }
    }
}
