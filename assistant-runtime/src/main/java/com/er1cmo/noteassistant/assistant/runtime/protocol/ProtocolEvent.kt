package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus

sealed interface ProtocolEvent {
    data class Connected(val sessionId: String) : ProtocolEvent
    data class Closed(val reason: String) : ProtocolEvent
    data class Error(val message: String) : ProtocolEvent
    data class AssistantText(val text: String, val sessionId: String?) : ProtocolEvent
    data class TtsState(val state: String, val sessionId: String?) : ProtocolEvent
    data class ListenState(val state: String, val sessionId: String?) : ProtocolEvent
    data class ToolCall(val toolName: String, val argumentsJson: String, val sessionId: String?) : ProtocolEvent
    data class McpResponse(
        val requestMethod: String?,
        val requestIdJson: String?,
        val toolName: String?,
        val status: McpToolStatus,
        val blocked: Boolean,
        val message: String,
        val responseJson: String,
        val sessionId: String?,
    ) : ProtocolEvent
    data class BinaryAudio(val data: ByteArray) : ProtocolEvent
    data class RawJson(val type: String, val json: String, val sessionId: String?) : ProtocolEvent
    data class ProtocolError(val raw: String, val reason: String) : ProtocolEvent
}
