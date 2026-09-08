package herdr.dev.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import dagger.hilt.android.AndroidEntryPoint
import herdr.dev.app.services.HerdrMonitoringService
import herdr.dev.app.ui.HerdrApp
import herdr.dev.app.ui.HerdrTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        ContextCompat.startForegroundService(this, Intent(this, HerdrMonitoringService::class.java))
        val launchPaneId = intent?.getStringExtra(EXTRA_PANE_ID)
        setContent {
            HerdrTheme {
                HerdrApp(initialPaneId = launchPaneId)
            }
        }
    }

    companion object {
        const val EXTRA_PANE_ID = "extra_pane_id"
    }
}
