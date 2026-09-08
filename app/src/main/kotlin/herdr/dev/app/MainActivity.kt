package herdr.dev.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import dagger.hilt.android.AndroidEntryPoint
import herdr.dev.app.data.SettingsRepository
import herdr.dev.app.services.HerdrMonitoringService
import herdr.dev.app.ui.HerdrApp
import herdr.dev.app.ui.HerdrTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
        )
        ContextCompat.startForegroundService(this, Intent(this, HerdrMonitoringService::class.java))

        handleDeepLink(intent)

        val launchPaneId = intent?.getStringExtra(EXTRA_PANE_ID)
        setContent {
            val accentIndex by settingsRepository.accentColorIndex.collectAsState(initial = 0)
            HerdrTheme(accentColorIndex = accentIndex) {
                HerdrApp(initialPaneId = launchPaneId)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: Intent?) {
        val data = intent?.data ?: return

        // Support both shep://pair?host=...&port=... and https://shep.work/pair?host=...&port=...
        // and fragment-based tokens like #h=neo.local&p=8765
        val host = data.getQueryParameter("host")
            ?: data.getQueryParameter("h")
            ?: parseFragmentParam(data.fragment, "h")
            ?: parseFragmentParam(data.fragment, "host")

        val port = data.getQueryParameter("port")
            ?: data.getQueryParameter("p")
            ?: parseFragmentParam(data.fragment, "p")
            ?: parseFragmentParam(data.fragment, "port")

        val tailcatKey = data.getQueryParameter("k")
            ?: data.getQueryParameter("key")
            ?: parseFragmentParam(data.fragment, "k")
            ?: parseFragmentParam(data.fragment, "key")

        if (!host.isNullOrBlank() || !tailcatKey.isNullOrBlank()) {
            lifecycleScope.launch {
                host?.let { settingsRepository.setServerHost(it) }
                port?.let { settingsRepository.setServerPort(it) }
                tailcatKey?.let { settingsRepository.setIrohNodeId(it) }
                settingsRepository.setOnboardingCompleted(true)
            }
        }
    }

    private fun parseFragmentParam(fragment: String?, key: String): String? {
        if (fragment.isNullOrBlank()) return null
        return fragment.split("&")
            .map { it.split("=", limit = 2) }
            .firstOrNull { it.size == 2 && it[0] == key }
            ?.get(1)
    }

    companion object {
        const val EXTRA_PANE_ID = "extra_pane_id"
    }
}
