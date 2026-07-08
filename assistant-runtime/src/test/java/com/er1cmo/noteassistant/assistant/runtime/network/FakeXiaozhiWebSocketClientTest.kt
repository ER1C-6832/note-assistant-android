package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpToolStatus
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class FakeXiaozhiWebSocketClientTest {
    private fun newClient(): FakeXiaozhiWebSocketClient = FakeXiaozhiWebSocketClient(
        messageBuilder = XiaozhiMessageBuilder(),
        messageRouter = XiaozhiMessageRouter(McpProtocolClient()),
    )

    private val config = XiaozhiConnectionConfig(
        websocketUrl = "wss://fake.local/xiaozhi",
        websocketToken = "token",
        deviceId = "02:00:00:00:00:01",
        clientId = "client-1",
    )

    @Test
    fun fakeConnectProducesSessionAndHelloJson() = runBlocking {
        val client = newClient()
        val result = client.connect(config)

        assertTrue(result.success)
        assertTrue(result.sessionId?.startsWith("phase3-fake-ws-") == true)
        assertTrue(result.outgoingHelloJson.contains("\"type\":\"hello\""))
        assertTrue(result.event is ProtocolEvent.Connected)
    }

    @Test
    fun fakeTextTurnUsesListenDetectAndReturnsAssistantText() = runBlocking {
        val client = newClient()
        client.connect(config)

        val turn = client.sendText("测试文本")

        assertTrue(turn.success)
        assertTrue(turn.outgoingJson?.contains("\"state\":\"detect\"") == true)
        assertTrue(turn.incomingJson?.contains("Fake Xiaozhi") == true)
        assertTrue(turn.event is ProtocolEvent.AssistantText)
    }

    @Test
    fun sendTextWhileDisconnectedReturnsSafeFailure() = runBlocking {
        val client = newClient()

        val turn = client.sendText("测试文本")

        assertFalse(turn.success)
        assertTrue(turn.event is ProtocolEvent.Error)
        assertEquals("Fake WebSocket 未连接", turn.message)
    }

    @Test
    fun fakePttControlAndAudioFrameLifecycleWorks() = runBlocking {
        val client = newClient()
        client.connect(config)

        val start = client.sendStartListening()
        val upload1 = client.sendAudioFrame(byteArrayOf(1, 2, 3))
        val upload2 = client.sendAudioFrame(byteArrayOf(4, 5, 6))
        val stop = client.sendStopListening()

        assertTrue(start.success)
        assertTrue(upload1.success)
        assertTrue(upload2.success)
        assertEquals(2, client.uploadedAudioFrameCount())
        assertTrue(stop.success)
        assertTrue(stop.incomingJson?.contains("uploaded_audio_frames") == true)
    }

    @Test
    fun notesToolCallIsBlockedThroughFakeWebSocketMcpPath() = runBlocking {
        val client = newClient()
        client.connect(config)

        val turn = client.simulateIncomingToolCall("notes.delete", "{\"note_id\":1}")

        assertTrue(turn.success)
        assertTrue(turn.event is ProtocolEvent.McpResponse)
        val response = turn.event as ProtocolEvent.McpResponse
        assertTrue(response.blocked)
        assertEquals(McpToolStatus.Blocked, response.status)
        assertTrue(turn.outgoingResponseJson?.contains("note_mutation_enabled") == true)
    }

    @Test
    fun abnormalServerCloseClearsConnectedState() = runBlocking {
        val client = newClient()
        client.connect(config)

        val closed = client.simulateServerClose(code = 1006, reason = "abnormal")

        assertEquals(1006, closed.code)
        assertEquals("abnormal", closed.reason)
        assertFalse(client.isConnected())
        assertNull(client.currentSessionId())
    }

    @Test
    fun transportFailureClearsConnectedState() = runBlocking {
        val client = newClient()
        client.connect(config)

        val error = client.simulateTransportFailure("network down")

        assertEquals("network down", error.message)
        assertFalse(client.isConnected())
    }
}
