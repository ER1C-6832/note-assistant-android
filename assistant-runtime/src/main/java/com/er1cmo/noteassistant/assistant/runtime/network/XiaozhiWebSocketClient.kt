package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.runtime.context.AssistantTurnContextStore
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString
import okio.ByteString.Companion.toByteString

@Singleton
class XiaozhiWebSocketClient @Inject constructor(
    private val messageBuilder: XiaozhiMessageBuilder,
    private val messageRouter: XiaozhiMessageRouter,
    private val assistantTurnContextStore: AssistantTurnContextStore,
) {
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    @Volatile private var webSocket: WebSocket? = null
    @Volatile private var connected: Boolean = false
    @Volatile private var connecting: Boolean = false

    @Volatile
    var sessionId: String = ""
        private set

    private val sendLock = Any()

    fun isConnected(): Boolean = connected && webSocket != null && sessionId.isNotBlank()

    suspend fun connect(
        config: XiaozhiConnectionConfig,
        onEvent: (XiaozhiWebSocketEvent) -> Unit,
    ): Boolean = withContext(Dispatchers.IO) {
        if (isConnected()) {
            onEvent(XiaozhiWebSocketEvent.Log("真实 WebSocket 已连接，无需重复连接"))
            return@withContext true
        }
        if (connecting) {
            onEvent(XiaozhiWebSocketEvent.Log("真实 WebSocket 正在连接中"))
            return@withContext false
        }
        validate(config)
        close("reconnect")
        connecting = true
        connected = false
        sessionId = ""

        val helloReceived = CompletableDeferred<Boolean>()
        val request = Request.Builder()
            .url(config.websocketUrl)
            .addHeader("Authorization", "Bearer ${config.websocketToken}")
            .addHeader("Protocol-Version", "1")
            .addHeader("Device-Id", config.deviceId)
            .addHeader("Client-Id", config.clientId)
            .build()

        val listener = object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val hello = messageBuilder.hello()
                onEvent(XiaozhiWebSocketEvent.OutgoingText(hello))
                val sent = webSocket.send(hello)
                if (!sent && !helloReceived.isCompleted) helloReceived.complete(false)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val event = messageRouter.routeText(text, realContext(sessionId.ifBlank { null }))
                onEvent(XiaozhiWebSocketEvent.IncomingText(text, event))
                if (event is ProtocolEvent.Connected) {
                    if (event.sessionId.isBlank()) {
                        onEvent(XiaozhiWebSocketEvent.Error("服务端 hello 未返回 session_id"))
                        if (!helloReceived.isCompleted) helloReceived.complete(false)
                        return
                    }
                    sessionId = event.sessionId
                    connected = true
                    connecting = false
                    onEvent(XiaozhiWebSocketEvent.Connected(event.sessionId))
                    if (!helloReceived.isCompleted) helloReceived.complete(true)
                }
            }

            override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
                val event = messageRouter.routeBinary(bytes.toByteArray())
                onEvent(XiaozhiWebSocketEvent.IncomingBinary(bytes.toByteArray(), event))
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                sessionId = ""
                onEvent(XiaozhiWebSocketEvent.Closed(code, reason))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                connected = false
                connecting = false
                onEvent(XiaozhiWebSocketEvent.Closed(code, reason))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                connected = false
                connecting = false
                sessionId = ""
                val status = response?.let { "HTTP ${it.code}" }.orEmpty()
                val message = listOf(status, t.message ?: t::class.java.simpleName)
                    .filter { it.isNotBlank() }
                    .joinToString("：")
                onEvent(XiaozhiWebSocketEvent.Error("真实 WebSocket 连接失败：$message"))
                if (!helloReceived.isCompleted) helloReceived.complete(false)
            }
        }

        webSocket = httpClient.newWebSocket(request, listener)

        try {
            val success = withTimeout(HELLO_TIMEOUT_MS) { helloReceived.await() }
            if (!success) close("hello_failed")
            success
        } catch (_: TimeoutCancellationException) {
            onEvent(XiaozhiWebSocketEvent.Error("等待真实服务端 hello 超时"))
            close("hello_timeout")
            false
        } finally {
            connecting = false
        }
    }

    fun sendText(text: String, onEvent: (XiaozhiWebSocketEvent) -> Unit = {}): Boolean {
        val currentSession = sessionId
        if (currentSession.isBlank()) return false
        val payload = messageBuilder.listenDetect(currentSession, text)
        onEvent(XiaozhiWebSocketEvent.OutgoingText(payload))
        return sendSessionPayload(payload)
    }

    fun sendStartListening(mode: String = "manual", onEvent: (XiaozhiWebSocketEvent) -> Unit = {}): Boolean {
        val currentSession = sessionId
        if (currentSession.isBlank()) return false
        val payload = messageBuilder.startListening(currentSession, mode)
        onEvent(XiaozhiWebSocketEvent.OutgoingText(payload))
        return sendSessionPayload(payload)
    }

    fun sendStopListening(onEvent: (XiaozhiWebSocketEvent) -> Unit = {}): Boolean {
        val currentSession = sessionId
        if (currentSession.isBlank()) return false
        val payload = messageBuilder.stopListening(currentSession)
        onEvent(XiaozhiWebSocketEvent.OutgoingText(payload))
        return sendSessionPayload(payload)
    }

    fun sendMcpResponse(responseJson: String, onEvent: (XiaozhiWebSocketEvent) -> Unit = {}): Boolean {
        val currentSession = sessionId
        if (currentSession.isBlank()) return false
        val payload = messageBuilder.mcp(currentSession, responseJson)
        onEvent(XiaozhiWebSocketEvent.OutgoingText(payload))
        return sendSessionPayload(payload)
    }

    fun sendAudioFrame(opusFrame: ByteArray): Boolean = synchronized(sendLock) {
        val socket = webSocket ?: return@synchronized false
        if (!isConnected()) return@synchronized false
        socket.send(opusFrame.toByteString())
    }

    fun close(reason: String = "client_close") {
        synchronized(sendLock) {
            connected = false
            connecting = false
            sessionId = ""
            webSocket?.close(NORMAL_CLOSE_CODE, reason.take(MAX_CLOSE_REASON_LENGTH))
            webSocket = null
        }
    }

    private fun sendSessionPayload(message: String): Boolean = synchronized(sendLock) {
        val socket = webSocket ?: return@synchronized false
        if (!isConnected()) return@synchronized false
        socket.send(message)
    }

    private fun validate(config: XiaozhiConnectionConfig) {
        require(config.websocketUrl.isNotBlank()) { "WebSocket URL 未配置" }
        require(config.websocketToken.isNotBlank()) { "WebSocket token 未配置" }
        require(config.deviceId.isNotBlank()) { "Device ID 未生成" }
        require(config.clientId.isNotBlank()) { "Client ID 未生成" }
    }

    private fun realContext(sessionId: String?): McpToolContext {
        val turnContext = assistantTurnContextStore.current()
        return McpToolContext(
            source = assistantTurnContextStore.currentMcpSource(),
            runtimeMode = "real",
            conversationId = turnContext?.conversationId,
            sessionId = sessionId,
        )
    }

    private companion object {
        const val HELLO_TIMEOUT_MS = 8_000L
        const val NORMAL_CLOSE_CODE = 1000
        const val MAX_CLOSE_REASON_LENGTH = 80
    }
}
