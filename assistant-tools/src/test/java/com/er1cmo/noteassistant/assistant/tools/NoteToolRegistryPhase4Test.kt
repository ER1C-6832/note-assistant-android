package com.er1cmo.noteassistant.assistant.tools

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteToolRegistryPhase4Test {
    @Test
    fun registryListsRequiredPhase4ToolsIncludingGapFillTools() {
        val registry = NoteToolRegistry(requiredPhase4ToolNames.map { FakeTool(it) }.toSet())

        val names = registry.listToolNames()

        requiredPhase4ToolNames.forEach { name ->
            assertTrue("missing tool $name", names.contains(name))
        }
        assertTrue("missing pinned-list gap tool", names.contains("notes.list_pinned"))
    }

    @Test
    fun descriptorSchemasExposeRiskMutationAndConfirmationFields() {
        val descriptor = FakeTool(
            name = "notes.clear_done",
            risk = McpRiskLevel.High,
            mutates = true,
            confirmation = McpToolDescriptor.CONFIRMATION_REQUIRED,
        ).descriptor.toJsonObject()

        assertEquals("notes.clear_done", descriptor.getString("name"))
        assertEquals("high", descriptor.getString("risk"))
        assertTrue(descriptor.getBoolean("mutates"))
        assertEquals(McpToolDescriptor.CONFIRMATION_REQUIRED, descriptor.getString("confirmation"))
        assertTrue(descriptor.getJSONObject("inputSchema").has("type"))
    }

    @Test
    fun unknownToolFailsClosed() = runBlocking {
        val registry = NoteToolRegistry(setOf(FakeTool("notes.search")))

        val result = registry.execute("notes.unknown", "{}", McpToolContext())

        assertEquals(McpToolStatus.NotImplemented.storageValue, result.status)
        assertEquals("notes.unknown", result.toolName)
        assertFalse(result.message.contains("成功"))
    }

    @Test
    fun requiresConfirmationEnvelopeKeepsPendingIdAndPreview() {
        val envelope = McpToolResult.requiresConfirmation(
            message = "将删除 1 条便签，是否确认？",
            confirmationId = "pending-1",
            toolName = "notes.delete",
            previewJson = JSONObject().put("affected_note_ids", listOf(1L)).toString(),
            affectedNoteIds = listOf(1L),
        ).toEnvelopeJsonObject()

        assertEquals("requires_confirmation", envelope.getString("status"))
        assertTrue(envelope.getBoolean("requires_confirmation"))
        assertEquals("pending-1", envelope.getString("confirmation_id"))
        assertEquals("notes.delete", envelope.getString("tool_name"))
        assertEquals(1, envelope.getJSONArray("affected_note_ids").getLong(0))
        assertTrue(envelope.has("confirmation_preview"))
    }

    private class FakeTool(
        override val name: String,
        private val risk: McpRiskLevel = McpRiskLevel.Low,
        private val mutates: Boolean = false,
        private val confirmation: String = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
    ) : McpTool {
        override val description: String = "fake $name"
        override val riskLevel: McpRiskLevel = risk
        override val descriptor: McpToolDescriptor = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = "{\"type\":\"object\",\"additionalProperties\":true}",
            riskLevel = risk,
            mutates = mutates,
            confirmation = confirmation,
            examples = listOf("example"),
        )
        override suspend fun call(argumentsJson: String): McpToolResult = McpToolResult.success("ok", toolName = name, risk = risk)
    }

    private companion object {
        val requiredPhase4ToolNames = listOf(
            "notes.search",
            "notes.list_recent",
            "notes.get",
            "notes.list_by_tag",
            "notes.list_archived",
            "notes.list_deleted",
            "notes.list_todos",
            "notes.list_done",
            "notes.list_pinned",
            "notes.create",
            "notes.append",
            "notes.update_title",
            "notes.replace_content",
            "notes.toggle_done",
            "notes.pin",
            "notes.archive",
            "notes.delete",
            "notes.restore",
            "notes.restore_revision",
            "notes.clear_done",
            "tags.create",
            "tags.search",
            "tags.list",
            "tags.rename",
            "tags.delete",
            "tags.bind",
            "ui.open_note",
            "ui.show_search",
            "ui.show_note_list",
            "ui.show_tag",
            "ui.show_archive",
            "ui.show_trash",
            "ui.show_confirmation",
            "assistant.confirm",
            "assistant.reject",
            "assistant.list_pending_confirmations",
        )
    }
}
