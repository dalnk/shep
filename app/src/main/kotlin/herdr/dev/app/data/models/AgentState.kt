package herdr.dev.app.data.models

import kotlinx.serialization.Serializable

@Serializable
enum class AgentState {
    WORKING,
    BLOCKED,
    DONE,
    IDLE,
}

