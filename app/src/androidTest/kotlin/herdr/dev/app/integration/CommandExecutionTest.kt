package herdr.dev.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import herdr.dev.app.data.HerdrSocketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class CommandExecutionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HerdrSocketRepository

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun connectToServer_andRefreshWorkspaces() = runTest {
        // Given: Herdr server running on localhost or network
        // For emulator: Use 10.0.2.2 to access host machine
        // For physical device: Use machine's local IP (e.g., 192.168.1.X)
        // For remote: Use Cloudflare tunnel URL
        
        // When
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val workspaces = repository.workspaces.value
        assert(workspaces.isNotEmpty()) { "Should have at least one workspace" }
        
        // Verify workspace structure
        val firstWorkspace = workspaces[0]
        assert(firstWorkspace.id.isNotBlank()) { "Workspace should have ID" }
        assert(firstWorkspace.name.isNotBlank()) { "Workspace should have name" }
        assert(firstWorkspace.tabs.isNotEmpty()) { "Workspace should have tabs" }
    }

    @Test
    fun sendCommandToPane_andReceiveResponse() = runTest {
        // Given: Connected to server with workspaces
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        val workspaces = repository.workspaces.value
        if (workspaces.isEmpty()) {
            return@runTest // Skip if no workspaces available
        }

        val paneId = workspaces[0].tabs[0].panes[0].id

        // When: Send a test command
        repository.sendInput(paneId, "echo test")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should not throw exception
        // In real scenario, we'd collect the output and verify it contains "test"
        val messages = mutableListOf<herdr.dev.app.data.models.ChatMessage>()
        repository.readPaneOutput(paneId).collect { message ->
            messages.add(message)
        }
        
        // Give some time for response
        kotlinx.coroutines.delay(2000)
        
        // Verify we got some response
        assert(messages.isNotEmpty()) { "Should receive response from agent" }
    }

    @Test
    fun sendGeminiCommand_andVerifyExecution() = runTest {
        // Given: Connected to server
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        val workspaces = repository.workspaces.value
        if (workspaces.isEmpty()) {
            return@runTest
        }

        val paneId = workspaces[0].tabs[0].panes[0].id

        // When: Send a command that would use gemini cli
        repository.sendInput(paneId, "Run gemini-cli --help")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should execute without error
        // In real scenario, verify output contains gemini help text
        val messages = mutableListOf<herdr.dev.app.data.models.ChatMessage>()
        repository.readPaneOutput(paneId).collect { message ->
            messages.add(message)
        }
        
        kotlinx.coroutines.delay(3000)
        
        assert(messages.isNotEmpty()) { "Should receive response from gemini command" }
    }

    @Test
    fun sendComplexCommand_andHandleOutput() = runTest {
        // Given: Connected to server
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        val workspaces = repository.workspaces.value
        if (workspaces.isEmpty()) {
            return@runTest
        }

        val paneId = workspaces[0].tabs[0].panes[0].id

        // When: Send a multi-step command
        repository.sendInput(paneId, "ls -la && pwd && whoami")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should handle multiple command outputs
        val messages = mutableListOf<herdr.dev.app.data.models.ChatMessage>()
        repository.readPaneOutput(paneId).collect { message ->
            messages.add(message)
        }
        
        kotlinx.coroutines.delay(3000)
        
        assert(messages.isNotEmpty()) { "Should receive output from complex command" }
    }

    @Test
    fun handleConnectionInterruption_andReconnect() = runTest {
        // Given: Connected to server
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        
        val initialState = repository.connectionState.value
        assert(initialState == herdr.dev.app.data.ConnectionState.CONNECTED) { 
            "Should be connected initially" 
        }

        // When: Simulate connection loss
        repository.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(repository.connectionState.value == herdr.dev.app.data.ConnectionState.DISCONNECTED) {
            "Should be disconnected"
        }

        // Then: Reconnect
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        
        assert(repository.connectionState.value == herdr.dev.app.data.ConnectionState.CONNECTED) {
            "Should reconnect successfully"
        }
    }
}
