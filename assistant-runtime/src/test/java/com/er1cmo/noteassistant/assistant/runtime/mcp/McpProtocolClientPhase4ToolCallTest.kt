package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpProtocolClientPhase4ToolCallTest {
    @Test
    fun toolsListUsesInjectedExecutorDescriptors() {
        val client = McpProtocolClient(setOf(FakeExecutor(FakeTool("notes.list_pinned"))))

        val response = client.handleJsonRpc("""{"jsonrpc":"2.0","id":1,"method":"tools/list"}""")

        assertEquals(McpToolStatus.Success, response.status)
        val tools = JSONObject(response.responseJson).getJSONObject("result").getJSONArray("tools")
        assertEquals("notes.list_pinned", tools.getJSONObject(0).getString("name"))
    }

    @Test
    fun toolsCallUsesInjectedExecutorResultEnvelope() {
        val client = McpProtocolClient(setOf(FakeExecutor(FakeTool("notes.search"))))

        val response = client.handleJsonRpc(
            """{"jsonrpc":"2.0","id":"call-1","method":"tools/call","params":{"name":"notes.search","arguments":{"query":"客户"}}}""",
        )

        assertEquals(McpToolStatus.Success, response.status)
        assertFalse(response.blocked)
        val structured = JSONObject(response.responseJson)
            .getJSONObject("result")
            .getJSONObject("structuredContent")
        assertEquals("success", structured.getString("status"))
        assertEquals("notes.search", structured.getString("tool_name"))
        assertEquals("客户", structured.getJSONObject("arguments").getString("query"))
    }

    @Test
    fun missingExecutorFailsClosed() {
        val client = McpProtocolClient()

        val response = client.handleJsonRpc(
            """{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"notes.delete","arguments":{"note_id":1}}}""",
        )

        assertTrue(response.blocked)
        val structured = JSONObject(response.responseJson)
            .getJSONObject("result")
            .getJSONObject("structuredContent")
        assertEquals("blocked", structured.getString("status"))
        assertEquals("executor_unavailable", structured.getString("error_code"))
    }

    private class FakeExecutor(private val tool: McpTool) : McpToolExecutor {
        override fun listDescriptors(): List<McpToolDescriptor> = listOf(tool.descriptor)
        override fun findTool(name: String): McpTool? = tool.takeIf { it.name == name }
        override suspend fun execute(name: String, argumentsJson: String, context: McpToolContext): McpToolResult {
            return findTool(name)?.call(argumentsJson, context) ?: McpToolResult.notImplemented(name)
        }
    }

    private class FakeTool(override val name: String) : McpTool {
        override val description: String = "fake $name"
        override val riskLevel: McpRiskLevel = McpRiskLevel.Low
        override val descriptor: McpToolDescriptor = McpToolDescriptor(name = name, description = description)
        override suspend fun call(argumentsJson: String): McpToolResult = McpToolResult.success(
            message = "fake success",
            resultJson = JSONObject().put("ok", true).toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
        ).copy(argumentsJson = argumentsJson)
    }
}
