package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolClientTest {
    private val client = McpProtocolClient()

    @Test
    fun toolsListReturnsEmptyDescriptorsWhenExecutorUnavailable() {
        val response = client.handleJsonRpc("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"method\":\"tools/list\",\"params\":{}}")

        assertEquals(McpToolStatus.Success, response.status)
        assertFalse(response.blocked)
        assertTrue(response.responseJson.contains("\"tools\":[]"))
        assertFalse(response.responseJson.contains("phase3.echo"))
        assertFalse(response.responseJson.contains("phase3.status"))
    }

    @Test
    fun notesToolCallFailsClosedWhenExecutorUnavailable() {
        val response = client.handleJsonRpc(
            "{\"jsonrpc\":\"2.0\",\"id\":\"2\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}}",
        )

        assertEquals(McpToolStatus.Blocked, response.status)
        assertTrue(response.blocked)
        assertEquals("notes.delete", response.toolName)
        assertTrue(response.responseJson.contains("\"blocked\":true"))
        assertTrue(response.responseJson.contains(McpToolResult.ERROR_EXECUTOR_UNAVAILABLE))
    }

    @Test
    fun tagsToolCallFailsClosedWhenExecutorUnavailable() {
        val response = client.handleJsonRpc(
            "{\"jsonrpc\":\"2.0\",\"id\":\"3\",\"method\":\"tools/call\",\"params\":{\"name\":\"tags.delete\",\"arguments\":{\"tag_id\":1}}}",
        )

        assertEquals(McpToolStatus.Blocked, response.status)
        assertTrue(response.blocked)
        assertEquals("tags.delete", response.toolName)
        assertTrue(response.responseJson.contains(McpToolResult.ERROR_EXECUTOR_UNAVAILABLE))
    }

    @Test
    fun initializeStillSucceeds() {
        val response = client.handleJsonRpc(
            "{\"jsonrpc\":\"2.0\",\"id\":\"4\",\"method\":\"initialize\",\"params\":{}}",
        )

        assertEquals(McpToolStatus.Success, response.status)
        assertFalse(response.blocked)
        assertTrue(response.responseJson.contains("note-assistant-android-phase4"))
    }
}
