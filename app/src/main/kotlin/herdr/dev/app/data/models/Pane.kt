package herdr.dev.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Pane(
    val id: String,
    val title: String,
    val agentName: String,
    val state: AgentState,
    val waitingSinceMs: Long? = null,
    val waitingDurationSeconds: Long? = null,
)

