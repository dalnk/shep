package herdr.dev.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import herdr.dev.app.ui.screens.ChatScreen
import herdr.dev.app.ui.screens.DashboardScreen
import herdr.dev.app.ui.screens.HomeScreen
import herdr.dev.app.ui.screens.SettingsScreen
import herdr.dev.app.viewmodel.HomeViewModel
import herdr.dev.app.viewmodel.MainViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

private const val ROUTE_HOME = "home"
private const val ROUTE_DASHBOARD = "dashboard"
private const val ROUTE_CHAT = "chat/{paneId}?initialMessage={initialMessage}"
private const val ROUTE_SETTINGS = "settings"

@Composable
fun HerdrApp(
    initialPaneId: String? = null,
    modifier: Modifier = Modifier,
    homeViewModel: HomeViewModel = hiltViewModel(),
) {
    LaunchedEffect(initialPaneId) {
        if (!initialPaneId.isNullOrBlank()) {
            homeViewModel.selectPane(initialPaneId)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Only show side-by-side dual pane if we are actually in landscape/wide orientation
        val isTabletLandscape = maxWidth >= 700.dp && maxWidth > maxHeight

        if (isTabletLandscape) {
            TwoPaneHerdrLayout(homeViewModel = homeViewModel)
        } else {
            SinglePaneHerdrLayout(homeViewModel = homeViewModel)
        }
    }
}

@Composable
private fun TwoPaneHerdrLayout(
    homeViewModel: HomeViewModel,
) {
    val workspaces by homeViewModel.workspaces.collectAsStateWithLifecycle()
    val allPanes = remember(workspaces) {
        workspaces.flatMap { it.tabs }.flatMap { it.panes }.distinctBy { it.id }
    }
    val currentSelectedPaneId by homeViewModel.selectedPaneId.collectAsStateWithLifecycle()

    // If no pane was selected initially, select the first available
    LaunchedEffect(allPanes, currentSelectedPaneId) {
        if (currentSelectedPaneId == null && allPanes.isNotEmpty()) {
            homeViewModel.selectPane(allPanes.first().id)
        }
    }

    var showSettings by remember { mutableStateOf(false) }
    var isSidebarCollapsed by remember { mutableStateOf(false) }

    Row(modifier = Modifier.fillMaxSize()) {
        // Left Master Pane (Sidebar: Home list + Agent status)
        if (!isSidebarCollapsed) {
            Surface(
                modifier = Modifier
                    .width(360.dp)
                    .fillMaxHeight(),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                tonalElevation = 1.dp
            ) {
                HomeScreen(
                    onNavigateToDashboard = { /* can show modal or inline */ },
                    onNavigateToChat = { paneId ->
                        homeViewModel.selectPane(paneId)
                        showSettings = false
                    },
                    onNavigateToSettings = {
                        showSettings = true
                    },
                    selectedPaneId = currentSelectedPaneId,
                    isSplitLayout = true,
                    isSidebarCollapsed = false,
                    onToggleSidebar = { isSidebarCollapsed = true },
                    viewModel = homeViewModel,
                )
            }

            // Subtle vertical divider line
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            )
        }

        // Right Detail Pane (Active Chat or Settings)
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(MaterialTheme.colorScheme.surface)
        ) {
            if (showSettings) {
                SettingsScreen(onNavigateBack = { showSettings = false })
            } else {
                val currentPane = currentSelectedPaneId
                if (currentPane != null) {
                    androidx.compose.runtime.key(currentPane) {
                        ChatScreen(
                            onNavigateBack = { /* No back needed in split mode */ },
                            paneId = currentPane,
                            showBackButton = isSidebarCollapsed,
                            onToggleSidebar = if (isSidebarCollapsed) {
                                { isSidebarCollapsed = false }
                            } else null,
                        )
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Text(
                                text = "Select an agent from the sidebar to view chat",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SinglePaneHerdrLayout(
    homeViewModel: HomeViewModel,
) {
    val navController = rememberNavController()
    val selectedPaneId by homeViewModel.selectedPaneId.collectAsStateWithLifecycle()

    // Restore or navigate to active thread upon switching to portrait mode
    LaunchedEffect(Unit) {
        val activePane = homeViewModel.selectedPaneId.value
        if (!activePane.isNullOrBlank()) {
            navController.navigate("chat/$activePane")
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_HOME,
        modifier = Modifier.fillMaxSize(),
    ) {
        composable(ROUTE_HOME) {
            HomeScreen(
                onNavigateToDashboard = { navController.navigate(ROUTE_DASHBOARD) },
                onNavigateToChat = { paneId ->
                    homeViewModel.selectPane(paneId)
                    navController.navigate("chat/$paneId")
                },
                onNavigateToSettings = { navController.navigate(ROUTE_SETTINGS) },
                viewModel = homeViewModel,
            )
        }
        composable(ROUTE_DASHBOARD) {
            DashboardScreen(
                onPaneClick = { paneId -> navController.navigate("chat/$paneId") },
                onNavigateBack = { navController.popBackStack() },
            )
        }
        composable(
            route = ROUTE_CHAT,
            arguments = listOf(
                navArgument("paneId") { type = NavType.StringType },
                navArgument("initialMessage") {
                    type = NavType.StringType
                    defaultValue = null
                    nullable = true
                }
            ),
        ) { backStackEntry ->
            val paneId = backStackEntry.arguments?.getString("paneId")
            val initialMessage = backStackEntry.arguments?.getString("initialMessage")
            ChatScreen(
                onNavigateBack = { navController.popBackStack() },
                paneId = paneId,
                initialMessage = initialMessage,
                showBackButton = true,
            )
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(onNavigateBack = { navController.popBackStack() })
        }
    }
}
