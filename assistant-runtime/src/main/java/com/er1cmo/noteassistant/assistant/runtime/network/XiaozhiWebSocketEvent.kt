package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent

sealed interface XiaozhiWebSocketEvent {
    data class Log(val message: String) : XiaozhiWebSocketEvent
    data class OutgoingText(val json: String) : XiaozhiWebSocketEvent
    data class IncomingText(val json: String, val event: ProtocolEvent) : XiaozhiWebSocketEvent
    data class IncomingBinary(val bytes: ByteArray, val event: ProtocolEvent) : XiaozhiWebSocketEvent
    data class Connected(val sessionId: String) : XiaozhiWebSocketEvent
    data class Closed(val code: Int, val reason: String) : XiaozhiWebSocketEvent
    data class Error(val message: String) : XiaozhiWebSocketEvent
}
