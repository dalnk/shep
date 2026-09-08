package herdr.dev.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Tab(
    val id: String,
    val name: String,
    val panes: List<Pane>,
)

