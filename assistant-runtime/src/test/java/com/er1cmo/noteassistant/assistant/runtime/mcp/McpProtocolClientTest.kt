package com.er1cmo.noteassistant.assistant.runtime.mcp

import org.junit.Assert.assertEquals
import org.junit.Test

class McpProtocolClientTest {
    private val client = McpProtocolClient()

    @Test
    fun noteMutationToolsAreBlockedInPhase3() {
        val result = client.handleToolCall("notes.delete", "{\"note_id\":1}")
        assertEquals(McpToolStatus.Blocked, result.status)
    }

    @Test
    fun phase3EchoToolSucceedsWithoutNotes() {
        val result = client.handleToolCall("phase3.echo", "{\"text\":\"hello\"}")
        assertEquals(McpToolStatus.Success, result.status)
    }
}
