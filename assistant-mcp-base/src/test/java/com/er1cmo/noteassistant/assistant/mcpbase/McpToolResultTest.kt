package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolResultTest {
    @Test
    fun successEnvelopePreservesCommandAndAffectedIds() {
        val result = McpToolResult.success(
            toolName = "notes.create",
            message = "已创建便签：周五寄出样品",
            resultJson = "{\"note_id\":123}",
            risk = McpRiskLevel.Medium,
            commandLogId = 42,
            affectedNoteIds = listOf(123),
        )

        val envelope = result.toEnvelopeJsonObject()

        assertEquals("success", envelope.getString("status"))
        assertEquals("medium", envelope.getString("risk"))
        assertFalse(envelope.getBoolean("requires_confirmation"))
        assertEquals(42, envelope.getLong("command_log_id"))
        assertEquals(123, envelope.getJSONArray("affected_note_ids").getLong(0))
        assertEquals(123, envelope.getJSONObject("result").getLong("note_id"))
    }

    @Test
    fun requiresConfirmationEnvelopeContainsPendingMetadata() {
        val result = McpToolResult.requiresConfirmation(
            toolName = "notes.delete",
            message = "删除前需要确认",
            confirmationId = "abc123",
            commandLogId = 99,
            affectedNoteIds = listOf(1, 2),
            summary = "将删除 2 条便签",
            previewJson = "{\"titles\":[\"A\",\"B\"]}",
            expiresAt = 1720000000000,
        )

        val envelope = result.toEnvelopeJsonObject()

        assertEquals("requires_confirmation", envelope.getString("status"))
        assertTrue(envelope.getBoolean("requires_confirmation"))
        assertEquals("abc123", envelope.getString("confirmation_id"))
        assertEquals("将删除 2 条便签", envelope.getString("confirmation_summary"))
        assertEquals("A", envelope.getJSONObject("confirmation_preview").getJSONArray("titles").getString(0))
        assertEquals(1720000000000, envelope.getLong("expires_at"))
    }

    @Test
    fun failedResultExposesSafeErrorCodeOnly() {
        val result = McpToolResult.invalidJson(toolName = "notes.create", argumentsJson = "{bad")

        val envelope = result.toEnvelopeJsonObject()

        assertEquals("failed", envelope.getString("status"))
        assertEquals("invalid_json", envelope.getString("error_code"))
        assertEquals("notes.create", envelope.getString("tool_name"))
        assertEquals(McpToolStatus.Failed, result.statusEnum)
    }
}
