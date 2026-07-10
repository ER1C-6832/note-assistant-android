package com.er1cmo.noteassistant.assistant.runtime

import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolExecutor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.runtime.mcp.McpProtocolClient
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallEventStore
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isDirectory
import kotlin.io.path.name
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4RuntimeContractTest {
    @Test
    fun invalidJsonReturnsInvalidJsonAndDoesNotExecuteTools() {
        val client = McpProtocolClient()

        val response = client.handleJsonRpc("{")

        assertTrue(response.blocked)
        assertEquals(McpToolStatus.NotImplemented, response.status)
        assertTrue(response.responseJson.contains(McpToolResult.ERROR_INVALID_JSON))
    }

    @Test
    fun unknownToolFailsClosedWhenExecutorUnavailable() {
        val client = McpProtocolClient()

        val response = client.handleJsonRpc(
            "{\"jsonrpc\":\"2.0\",\"id\":\"unknown\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.not_registered\",\"arguments\":{}}}",
        )

        assertEquals(McpToolStatus.Blocked, response.status)
        assertTrue(response.blocked)
        assertEquals("notes.not_registered", response.toolName)
        assertTrue(response.responseJson.contains(McpToolResult.ERROR_EXECUTOR_UNAVAILABLE))
    }

    @Test
    fun fakeRuntimeToolsCallCanUseSharedExecutorAndMapConfirmationResult() {
        val executor = RecordingExecutor(
            McpToolResult.requiresConfirmation(
                message = "将删除 1 条便签，是否确认？",
                confirmationId = "pending-phase4-14",
                toolName = "notes.delete",
                commandLogId = 42L,
                affectedNoteIds = listOf(7L),
                resultJson = "{\"affected_note_ids\":[7]}",
                previewJson = "{\"affected_note_ids\":[7]}",
            ),
        )
        val client = McpProtocolClient(setOf(executor), ToolCallEventStore())

        val response = client.handleJsonRpc(
            "{\"jsonrpc\":\"2.0\",\"id\":\"fake-call\",\"method\":\"tools/call\",\"params\":{\"name\":\"notes.delete\",\"arguments\":{\"note_id\":7}}}",
        )

        assertEquals(McpToolStatus.RequiresConfirmation, response.status)
        assertFalse(response.blocked)
        assertEquals("notes.delete", executor.lastName)
        assertEquals("{\"note_id\":7}", executor.lastArgumentsJson)
        assertNotNull(executor.lastContext)
        assertTrue(response.responseJson.contains("pending-phase4-14"))
        assertTrue(response.responseJson.contains("requires_confirmation"))
        assertTrue(response.responseJson.contains("command_log_id"))
    }

    @Test
    fun mcpToolContextDefinesSourceContractForPhase5() {
        assertEquals("voice", McpToolContext.SOURCE_VOICE)
        assertEquals("local_tool_simulator", McpToolContext.SOURCE_LOCAL_TOOL_SIMULATOR)
        assertEquals("wakeword", McpToolContext.SOURCE_WAKEWORD)
    }

    @Test
    fun runtimeDiagnosticsExposePhase4ToolFieldsAndRemovePhase3BlockedWording() {
        val stateSource = sourceText("assistant-runtime", "src/main/java/com/er1cmo/noteassistant/assistant/runtime/state/AssistantState.kt")
        val controllerSource = sourceText("assistant-runtime", "src/main/java/com/er1cmo/noteassistant/assistant/runtime/controller/LocalAssistantController.kt")

        listOf(
            "phase4RealToolCallVerified",
            "lastToolName",
            "lastToolStatus",
            "lastCommandLogId",
            "lastConfirmationId",
        ).forEach { required ->
            assertTrue("AssistantState missing $required", stateSource.contains(required))
        }

        assertFalse("runtime status text must not keep Phase3 boundary wording", controllerSource.contains("Phase3 边界处理"))
        assertFalse("runtime diagnostics must not expose tool_block wording", controllerSource.contains("tool_block"))
    }

    @Test
    fun assistantRuntimeDoesNotDependOnNotesDomainDataOrRoom() {
        val source = moduleSourceText("assistant-runtime")
        val forbidden = listOf(
            "NoteCommandService",
            "com.er1cmo.noteassistant.notes.domain",
            "com.er1cmo.noteassistant.notes.data",
            "androidx.room",
            "RoomDatabase",
            "NoteDao",
            "NoteRepositoryImpl",
        )
        forbidden.forEach { token ->
            assertFalse("assistant-runtime must not depend on $token", source.contains(token))
        }
    }

    private class RecordingExecutor(
        private val result: McpToolResult,
    ) : McpToolExecutor {
        var lastName: String? = null
        var lastArgumentsJson: String? = null
        var lastContext: McpToolContext? = null

        override fun listDescriptors(): List<McpToolDescriptor> = emptyList()
        override fun findTool(name: String): McpTool? = null
        override suspend fun execute(name: String, argumentsJson: String, context: McpToolContext): McpToolResult {
            lastName = name
            lastArgumentsJson = argumentsJson
            lastContext = context
            return result.copy(toolName = name, argumentsJson = argumentsJson)
        }
    }

    private fun moduleSourceText(module: String): String {
        val sourceRoot = projectRoot().resolve(module).resolve("src/main/java")
        require(sourceRoot.isDirectory()) { "Missing source root: $sourceRoot" }
        return Files.walk(sourceRoot).use { stream ->
            stream
                .filter { Files.isRegularFile(it) && it.name.endsWith(".kt") }
                .map { Files.readString(it) }
                .toList()
                .joinToString("\n")
        }
    }

    private fun sourceText(module: String, relativePath: String): String {
        return Files.readString(projectRoot().resolve(module).resolve(relativePath))
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }
}
