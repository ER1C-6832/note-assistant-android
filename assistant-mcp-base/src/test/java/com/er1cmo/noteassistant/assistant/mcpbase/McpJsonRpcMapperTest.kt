package com.er1cmo.noteassistant.assistant.mcpbase

import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpJsonRpcMapperTest {
    @Test
    fun toolsListResponseContainsDescriptorFields() = runBlocking {
        val response = McpJsonRpcMapper.handleJsonRpc(
            payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":\"tools\",\"method\":\"tools/list\",\"params\":{}}",
            executor = TestRegistry(listOf(CreateTool)),
        )

        val tool = JSONObject(response)
            .getJSONObject("result")
            .getJSONArray("tools")
            .getJSONObject(0)

        assertEquals("notes.create", tool.getString("name"))
        assertEquals("medium", tool.getString("risk"))
        assertTrue(tool.getBoolean("mutates"))
        assertEquals("object", tool.getJSONObject("inputSchema").getString("type"))
    }

    @Test
    fun toolsCallResponseUsesStableEnvelope() = runBlocking {
        val response = McpJsonRpcMapper.handleJsonRpc(
            payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":42,\"method\":\"tools/call\",\"params\":{\"name\":\"notes.create\",\"arguments\":{\"title\":\"样品\"}}}",
            executor = TestRegistry(listOf(CreateTool)),
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
        )

        val root = JSONObject(response)
        val result = root.getJSONObject("result")
        val envelope = result.getJSONObject("structuredContent")

        assertEquals(42, root.getLong("id"))
        assertFalse(result.getBoolean("isError"))
        assertEquals("success", envelope.getString("status"))
        assertEquals("notes.create", envelope.getString("tool_name"))
        assertEquals(123, envelope.getJSONArray("affected_note_ids").getLong(0))
        assertEquals(42, envelope.getLong("command_log_id"))
    }

    @Test
    fun invalidJsonReturnsJsonRpcParseError() = runBlocking {
        val response = McpJsonRpcMapper.handleJsonRpc(
            payloadJson = "{not-json",
            executor = TestRegistry(emptyList()),
        )

        val error = JSONObject(response).getJSONObject("error")

        assertEquals(-32700, error.getInt("code"))
        assertEquals("invalid_json", error.getJSONObject("data").getString("error_code"))
    }

    @Test
    fun missingToolNameReturnsValidationEnvelope() = runBlocking {
        val response = McpJsonRpcMapper.handleJsonRpc(
            payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":\"call\",\"method\":\"tools/call\",\"params\":{\"arguments\":{}}}",
            executor = TestRegistry(emptyList()),
        )

        val envelope = JSONObject(response)
            .getJSONObject("result")
            .getJSONObject("structuredContent")

        assertEquals("failed", envelope.getString("status"))
        assertEquals("validation_error", envelope.getString("error_code"))
    }

    @Test
    fun failClosedExecutorReturnsBlockedEnvelope() = runBlocking {
        val response = McpJsonRpcMapper.handleJsonRpc(
            payloadJson = "{\"jsonrpc\":\"2.0\",\"id\":\"call\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.delete\",\"arguments\":{\"note_id\":1}}}",
            executor = FailClosedMcpToolExecutor(),
        )

        val result = JSONObject(response).getJSONObject("result")
        val envelope = result.getJSONObject("structuredContent")

        assertTrue(result.getBoolean("isError"))
        assertEquals("blocked", envelope.getString("status"))
        assertEquals("executor_unavailable", envelope.getString("error_code"))
    }

    private class TestRegistry(override val tools: List<McpTool>) : McpToolRegistry

    private object CreateTool : McpTool {
        override val name: String = "notes.create"
        override val description: String = "创建便签"
        override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
        override val descriptor: McpToolDescriptor = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": { "title": { "type": "string" } },
                  "required": ["title"]
                }
            """.trimIndent(),
            riskLevel = riskLevel,
            mutates = true,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED_BY_DEFAULT,
            examples = listOf("帮我记一下明天上午十点联系客户"),
        )

        override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
            val title = ToolArgumentParser.parse(argumentsJson).getOrThrow().requireString("title")
            return McpToolResult.success(
                toolName = name,
                message = "已创建便签：$title",
                resultJson = "{\"note_id\":123,\"source\":\"${context.source}\"}",
                risk = McpRiskLevel.Medium,
                commandLogId = 42,
                affectedNoteIds = listOf(123),
            )
        }

        override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())
    }
}
