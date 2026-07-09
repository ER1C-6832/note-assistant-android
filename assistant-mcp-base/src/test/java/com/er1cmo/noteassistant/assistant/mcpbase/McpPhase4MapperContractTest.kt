package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpPhase4MapperContractTest {
    @Test
    fun toolDescriptorJsonContainsPhase4ContractFields() {
        val descriptor = McpToolDescriptor(
            name = "notes.append",
            description = "向指定便签追加内容，不覆盖原正文。",
            inputSchemaJson = """{"type":"object","properties":{"note_id":{"type":"integer"},"content":{"type":"string"}},"required":["note_id","content"]}""",
            riskLevel = McpRiskLevel.Medium,
            mutates = true,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED_BY_DEFAULT,
            examples = listOf("给刚才那条补充一句"),
        ).toJsonObject()

        assertEquals("notes.append", descriptor.getString("name"))
        assertEquals("medium", descriptor.getString("risk"))
        assertTrue(descriptor.getBoolean("mutates"))
        assertTrue(descriptor.getJSONObject("inputSchema").getJSONObject("properties").has("note_id"))
        assertEquals(1, descriptor.getJSONArray("examples").length())
    }

    @Test
    fun resultEnvelopePreservesMachineReadableFields() {
        val envelope = McpToolResult.success(
            message = "已创建便签",
            resultJson = JSONObject().put("note_id", 42).toString(),
            toolName = "notes.create",
            risk = McpRiskLevel.Medium,
            commandLogId = 7,
            affectedNoteIds = listOf(42),
        ).toEnvelopeJsonObject()

        assertEquals("success", envelope.getString("status"))
        assertFalse(envelope.getBoolean("requires_confirmation"))
        assertEquals(7, envelope.getLong("command_log_id"))
        assertEquals(42, envelope.getJSONArray("affected_note_ids").getLong(0))
        assertEquals(42, envelope.getJSONObject("result").getLong("note_id"))
    }

    @Test
    fun invalidJsonMapsToSafeErrorStatus() {
        val result = McpToolResult.invalidJson("notes.create", "{bad")
        val envelope = result.toEnvelopeJsonObject()

        assertEquals("failed", envelope.getString("status"))
        assertEquals("invalid_json", envelope.getString("error_code"))
        assertEquals("notes.create", envelope.getString("tool_name"))
    }
}
