package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpToolStatus
import org.junit.Assert.assertEquals
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
    fun toolsListRoutesToMcpResponse() {
        val event = router.routeText(
            "{\"type\":\"mcp\",\"session_id\":\"abc\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":\"list\",\"method\":\"tools/list\",\"params\":{}}}",
        )

        assertTrue(event is ProtocolEvent.McpResponse)
        val response = event as ProtocolEvent.McpResponse
        assertEquals("tools/list", response.requestMethod)
        assertEquals(McpToolStatus.Success, response.status)
        assertTrue(response.responseJson.contains("phase3.echo"))
    }

    @Test
    fun noteToolCallRoutesToBlockedMcpResponse() {
        val event = router.routeText(
            "{\"type\":\"mcp\",\"session_id\":\"abc\",\"payload\":{\"jsonrpc\":\"2.0\",\"id\":\"call\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}}}",
        )

        assertTrue(event is ProtocolEvent.McpResponse)
        val response = event as ProtocolEvent.McpResponse
        assertEquals("tools/call", response.requestMethod)
        assertEquals("notes.delete", response.toolName)
        assertEquals(McpToolStatus.Blocked, response.status)
        assertTrue(response.blocked)
        assertTrue(response.responseJson.contains("\"note_mutation_enabled\":false"))
    }
}
