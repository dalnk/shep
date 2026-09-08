package herdr.dev.app.integration

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import herdr.dev.app.viewmodel.SSHConnectionViewModel
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
class SSHCommandExecutionTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var sshViewModel: SSHConnectionViewModel

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var context: Context

    // Test server credentials - configure these for actual testing
    // For local testing: Use 10.0.2.2 (emulator) or 192.168.1.X (physical device on same network)
    private val testHost = "10.0.2.2" // Emulator accesses host machine via this IP
    private val testPort = 22
    private val testUsername = System.getenv("SSH_TEST_USER") ?: "your-username"
    private val testPassword = System.getenv("SSH_TEST_PASS") ?: "your-password"

    @Before
    fun setup() {
        hiltRule.inject()
        context = ApplicationProvider.getApplicationContext()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        sshViewModel.disconnect()
    }

    @Test
    fun sshConnect_andExecuteSimpleCommand() = runTest {
        // Given: Test server credentials (configure these for actual testing)
        // Skip if credentials not configured
        if (testHost == "your-test-server.com") {
            return@runTest // Skip test in CI/CD without credentials
        }

        // When: Connect to SSH server
        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should be connected
        val state = sshViewModel.connectionState.value
        assert(state is SSHConnectionViewModel.ConnectionState.Connected) {
            "Should be connected to SSH server"
        }

        // When: Execute simple command
        val output = sshViewModel.executeCommand("echo 'hello from ssh'")

        // Then: Should receive output
        assert(output.contains("hello from ssh")) {
            "Command output should contain expected text. Got: $output"
        }
    }

    @Test
    fun sshExecute_geminiCommand() = runTest {
        // Given: Connected to SSH server
        if (testHost == "your-test-server.com") {
            return@runTest
        }

        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Execute gemini command
        val output = sshViewModel.executeCommand("which gemini-cli")

        // Then: Should show gemini-cli path or indicate not found
        assert(output.isNotBlank()) {
            "Should receive output from command. Got: $output"
        }
    }

    @Test
    fun sshExecute_multipleCommands() = runTest {
        // Given: Connected to SSH server
        if (testHost == "your-test-server.com") {
            return@runTest
        }

        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Execute multiple commands
        val pwdOutput = sshViewModel.executeCommand("pwd")
        val lsOutput = sshViewModel.executeCommand("ls -la")
        val whoamiOutput = sshViewModel.executeCommand("whoami")

        // Then: All should succeed
        assert(pwdOutput.isNotBlank()) { "pwd should return output" }
        assert(lsOutput.isNotBlank()) { "ls should return output" }
        assert(whoamiOutput.isNotBlank()) { "whoami should return output" }
    }

    @Test
    fun sshHandle_invalidCredentials() = runTest {
        // Given: Invalid credentials
        if (testHost == "your-test-server.com") {
            return@runTest
        }

        // When: Try to connect with wrong password
        sshViewModel.connect(testHost, testPort, testUsername, "wrongpassword")
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should fail
        val state = sshViewModel.connectionState.value
        assert(state is SSHConnectionViewModel.ConnectionState.Error) {
            "Should fail with invalid credentials"
        }
    }

    @Test
    fun sshHandle_connectionLoss_andReconnect() = runTest {
        // Given: Connected to SSH server
        if (testHost == "your-test-server.com") {
            return@runTest
        }

        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Disconnect
        sshViewModel.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should be idle
        assert(sshViewModel.connectionState.value is SSHConnectionViewModel.ConnectionState.Idle) {
            "Should be idle after disconnect"
        }

        // When: Reconnect
        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // Then: Should be connected again
        assert(sshViewModel.connectionState.value is SSHConnectionViewModel.ConnectionState.Connected) {
            "Should reconnect successfully"
        }
    }

    @Test
    fun sshExecute_longRunningCommand() = runTest {
        // Given: Connected to SSH server
        if (testHost == "your-test-server.com") {
            return@runTest
        }

        sshViewModel.connect(testHost, testPort, testUsername, testPassword)
        testDispatcher.scheduler.advanceUntilIdle()

        // When: Execute command that takes time
        val output = sshViewModel.executeCommand("sleep 2 && echo 'done'")

        // Then: Should complete
        assert(output.contains("done")) {
            "Long running command should complete. Got: $output"
        }
    }
}
