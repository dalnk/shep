package herdr.dev.app.services

import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import herdr.dev.app.data.HerdrEvent
import herdr.dev.app.data.HerdrSocketRepository
import herdr.dev.app.data.models.AgentState
import herdr.dev.app.notifications.HerdrNotificationManager
import javax.inject.Inject
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

@AndroidEntryPoint
class HerdrMonitoringService : LifecycleService() {
    @Inject
    lateinit var repository: HerdrSocketRepository

    @Inject
    lateinit var notificationManager: HerdrNotificationManager

    @Inject
    lateinit var settingsRepository: herdr.dev.app.data.SettingsRepository

    override fun onCreate() {
        super.onCreate()
        notificationManager.ensureChannels()
        startForeground(1001, notificationManager.buildForegroundMonitoringNotification())
        
        lifecycleScope.launch {
            repository.connect()
        }

        lifecycleScope.launch {
            repository.workspaces.collect { workspaces ->
                val allPanes = workspaces.flatMap { it.tabs }.flatMap { it.panes }.distinctBy { it.id }
                val activeCount = allPanes.count { it.state == AgentState.WORKING }
                val attentionCount = allPanes.count { it.state == AgentState.BLOCKED }
                val doneCount = allPanes.count { it.state == AgentState.DONE }
                
                val notification = notificationManager.buildForegroundMonitoringNotification(
                    activeCount = activeCount,
                    attentionCount = attentionCount,
                    doneCount = doneCount,
                )
                val manager = getSystemService(android.app.NotificationManager::class.java)
                manager.notify(1001, notification)
            }
        }

        lifecycleScope.launch {
            repository.events.collect { event ->
                if (event is HerdrEvent.AgentStateChanged &&
                    event.previous == AgentState.WORKING &&
                    (event.current == AgentState.BLOCKED || event.current == AgentState.DONE)
                ) {
                    val allPanes = repository.workspaces.value.flatMap { it.tabs }.flatMap { it.panes }
                    val targetPane = allPanes.find { it.id == event.paneId }
                    val currentDangerLevel = settingsRepository.dangerLevel.first()

                    if (event.current == AgentState.BLOCKED && currentDangerLevel == herdr.dev.app.data.DangerLevel.DANGERMAXXING) {
                        // Dangermaxxing: automatically proceed paused agent!
                        val action = if (targetPane?.agentName?.lowercase() == "copilot") "y" else "p"
                        repository.sendInput(event.paneId, action)
                        notificationManager.notifyAttentionNeeded(
                            paneId = event.paneId,
                            currentState = event.current,
                            agentName = targetPane?.agentName ?: "Agent",
                            titleExcerpt = "⚡ Auto-proceeded (${action}) via Dangermaxxing",
                        )
                    } else {
                        notificationManager.notifyAttentionNeeded(
                            paneId = event.paneId,
                            currentState = event.current,
                            agentName = targetPane?.agentName ?: "Agent",
                            titleExcerpt = targetPane?.title ?: "",
                        )
                    }
                }
            }
        }
    }
}
