package herdr.dev.app.data.models

import kotlinx.serialization.Serializable

@Serializable
data class Workspace(
    val id: String,
    val name: String,
    val tabs: List<Tab>,
)

