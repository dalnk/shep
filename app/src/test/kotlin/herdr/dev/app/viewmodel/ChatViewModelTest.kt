package herdr.dev.app.viewmodel

import androidx.lifecycle.SavedStateHandle
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.models.ChatMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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
import org.mockito.kotlin.never
import org.mockito.kotlin.eq
import org.mockito.kotlin.any

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    @Mock
    private lateinit var repository: HerdrSocketRepository

    private lateinit var viewModel: ChatViewModel

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        Dispatchers.setMain(testDispatcher)

        // ChatViewModel.init collects workspaces and polls output
        whenever(repository.workspaces).thenReturn(MutableStateFlow(emptyList()))
        whenever(repository.readPaneOutput(any())).thenReturn(MutableSharedFlow())
        whenever(repository.getCachedPaneTranscript(any())).thenReturn(null)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `init subscribes to pane output`() = runTest {
        // Given
        val savedStateHandle = SavedStateHandle(mapOf("paneId" to "pane1"))
        val messageFlow = MutableSharedFlow<ChatMessage>()
        
        whenever(repository.readPaneOutput("pane1")).thenReturn(messageFlow)

        // When
        viewModel = ChatViewModel(repository, savedStateHandle)
        testScheduler.advanceTimeBy(100)

        // Then
        verify(repository).readPaneOutput("pane1")
    }

    @Test
    fun `uiState contains correct paneId`() = runTest {
        // Given
        val savedStateHandle = SavedStateHandle(mapOf("paneId" to "pane1"))
        val messageFlow = MutableSharedFlow<ChatMessage>()
        
        whenever(repository.readPaneOutput("pane1")).thenReturn(messageFlow)

        // When
        viewModel = ChatViewModel(repository, savedStateHandle)

        // Then
        assert(viewModel.uiState.value.paneId == "pane1")
    }

    @Test
    fun `onDraftChanged updates draft in uiState`() = runTest {
        // Given
        val savedStateHandle = SavedStateHandle(mapOf("paneId" to "pane1"))
        val messageFlow = MutableSharedFlow<ChatMessage>()
        
        whenever(repository.readPaneOutput("pane1")).thenReturn(messageFlow)
        viewModel = ChatViewModel(repository, savedStateHandle)

        // When
        viewModel.onDraftChanged("Test message")

        // Then
        assert(viewModel.uiState.value.draft == "Test message")
    }

    @Test
    fun `sendDraft calls repository sendInput and clears draft`() = runTest {
        // Given
        val savedStateHandle = SavedStateHandle(mapOf("paneId" to "pane1"))
        val messageFlow = MutableSharedFlow<ChatMessage>()
        
        whenever(repository.readPaneOutput("pane1")).thenReturn(messageFlow)
        viewModel = ChatViewModel(repository, savedStateHandle)
        viewModel.onDraftChanged("Test message")

        // When
        viewModel.sendDraft()
        testScheduler.advanceTimeBy(100)

        // Then
        verify(repository).sendInput("pane1", "Test message")
        assert(viewModel.uiState.value.draft == "")
    }

    @Test
    fun `sendDraft does nothing for blank draft`() = runTest {
        // Given
        val savedStateHandle = SavedStateHandle(mapOf("paneId" to "pane1"))
        val messageFlow = MutableSharedFlow<ChatMessage>()

        whenever(repository.readPaneOutput("pane1")).thenReturn(messageFlow)
        viewModel = ChatViewModel(repository, savedStateHandle)
        viewModel.onDraftChanged("   ")

        // When
        viewModel.sendDraft()
        testScheduler.advanceTimeBy(100)

        // Then
        verify(repository, never()).sendInput(eq("pane1"), any())
        assert(viewModel.uiState.value.draft == "   ")
    }
}
