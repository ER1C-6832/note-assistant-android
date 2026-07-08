package com.er1cmo.noteassistant.assistant.runtime.network

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.protocol.ProtocolEvent
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageBuilder
import com.er1cmo.noteassistant.assistant.runtime.protocol.XiaozhiMessageRouter
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeXiaozhiWebSocketClientAudioTest {
    private val client = FakeXiaozhiWebSocketClient(
        messageBuilder = XiaozhiMessageBuilder(),
        messageRouter = XiaozhiMessageRouter(McpProtocolClient()),
    )

    @Test
    fun listenStartAudioFrameAndListenStopRoundTrip() = runBlocking {
        client.connect(
            XiaozhiConnectionConfig(
                websocketUrl = "wss://fake.local/xiaozhi",
                websocketToken = "token",
                deviceId = "02:00:00:00:00:01",
                clientId = "client-1",
            ),
        )

        val start = client.sendStartListening()
        val audio = client.sendAudioFrame("fake-opus".toByteArray())
        val stop = client.sendStopListening()

        assertTrue(start.success)
        assertTrue(start.outgoingJson?.contains("\"state\":\"start\"") == true)
        assertTrue(start.event is ProtocolEvent.ListenState)
        assertTrue(audio.success)
        assertEquals(1, audio.uploadedAudioFrames)
        assertTrue(stop.success)
        assertTrue(stop.outgoingJson?.contains("\"state\":\"stop\"") == true)
    }
}
