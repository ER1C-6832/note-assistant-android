package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiMessageRouterTest {
    private val router = XiaozhiMessageRouter(McpProtocolClient())

    @Test
    fun helloRoutesToConnected() {
        val event = router.routeText("{\"type\":\"hello\",\"session_id\":\"abc\",\"transport\":\"websocket\"}")

        assertTrue(event is ProtocolEvent.Connected)
        assertEquals("abc", (event as ProtocolEvent.Connected).sessionId)
    }

    @Test
    fun textRoutesToAssistantText() {
        val event = router.routeText("{\"type\":\"text\",\"session_id\":\"abc\",\"text\":\"你好\"}")

        assertTrue(event is ProtocolEvent.AssistantText)
        assertEquals("你好", (event as ProtocolEvent.AssistantText).text)
    }

    @Test
    fun malformedJsonBecomesProtocolError() {
        val event = router.routeText("{not-json")

        assertTrue(event is ProtocolEvent.ProtocolError)
        assertEquals("invalid_json", (event as ProtocolEvent.ProtocolError).reason)
    }

    @Test
    fun missingTypeBecomesProtocolError() {
        val event = router.routeText("{\"session_id\":\"abc\"}")

        assertTrue(event is ProtocolEvent.ProtocolError)
        assertEquals("missing type", (event as ProtocolEvent.ProtocolError).reason)
    }

    @Test
    fun toolsListRoutesToExecutorBackedMcpResponse() {
        val event = router.routeText(
            "{\"type\":\"mcp\",\"session_id\":\"abc\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":\"tools\",\"method\":\"tools/list\",\"params\":{}}}",
        )

        assertTrue(event is ProtocolEvent.McpResponse)
        val response = event as ProtocolEvent.McpResponse
        assertFalse(response.blocked)
        assertEquals(McpToolStatus.Success, response.status)
        assertTrue(response.responseJson.contains("\"tools\":[]"))
        assertFalse(response.responseJson.contains("phase3.status"))
    }

    @Test
    fun noteToolCallFailsClosedAtProtocolBoundary() {
        val event = router.routeText(
            "{\"type\":\"mcp\",\"session_id\":\"abc\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":\"call\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}}}",
        )

        assertTrue(event is ProtocolEvent.McpResponse)
        val response = event as ProtocolEvent.McpResponse
        assertTrue(response.blocked)
        assertEquals(McpToolStatus.Blocked, response.status)
        assertEquals("notes.delete", response.toolName)
        assertTrue(response.responseJson.contains(McpToolResult.ERROR_EXECUTOR_UNAVAILABLE))
    }

    @Test
    fun binaryRoutesToBinaryAudio() {
        val event = router.routeBinary(byteArrayOf(1, 2, 3))

        assertTrue(event is ProtocolEvent.BinaryAudio)
        assertEquals(3, (event as ProtocolEvent.BinaryAudio).data.size)
    }
}
