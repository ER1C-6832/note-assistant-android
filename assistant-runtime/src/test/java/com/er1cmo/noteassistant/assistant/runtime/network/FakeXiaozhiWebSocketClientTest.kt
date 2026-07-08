package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpToolStatus
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeXiaozhiWebSocketClientTest {
    private val client = FakeXiaozhiWebSocketClient(
        messageBuilder = XiaozhiMessageBuilder(),
        messageRouter = XiaozhiMessageRouter(McpProtocolClient()),
    )

    @Test
    fun fakeConnectProducesSessionAndHelloJson() = runBlocking {
        val result = client.connect(config())

        assertTrue(result.success)
        assertTrue(result.sessionId?.startsWith("phase3-fake-ws-") == true)
        assertTrue(result.outgoingHelloJson.contains("\"type\":\"hello\""))
        assertTrue(result.event is ProtocolEvent.Connected)
    }

    @Test
    fun fakeTextTurnUsesListenDetectAndReturnsAssistantText() = runBlocking {
        client.connect(config())
        val turn = client.sendText("测试文本")
        assertTrue(turn.success)
        assertTrue(turn.outgoingJson?.contains("\"state\":\"detect\"") == true)
        assertTrue(turn.incomingJson?.contains("Fake Xiaozhi") == true)
        assertTrue(turn.event is ProtocolEvent.AssistantText)
    }

    @Test
    fun fakeToolsListRequestCreatesMcpResponseMessage() = runBlocking {
        client.connect(config())
        val turn = client.simulateIncomingToolsListRequest()

        assertTrue(turn.success)
        assertTrue(turn.incomingJson?.contains("tools/list") == true)
        assertTrue(turn.outgoingResponseJson?.contains("\"type\":\"mcp\"") == true)
        assertTrue(turn.outgoingResponseJson?.contains("phase3.echo") == true)
        assertTrue(turn.event is ProtocolEvent.McpResponse)
    }

    @Test
    fun fakeNoteToolCallIsBlockedAndWrappedAsMcpResponse() = runBlocking {
        client.connect(config())
        val turn = client.simulateIncomingToolCall("notes.delete", "{\"note_id\":1}")

        assertTrue(turn.success)
        assertTrue(turn.incomingJson?.contains("notes.delete") == true)
        assertTrue(turn.outgoingResponseJson?.contains("\"blocked\":true") == true)
        val event = turn.event as ProtocolEvent.McpResponse
        assertEquals(McpToolStatus.Blocked, event.status)
        assertEquals("notes.delete", event.toolName)
    }

    private fun config(): XiaozhiConnectionConfig = XiaozhiConnectionConfig(
        websocketUrl = "wss://fake.local/xiaozhi",
        websocketToken = "token",
        deviceId = "02:00:00:00:00:01",
        clientId = "client-1",
    )
}
