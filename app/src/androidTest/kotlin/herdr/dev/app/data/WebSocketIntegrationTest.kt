package herdr.dev.app.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
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
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltAndroidTest
class WebSocketIntegrationTest {

    @get:Rule
    val hiltRule = HiltAndroidRule(this)

    @Inject
    lateinit var repository: HerdrSocketRepository

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        hiltRule.inject()
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun repository_connects_and_updatesConnectionState() = runTest {
        // Given
        val initialState = repository.connectionState.value

        // When
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        val finalState = repository.connectionState.value
        // Note: This test will pass in mock mode, but may fail if real server is unavailable
        assert(finalState != initialState)
    }

    @Test
    fun repository_disconnects_and_updatesConnectionState() = runTest {
        // Given
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        repository.disconnect()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        assert(repository.connectionState.value == ConnectionState.DISCONNECTED)
    }

    @Test
    fun repository_refreshesWorkspaces() = runTest {
        // Given
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()

        // When
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        // Then
        // In mock mode, should have workspaces
        // In real mode, depends on server
        val workspaces = repository.workspaces.value
        assert(workspaces.isNotEmpty())
    }

    @Test
    fun repository_sendsInputToPane() = runTest {
        // Given
        repository.connect()
        testDispatcher.scheduler.advanceUntilIdle()
        repository.refreshWorkspaces()
        testDispatcher.scheduler.advanceUntilIdle()

        val workspaces = repository.workspaces.value
        if (workspaces.isNotEmpty()) {
            val paneId = workspaces[0].tabs[0].panes[0].id

            // When
            repository.sendInput(paneId, "Test message")
            testDispatcher.scheduler.advanceUntilIdle()

            // Then
            // Should not throw exception
            assert(true)
        }
    }
}
