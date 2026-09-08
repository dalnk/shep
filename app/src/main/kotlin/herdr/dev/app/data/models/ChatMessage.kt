package herdr.dev.app.data.models

data class ChatQuestion(
    val kind: String = "input",
    val prompt: String = "",
    val options: List<String> = emptyList(),
)

data class ChatMessage(
    val id: String,
    val paneId: String,
    val text: String,
    val fromUser: Boolean,
    val createdAtMillis: Long,
    val thinking: String? = null,
    val tools: List<String> = emptyList(),
    val question: ChatQuestion? = null,
)

