package com.er1cmo.noteassistant.assistant.mcpbase

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolRegistryTest {
    @Test
    fun registryListsDescriptorsAndExecutesRegisteredTool() = runBlocking {
        val registry = TestRegistry(
            listOf(
                EchoTool,
                StatusTool,
            ),
        )

        assertEquals(listOf("phase4.echo", "phase4.status"), registry.listDescriptors().map { it.name })
        assertEquals("phase4.echo", registry.findTool("phase4.echo")?.name)

        val result = registry.execute(
            name = "phase4.echo",
            argumentsJson = "{\"text\":\"hi\"}",
            context = McpToolContext(source = McpToolContext.SOURCE_LOCAL_TOOL_SIMULATOR),
        )

        assertEquals(McpToolStatus.Success, result.statusEnum)
        assertEquals("phase4.echo", result.toolName)
        assertTrue(result.resultJson?.contains("hi") == true)
    }

    @Test
    fun unknownToolFailsClosedAsNotImplemented() = runBlocking {
        val registry = TestRegistry(emptyList())

        val result = registry.execute("notes.unknown", "{}")

        assertEquals(McpToolStatus.NotImplemented, result.statusEnum)
        assertEquals("unsupported_tool", result.errorCode)
        assertEquals("notes.unknown", result.toolName)
        assertNull(registry.findTool("notes.unknown"))
    }

    private class TestRegistry(override val tools: List<McpTool>) : McpToolRegistry

    private object EchoTool : McpTool {
        override val name: String = "phase4.echo"
        override val description: String = "echo"
        override val riskLevel: McpRiskLevel = McpRiskLevel.Low

        override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
            val text = ToolArgumentParser.parse(argumentsJson).getOrThrow().optionalString("text")
            return McpToolResult.success(
                toolName = name,
                message = "echo ok",
                resultJson = "{\"text\":\"$text\",\"source\":\"${context.source}\"}",
            )
        }

        override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())
    }

    private object StatusTool : McpTool {
        override val name: String = "phase4.status"
        override val description: String = "status"
        override val riskLevel: McpRiskLevel = McpRiskLevel.Low
        override suspend fun call(argumentsJson: String): McpToolResult = McpToolResult.success("ok", toolName = name)
    }
}
