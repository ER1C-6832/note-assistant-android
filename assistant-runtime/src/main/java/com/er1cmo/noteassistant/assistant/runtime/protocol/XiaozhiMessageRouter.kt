package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import javax.inject.Inject

class XiaozhiMessageRouter @Inject constructor(
    private val mcpProtocolClient: McpProtocolClient,
) {
    fun routeText(raw: String): ProtocolEvent {
        val type = raw.jsonString("type") ?: return ProtocolEvent.ProtocolError(raw, "missing type")
        val sessionId = raw.jsonString("session_id")
        return when (type) {
            "hello" -> ProtocolEvent.Connected(sessionId ?: "")
            "tts" -> ProtocolEvent.TtsState(state = raw.jsonString("state") ?: "unknown", sessionId = sessionId)
            "listen" -> ProtocolEvent.ListenState(state = raw.jsonString("state") ?: "unknown", sessionId = sessionId)
            "mcp" -> routeMcp(raw, sessionId)
            "stt", "llm", "text" -> ProtocolEvent.AssistantText(text = raw.jsonString("text") ?: raw, sessionId = sessionId)
            else -> ProtocolEvent.RawJson(type = type, json = raw, sessionId = sessionId)
        }
    }

    fun routeBinary(data: ByteArray): ProtocolEvent = ProtocolEvent.BinaryAudio(data)

    private fun routeMcp(raw: String, sessionId: String?): ProtocolEvent {
        val toolName = raw.jsonString("name") ?: raw.jsonString("tool") ?: raw.jsonString("tool_name")
        if (toolName.isNullOrBlank()) return ProtocolEvent.RawJson(type = "mcp", json = raw, sessionId = sessionId)
        val argumentsJson = raw.extractObjectAfter("arguments") ?: raw.extractObjectAfter("args") ?: "{}"
        val result = mcpProtocolClient.handleToolCall(toolName = toolName, argumentsJson = argumentsJson)
        return ProtocolEvent.ToolCall(
            toolName = result.toolName,
            argumentsJson = result.argumentsJson ?: argumentsJson,
            sessionId = sessionId,
        )
    }
}

private fun String.jsonString(key: String): String? {
    val pattern = Regex("\\\"" + Regex.escape(key) + "\\\"\\s*:\\s*\\\"([^\\\"]*)\\\"")
    return pattern.find(this)?.groupValues?.getOrNull(1)
}

private fun String.extractObjectAfter(key: String): String? {
    val keyIndex = indexOf("\"$key\"")
    if (keyIndex < 0) return null
    val start = indexOf('{', keyIndex)
    if (start < 0) return null
    var depth = 0
    for (index in start until length) {
        when (this[index]) {
            '{' -> depth += 1
            '}' -> {
                depth -= 1
                if (depth == 0) return substring(start, index + 1)
            }
        }
    }
    return null
}
