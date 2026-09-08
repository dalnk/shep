package herdr.dev.app.viewmodel

import herdr.dev.app.data.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.ConnectionState
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @Mock
    private lateinit var settingsRepository: SettingsRepository

    @Mock
    private lateinit var socketRepository: HerdrSocketRepository

    private lateinit var viewModel: SettingsViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)
        whenever(socketRepository.connectionState).thenReturn(MutableStateFlow(ConnectionState.DISCONNECTED))
        whenever(socketRepository.workspaces).thenReturn(MutableStateFlow(emptyList()))
        whenever(settingsRepository.dangerLevel).thenReturn(MutableStateFlow(herdr.dev.app.data.DangerLevel.NORMAL))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init loads server host from repository`() = runTest {
        // Given
        val hostFlow = MutableStateFlow("10.0.0.82")
        val portFlow = MutableStateFlow("8765")
        
        whenever(settingsRepository.serverHost).thenReturn(hostFlow)
        whenever(settingsRepository.serverPort).thenReturn(portFlow)
        whenever(settingsRepository.irohNodeId).thenReturn(MutableStateFlow(""))
        whenever(settingsRepository.connectionType).thenReturn(MutableStateFlow(herdr.dev.app.data.ConnectionType.LOCAL))

        // When
        viewModel = SettingsViewModel(settingsRepository, socketRepository)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(viewModel.uiState.value.serverHost == "10.0.0.82")
        assert(viewModel.uiState.value.serverPort == "8765")
    }

    @Test
    fun `updateServerHost calls repository setServerHost`() = runTest {
        // Given
        val hostFlow = MutableStateFlow("10.0.0.82")
        val portFlow = MutableStateFlow("8765")
        
        whenever(settingsRepository.serverHost).thenReturn(hostFlow)
        whenever(settingsRepository.serverPort).thenReturn(portFlow)
        whenever(settingsRepository.irohNodeId).thenReturn(MutableStateFlow(""))
        whenever(settingsRepository.connectionType).thenReturn(MutableStateFlow(herdr.dev.app.data.ConnectionType.LOCAL))
        viewModel = SettingsViewModel(settingsRepository, socketRepository)

        // When
        viewModel.updateServerHost("192.168.1.1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(settingsRepository).setServerHost("192.168.1.1")
    }

    @Test
    fun `updateServerPort calls repository setServerPort`() = runTest {
        // Given
        val hostFlow = MutableStateFlow("10.0.0.82")
        val portFlow = MutableStateFlow("8765")
        
        whenever(settingsRepository.serverHost).thenReturn(hostFlow)
        whenever(settingsRepository.serverPort).thenReturn(portFlow)
        whenever(settingsRepository.irohNodeId).thenReturn(MutableStateFlow(""))
        whenever(settingsRepository.connectionType).thenReturn(MutableStateFlow(herdr.dev.app.data.ConnectionType.LOCAL))
        viewModel = SettingsViewModel(settingsRepository, socketRepository)

        // When
        viewModel.updateServerPort("9000")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        verify(settingsRepository).setServerPort("9000")
    }

    @Test
    fun `uiState updates when repository host changes`() = runTest {
        // Given
        val hostFlow = MutableStateFlow("10.0.0.82")
        val portFlow = MutableStateFlow("8765")
        
        whenever(settingsRepository.serverHost).thenReturn(hostFlow)
        whenever(settingsRepository.serverPort).thenReturn(portFlow)
        whenever(settingsRepository.irohNodeId).thenReturn(MutableStateFlow(""))
        whenever(settingsRepository.connectionType).thenReturn(MutableStateFlow(herdr.dev.app.data.ConnectionType.LOCAL))
        viewModel = SettingsViewModel(settingsRepository, socketRepository)

        // When
        hostFlow.value = "192.168.1.1"
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(viewModel.uiState.value.serverHost == "192.168.1.1")
    }

    @Test
    fun `uiState updates when repository port changes`() = runTest {
        // Given
        val hostFlow = MutableStateFlow("10.0.0.82")
        val portFlow = MutableStateFlow("8765")
        
        whenever(settingsRepository.serverHost).thenReturn(hostFlow)
        whenever(settingsRepository.serverPort).thenReturn(portFlow)
        whenever(settingsRepository.irohNodeId).thenReturn(MutableStateFlow(""))
        whenever(settingsRepository.connectionType).thenReturn(MutableStateFlow(herdr.dev.app.data.ConnectionType.LOCAL))
        viewModel = SettingsViewModel(settingsRepository, socketRepository)

        // When
        portFlow.value = "9000"
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(viewModel.uiState.value.serverPort == "9000")
    }
}
