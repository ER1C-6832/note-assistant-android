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
    private var uploadedAudioFrames: Int = 0

    fun isConnected(): Boolean = connected && sessionId != null

    fun currentSessionId(): String? = sessionId

    fun uploadedAudioFrameCount(): Int = uploadedAudioFrames

    suspend fun connect(config: XiaozhiConnectionConfig): FakeWebSocketConnectResult {
        delay(80)
        uploadedAudioFrames = 0
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

    suspend fun sendStartListening(mode: String = "manual"): FakeWebSocketControlTurn {
        val currentSession = sessionId
        if (!connected || currentSession.isNullOrBlank()) return notConnectedControlTurn()
        delay(30)
        uploadedAudioFrames = 0
        val outgoing = messageBuilder.startListening(currentSession, mode)
        val incoming = "{\"session_id\":\"$currentSession\",\"type\":\"listen\",\"state\":\"start\",\"mode\":\"${mode.escapeJson()}\"}"
        val event = messageRouter.routeText(incoming)
        return FakeWebSocketControlTurn(
            success = event is ProtocolEvent.ListenState,
            outgoingJson = outgoing,
            incomingJson = incoming,
            event = event,
            message = "Fake listen/start 已确认",
        )
    }

    fun sendAudioFrame(fakeOpusFrame: ByteArray): FakeWebSocketAudioTurn {
        val currentSession = sessionId
        if (!connected || currentSession.isNullOrBlank()) {
            return FakeWebSocketAudioTurn(
                success = false,
                uploadedAudioFrames = uploadedAudioFrames,
                message = "Fake WebSocket 未连接，无法上传音频帧",
            )
        }
        if (fakeOpusFrame.isEmpty()) {
            return FakeWebSocketAudioTurn(
                success = false,
                uploadedAudioFrames = uploadedAudioFrames,
                message = "Fake Opus 帧为空，未上传",
            )
        }
        uploadedAudioFrames += 1
        return FakeWebSocketAudioTurn(
            success = true,
            uploadedAudioFrames = uploadedAudioFrames,
            message = "Fake audio frame uploaded：$uploadedAudioFrames",
        )
    }

    suspend fun sendStopListening(): FakeWebSocketControlTurn {
        val currentSession = sessionId
        if (!connected || currentSession.isNullOrBlank()) return notConnectedControlTurn()
        delay(30)
        val outgoing = messageBuilder.stopListening(currentSession)
        val incoming = "{\"session_id\":\"$currentSession\",\"type\":\"listen\",\"state\":\"stop\",\"uploaded_audio_frames\":$uploadedAudioFrames}"
        val event = messageRouter.routeText(incoming)
        return FakeWebSocketControlTurn(
            success = event is ProtocolEvent.ListenState,
            outgoingJson = outgoing,
            incomingJson = incoming,
            event = event,
            message = "Fake listen/stop 已确认，上传 $uploadedAudioFrames 帧",
        )
    }

    suspend fun simulateIncomingToolsListRequest(): FakeWebSocketMcpTurn {
        val payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":\"phase3-tools-list\",\"method\":\"tools/list\",\"params\":{}}"
        return simulateIncomingMcpRequest(payloadJson)
    }

    suspend fun simulateIncomingToolCall(
        toolName: String,
        argumentsJson: String,
    ): FakeWebSocketMcpTurn {
        val payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":\"phase3-tools-call\",\"method\":\"tools/call\",\"params\":{\"name\":\"${toolName.escapeJson()}\",\"arguments\":${argumentsJson.ifBlank { "{}" }}}}"
        return simulateIncomingMcpRequest(payloadJson)
    }

    suspend fun simulateIncomingMcpRequest(payloadJson: String): FakeWebSocketMcpTurn {
        val currentSession = sessionId
        if (!connected || currentSession.isNullOrBlank()) {
            return FakeWebSocketMcpTurn(
                success = false,
                incomingJson = null,
                outgoingResponseJson = null,
                event = ProtocolEvent.Error("Fake WebSocket 未连接"),
                message = "Fake WebSocket 未连接，无法模拟 MCP 请求",
            )
        }
        delay(60)
        val incoming = "{\"session_id\":\"$currentSession\",\"type\":\"mcp\",\"payload\":$payloadJson}"
        val event = messageRouter.routeText(incoming)
        val responseJson = if (event is ProtocolEvent.McpResponse) {
            messageBuilder.mcp(currentSession, event.responseJson)
        } else {
            null
        }
        val message = when (event) {
            is ProtocolEvent.McpResponse -> event.message
            is ProtocolEvent.ProtocolError -> "MCP 协议错误：${event.reason}"
            else -> "MCP 请求未产生响应事件：${event.javaClass.simpleName}"
        }
        return FakeWebSocketMcpTurn(
            success = event is ProtocolEvent.McpResponse,
            incomingJson = incoming,
            outgoingResponseJson = responseJson,
            event = event,
            message = message,
        )
    }

    fun close(reason: String = "fake_close"): XiaozhiWebSocketEvent.Closed {
        connected = false
        sessionId = null
        uploadedAudioFrames = 0
        return XiaozhiWebSocketEvent.Closed(code = 1000, reason = reason)
    }

    private fun notConnectedControlTurn(): FakeWebSocketControlTurn = FakeWebSocketControlTurn(
        success = false,
        outgoingJson = null,
        incomingJson = null,
        event = ProtocolEvent.Error("Fake WebSocket 未连接"),
        message = "Fake WebSocket 未连接",
    )
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

data class FakeWebSocketControlTurn(
    val success: Boolean,
    val outgoingJson: String?,
    val incomingJson: String?,
    val event: ProtocolEvent,
    val message: String,
)

data class FakeWebSocketAudioTurn(
    val success: Boolean,
    val uploadedAudioFrames: Int,
    val message: String,
)

data class FakeWebSocketMcpTurn(
    val success: Boolean,
    val incomingJson: String?,
    val outgoingResponseJson: String?,
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
