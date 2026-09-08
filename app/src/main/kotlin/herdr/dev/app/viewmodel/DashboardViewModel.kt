package herdr.dev.app.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import herdr.dev.app.data.ConnectionState
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.models.Workspace
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

data class DashboardUiState(
    val isLoading: Boolean = true,
    val connectionState: ConnectionState = ConnectionState.DISCONNECTED,
    val workspaces: List<Workspace> = emptyList(),
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val repository: HerdrSocketRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repository.connect()
            repository.refreshWorkspaces()
        }

        viewModelScope.launch {
            combine(repository.connectionState, repository.workspaces) { connection, workspaces ->
                android.util.Log.d("DashboardVM", "State update: conn=$connection, workspaces=${workspaces.size}")
                DashboardUiState(
                    isLoading = connection == ConnectionState.CONNECTING,
                    connectionState = connection,
                    workspaces = workspaces,
                )
            }.collect { state -> _uiState.value = state }
        }
    }

    fun sendQuickMessage(paneId: String, message: String) {
        viewModelScope.launch {
            repository.sendInput(paneId, message)
        }
    }

    fun startAgent(workspaceId: String, agentName: String) {
        viewModelScope.launch {
            repository.startAgent(workspaceId, agentName)
        }
    }
}
