package herdr.dev.app.data.socket

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class JsonRpcRequest(
    @SerialName("jsonrpc") val jsonRpc: String = "2.0",
    val id: String,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class JsonRpcResponse(
    @SerialName("jsonrpc") val jsonRpc: String = "2.0",
    val id: String,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
)

@Serializable
data class JsonRpcEvent(
    @SerialName("jsonrpc") val jsonRpc: String = "2.0",
    val method: String,
    val params: JsonElement? = null,
)

