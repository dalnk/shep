package herdr.dev.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import herdr.dev.app.data.ConnectionState
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.SettingsRepository
import herdr.dev.app.data.DangerLevel
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.Pane
import herdr.dev.app.data.models.Workspace
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: HerdrSocketRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val connectionState: StateFlow<ConnectionState> = repository.connectionState
    val workspaces: StateFlow<List<Workspace>> = repository.workspaces
    val dangerLevel: StateFlow<DangerLevel> = settingsRepository.dangerLevel
        .stateIn(viewModelScope, SharingStarted.Eagerly, DangerLevel.NORMAL)

    private val _lastAutoProceedMessage = MutableStateFlow<String?>(null)
    val lastAutoProceedMessage: StateFlow<String?> = _lastAutoProceedMessage.asStateFlow()

    private val _selectedPaneId = MutableStateFlow<String?>(null)
    val selectedPaneId: StateFlow<String?> = _selectedPaneId.asStateFlow()

    fun selectPane(paneId: String?) {
        _selectedPaneId.value = paneId
    }

    private val autoProceededPanes = mutableMapOf<String, Long>()

    init {
        viewModelScope.launch {
            repository.connect()
            repository.refreshWorkspaces()
        }
        viewModelScope.launch {
            repository.events.collect {
                repository.refreshWorkspaces()
            }
        }
        viewModelScope.launch {
            workspaces.collect { ws ->
                checkAndAutoProceed(ws, dangerLevel.value)
            }
        }
        viewModelScope.launch {
            dangerLevel.collect { dl ->
                checkAndAutoProceed(workspaces.value, dl)
            }
        }
        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                if (connectionState.value == ConnectionState.CONNECTED) {
                    repository.refreshWorkspaces()
                    checkAndAutoProceed(workspaces.value, dangerLevel.value)
                }
            }
        }
    }

    private fun checkAndAutoProceed(workspacesList: List<Workspace>, level: DangerLevel) {
        if (level != DangerLevel.DANGERMAXXING) return
        val allPanes = workspacesList.flatMap { it.tabs }.flatMap { it.panes }.distinctBy { it.id }
        val blockedPanes = allPanes.filter { it.state == AgentState.BLOCKED }
        val now = System.currentTimeMillis()

        for (pane in blockedPanes) {
            val lastProceed = autoProceededPanes[pane.id] ?: 0L
            // Cooldown of 6 seconds per pane so the agent has time to register and process
            if (now - lastProceed > 6000L) {
                autoProceededPanes[pane.id] = now
                val action = if (pane.agentName.lowercase() == "copilot") "y" else "p"
                sendQuickAction(pane.id, action)
                _lastAutoProceedMessage.value = "⚡ Dangermaxxing: Auto-proceeded ${pane.title.ifBlank { pane.agentName }} ($action)"
            }
        }
    }
    
    fun refresh() {
        viewModelScope.launch {
            repository.refreshWorkspaces()
        }
    }

    fun reconnect() {
        viewModelScope.launch {
            repository.disconnect()
            repository.connect()
        }
    }

    private val _dismissedTaskIds = kotlinx.coroutines.flow.MutableStateFlow<Set<String>>(emptySet())
    val dismissedTaskIds: StateFlow<Set<String>> = _dismissedTaskIds.asStateFlow()

    fun dismissTask(paneId: String) {
        _dismissedTaskIds.value = _dismissedTaskIds.value + paneId
    }

    fun sendQuickAction(paneId: String, action: String) {
        viewModelScope.launch {
            when (action.lowercase()) {
                "y" -> repository.sendInput(paneId, "y")
                "n" -> repository.sendInput(paneId, "n")
                "enter" -> repository.sendInput(paneId, "")
                "esc" -> repository.sendKeys(paneId, listOf("esc"))
                "ctrl+c" -> repository.sendKeys(paneId, listOf("ctrl+c"))
                else -> repository.sendInput(paneId, action)
            }
            repository.refreshWorkspaces()
        }
    }
}
