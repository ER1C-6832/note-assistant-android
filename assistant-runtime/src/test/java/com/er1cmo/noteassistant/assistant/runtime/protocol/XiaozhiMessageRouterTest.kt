package com.er1cmo.noteassistant.assistant.runtime.protocol

import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiMessageRouterTest {
    private val router = XiaozhiMessageRouter(McpProtocolClient())

    @Test
    fun assistantTextMessageRoutesToAssistantTextEvent() {
        val event = router.routeText("{\"type\":\"text\",\"session_id\":\"s1\",\"text\":\"你好\"}")
        assertTrue(event is ProtocolEvent.AssistantText)
        assertEquals("你好", (event as ProtocolEvent.AssistantText).text)
    }

    @Test
    fun noteToolCallRoutesButIsStillOnlyProtocolEvent() {
        val event = router.routeText("{\"type\":\"mcp\",\"session_id\":\"s1\",\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}")
        assertTrue(event is ProtocolEvent.ToolCall)
        assertEquals("notes.delete", (event as ProtocolEvent.ToolCall).toolName)
    }
}
