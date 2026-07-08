package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeXiaozhiWebSocketClientTest {
    private val client = FakeXiaozhiWebSocketClient(
        messageBuilder = XiaozhiMessageBuilder(),
        messageRouter = XiaozhiMessageRouter(McpProtocolClient()),
    )

    @Test
    fun fakeConnectProducesSessionAndHelloJson() = runBlocking {
        val result = client.connect(
            XiaozhiConnectionConfig(
                websocketUrl = "wss://fake.local/xiaozhi",
                websocketToken = "token",
                deviceId = "02:00:00:00:00:01",
                clientId = "client-1",
            ),
        )

        assertTrue(result.success)
        assertTrue(result.sessionId?.startsWith("phase3-fake-ws-") == true)
        assertTrue(result.outgoingHelloJson.contains("\"type\":\"hello\""))
        assertTrue(result.event is ProtocolEvent.Connected)
    }

    @Test
    fun fakeTextTurnUsesListenDetectAndReturnsAssistantText() = runBlocking {
        client.connect(
            XiaozhiConnectionConfig(
                websocketUrl = "wss://fake.local/xiaozhi",
                websocketToken = "token",
                deviceId = "02:00:00:00:00:01",
                clientId = "client-1",
            ),
        )
        val turn = client.sendText("测试文本")
        assertTrue(turn.success)
        assertTrue(turn.outgoingJson?.contains("\"state\":\"detect\"") == true)
        assertTrue(turn.incomingJson?.contains("Fake Xiaozhi") == true)
        assertTrue(turn.event is ProtocolEvent.AssistantText)
    }
}
