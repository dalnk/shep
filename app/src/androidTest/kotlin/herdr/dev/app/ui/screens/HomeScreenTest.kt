package herdr.dev.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import org.junit.Rule
import org.junit.Test

class HomeScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun homeScreen_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToDashboard = {},
                    onNavigateToChat = {},
                    onNavigateToSettings = {},
                    onNavigateToFileExplorer = {},
                    onNavigateToSearch = {},
                    onNavigateToSourceControl = {},
                    onNavigateToExtensions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Herdr").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysAskAnythingSection() {
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToDashboard = {},
                    onNavigateToChat = {},
                    onNavigateToSettings = {},
                    onNavigateToFileExplorer = {},
                    onNavigateToSearch = {},
                    onNavigateToSourceControl = {},
                    onNavigateToExtensions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Ask anything").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysActionButtons() {
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToDashboard = {},
                    onNavigateToChat = {},
                    onNavigateToSettings = {},
                    onNavigateToFileExplorer = {},
                    onNavigateToSearch = {},
                    onNavigateToSourceControl = {},
                    onNavigateToExtensions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Open project").assertIsDisplayed()
        composeTestRule.onNodeWithText("Clone repo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Connect via SSH").assertIsDisplayed()
    }

    @Test
    fun homeScreen_displaysSessionSuggestions() {
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToDashboard = {},
                    onNavigateToChat = {},
                    onNavigateToSettings = {},
                    onNavigateToFileExplorer = {},
                    onNavigateToSearch = {},
                    onNavigateToSourceControl = {},
                    onNavigateToExtensions = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Start new session").assertIsDisplayed()
    }

    @Test
    fun homeScreen_navigatesToSettingsOnSettingsClick() {
        var settingsClicked = false
        
        composeTestRule.setContent {
            MaterialTheme {
                HomeScreen(
                    onNavigateToDashboard = {},
                    onNavigateToChat = {},
                    onNavigateToSettings = { settingsClicked = true },
                    onNavigateToFileExplorer = {},
                    onNavigateToSearch = {},
                    onNavigateToSourceControl = {},
                    onNavigateToExtensions = {}
                )
            }
        }

        composeTestRule.onNodeWithContentDescription("Settings").performClick()
        assert(settingsClicked)
    }
}
