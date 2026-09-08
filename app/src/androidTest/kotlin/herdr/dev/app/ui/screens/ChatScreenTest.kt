package herdr.dev.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class ChatScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun chatScreen_displaysWithInitialMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatScreen(
                    onNavigateBack = {},
                    initialMessage = "Test initial message"
                )
            }
        }

        // Should display the screen
        composeTestRule.onNodeWithText("Test initial message").assertIsDisplayed()
    }

    @Test
    fun chatScreen_displaysWithoutInitialMessage() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatScreen(
                    onNavigateBack = {},
                    initialMessage = null
                )
            }
        }

        // Should display empty chat state
        composeTestRule.onNodeWithText("No messages yet").assertIsDisplayed()
    }

    @Test
    fun chatScreen_displaysInputField() {
        composeTestRule.setContent {
            MaterialTheme {
                ChatScreen(
                    onNavigateBack = {},
                    initialMessage = null
                )
            }
        }

        // Should have input field
        composeTestRule.onNodeWithText("Message").assertIsDisplayed()
    }
}
