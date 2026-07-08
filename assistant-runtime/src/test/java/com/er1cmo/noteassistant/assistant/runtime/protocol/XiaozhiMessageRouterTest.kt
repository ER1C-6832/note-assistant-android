package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
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
    fun noteToolCallIsParsedAsToolCallOnly() {
        val event = router.routeText("{\"type\":\"mcp\",\"session_id\":\"abc\",\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}")
        assertTrue(event is ProtocolEvent.ToolCall)
        assertEquals("notes.delete", (event as ProtocolEvent.ToolCall).toolName)
    }
}
