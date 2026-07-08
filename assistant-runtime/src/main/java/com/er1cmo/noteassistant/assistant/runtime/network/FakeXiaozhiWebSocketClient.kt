package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay

@Singleton
class FakeXiaozhiWebSocketClient @Inject constructor(
    private val messageBuilder: XiaozhiMessageBuilder,
    private val messageRouter: XiaozhiMessageRouter,
) {
    private var sessionId: String? = null
    private var connected: Boolean = false

    fun isConnected(): Boolean = connected && sessionId != null

    fun currentSessionId(): String? = sessionId

    suspend fun connect(config: XiaozhiConnectionConfig): FakeWebSocketConnectResult {
        delay(80)
        val nextSessionId = "phase3-fake-ws-${System.currentTimeMillis()}"
        val outgoingHello = messageBuilder.hello()
        val incomingHello = "{\"type\":\"hello\",\"transport\":\"websocket\",\"session_id\":\"$nextSessionId\"}"
        val event = messageRouter.routeText(incomingHello)
        if (event is ProtocolEvent.Connected && event.sessionId.isNotBlank()) {
            sessionId = event.sessionId
            connected = true
        } else {
            connected = false
            sessionId = null
        }
        return FakeWebSocketConnectResult(
            success = connected,
            sessionId = sessionId,
            outgoingHelloJson = outgoingHello,
            incomingHelloJson = incomingHello,
            websocketUrl = config.websocketUrl,
            event = event,
        )
    }

    suspend fun sendText(text: String): FakeWebSocketTextTurn {
        val currentSession = sessionId
        if (!connected || currentSession.isNullOrBlank()) {
            return FakeWebSocketTextTurn(
                success = false,
                outgoingJson = null,
                incomingJson = null,
                event = ProtocolEvent.Error("Fake WebSocket 未连接"),
                message = "Fake WebSocket 未连接",
            )
        }
        delay(80)
        val outgoing = messageBuilder.listenDetect(currentSession, text)
        val incoming = "{\"session_id\":\"$currentSession\",\"type\":\"text\",\"text\":\"Fake Xiaozhi：已通过 Fake WebSocket 收到『${text.escapeJson()}』，Phase3 不执行便签工具。\"}"
        val event = messageRouter.routeText(incoming)
        return FakeWebSocketTextTurn(
            success = true,
            outgoingJson = outgoing,
            incomingJson = incoming,
            event = event,
            message = if (event is ProtocolEvent.AssistantText) event.text else "Fake WebSocket 已收到文本",
        )
    }

    fun close(reason: String = "fake_close"): XiaozhiWebSocketEvent.Closed {
        connected = false
        sessionId = null
        return XiaozhiWebSocketEvent.Closed(code = 1000, reason = reason)
    }
}

data class FakeWebSocketConnectResult(
    val success: Boolean,
    val sessionId: String?,
    val outgoingHelloJson: String,
    val incomingHelloJson: String,
    val websocketUrl: String,
    val event: ProtocolEvent,
)

data class FakeWebSocketTextTurn(
    val success: Boolean,
    val outgoingJson: String?,
    val incomingJson: String?,
    val event: ProtocolEvent,
    val message: String,
)

private fun String.escapeJson(): String = buildString {
    for (char in this@escapeJson) {
        when (char) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> append(char)
        }
    }
}
