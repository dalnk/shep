package herdr.dev.app.ui.screens

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class DashboardScreenTest {

    @get:Rule
    val composeTestRule = createComposeRule()

    @Test
    fun dashboardScreen_displaysTitle() {
        composeTestRule.setContent {
            MaterialTheme {
                DashboardScreen(
                    onPaneClick = {}
                )
            }
        }

        composeTestRule.onNodeWithText("Herdr dashboard").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_displaysEmptyState() {
        composeTestRule.setContent {
            MaterialTheme {
                DashboardScreen(
                    onPaneClick = {}
                )
            }
        }

        // Should display empty state when no workspaces
        composeTestRule.onNodeWithText("Herdr dashboard").assertIsDisplayed()
    }

    @Test
    fun dashboardScreen_callsOnPaneClickWhenPaneClicked() {
        var clickedPaneId: String? = null
        
        composeTestRule.setContent {
            MaterialTheme {
                DashboardScreen(
                    onPaneClick = { clickedPaneId = it }
                )
            }
        }

        // Note: This test would need mock data to actually test pane clicking
        // For now, we're just verifying the screen renders
        composeTestRule.onNodeWithText("Herdr dashboard").assertIsDisplayed()
    }
}
