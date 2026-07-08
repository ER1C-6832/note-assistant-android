package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import javax.inject.Inject
import org.json.JSONObject

class XiaozhiMessageRouter @Inject constructor(
    private val mcpProtocolClient: McpProtocolClient,
) {
    fun routeText(raw: String): ProtocolEvent {
        val json = runCatching { JSONObject(raw) }.getOrNull()
            ?: return ProtocolEvent.ProtocolError(raw, "invalid_json")
        val type = json.optString("type").ifBlank {
            if (json.optString("method").isNotBlank()) "mcp" else ""
        }
        if (type.isBlank()) return ProtocolEvent.ProtocolError(raw, "missing type")
        val sessionId = json.optString("session_id").ifBlank { null }
        return when (type) {
            "hello" -> ProtocolEvent.Connected(sessionId.orEmpty())
            "tts" -> ProtocolEvent.TtsState(state = json.optString("state", "unknown"), sessionId = sessionId)
            "listen" -> ProtocolEvent.ListenState(state = json.optString("state", "unknown"), sessionId = sessionId)
            "mcp" -> routeMcp(json, sessionId)
            "stt", "llm", "text" -> ProtocolEvent.AssistantText(text = json.optString("text", raw), sessionId = sessionId)
            else -> ProtocolEvent.RawJson(type = type, json = raw, sessionId = sessionId)
        }
    }

    fun routeBinary(data: ByteArray): ProtocolEvent = ProtocolEvent.BinaryAudio(data)

    private fun routeMcp(json: JSONObject, sessionId: String?): ProtocolEvent {
        val payloadJson = when (val payload = json.opt("payload")) {
            is JSONObject -> payload.toString()
            is String -> payload.ifBlank { json.toString() }
            else -> if (json.optString("method").isNotBlank()) json.toString() else null
        } ?: return ProtocolEvent.ProtocolError(json.toString(), "missing mcp payload")

        val response = mcpProtocolClient.handleJsonRpc(payloadJson)
        return ProtocolEvent.McpResponse(
            requestMethod = response.method,
            requestIdJson = response.requestIdJson,
            toolName = response.toolName,
            status = response.status,
            blocked = response.blocked,
            message = response.message,
            responseJson = response.responseJson,
            sessionId = sessionId,
        )
    }
}
