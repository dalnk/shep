package herdr.dev.app.viewmodel

import herdr.dev.app.data.ConnectionState
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.data.models.Pane
import herdr.dev.app.data.models.Tab
import herdr.dev.app.data.models.Workspace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    @Mock
    private lateinit var repository: HerdrSocketRepository

    private lateinit var viewModel: DashboardViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init connects to repository and refreshes workspaces`() = runTest {
        // Given
        val connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
        val workspaces = MutableStateFlow(emptyList<Workspace>())
        
        whenever(repository.connectionState).thenReturn(connectionState)
        whenever(repository.workspaces).thenReturn(workspaces)

        // When
        viewModel = DashboardViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(repository).connect()
        verify(repository).refreshWorkspaces()
    }

    @Test
    fun `uiState reflects connection state and workspaces`() = runTest {
        // Given
        val connectionState = MutableStateFlow(ConnectionState.CONNECTED)
        val workspaces = MutableStateFlow(
            listOf(
                Workspace(
                    id = "ws1",
                    name = "Test Workspace",
                    tabs = listOf(
                        Tab(
                            id = "tab1",
                            name = "Test Tab",
                            panes = listOf(
                                Pane(
                                    id = "pane1",
                                    title = "Test Pane",
                                    agentName = "Test Agent",
                                    state = AgentState.WORKING
                                )
                            )
                        )
                    )
                )
            )
        )
        
        whenever(repository.connectionState).thenReturn(connectionState)
        whenever(repository.workspaces).thenReturn(workspaces)

        // When
        viewModel = DashboardViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val state = viewModel.uiState.value
        assert(state.connectionState == ConnectionState.CONNECTED)
        assert(state.workspaces.size == 1)
        assert(state.workspaces[0].name == "Test Workspace")
    }

    @Test
    fun `sendQuickMessage calls repository sendInput`() = runTest {
        // Given
        val connectionState = MutableStateFlow(ConnectionState.CONNECTED)
        val workspaces = MutableStateFlow(emptyList<Workspace>())
        
        whenever(repository.connectionState).thenReturn(connectionState)
        whenever(repository.workspaces).thenReturn(workspaces)

        viewModel = DashboardViewModel(repository)
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        viewModel.sendQuickMessage("pane1", "Test message")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(repository).sendInput("pane1", "Test message")
    }
}
