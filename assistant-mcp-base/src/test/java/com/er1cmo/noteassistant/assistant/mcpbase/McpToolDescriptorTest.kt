package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class McpToolDescriptorTest {
    @Test
    fun descriptorJsonContainsPhase4RequiredFields() {
        val descriptor = McpToolDescriptor(
            name = "notes.append",
            description = "向指定便签追加内容，不覆盖原正文。",
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "note_id": { "type": "integer" },
                    "content": { "type": "string" }
                  },
                  "required": ["note_id", "content"]
                }
            """.trimIndent(),
            riskLevel = McpRiskLevel.Medium,
            mutates = true,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED_BY_DEFAULT,
            examples = listOf("给刚才那条补充一句，快递单号待同步"),
        )

        val json = descriptor.toJsonObject()

        assertEquals("notes.append", json.getString("name"))
        assertEquals("medium", json.getString("risk"))
        assertTrue(json.getBoolean("mutates"))
        assertEquals(McpToolDescriptor.CONFIRMATION_NOT_REQUIRED_BY_DEFAULT, json.getString("confirmation"))
        assertEquals("object", json.getJSONObject("inputSchema").getString("type"))
        assertEquals(1, json.getJSONArray("examples").length())
    }

    @Test
    fun invalidSchemaFallsBackToObjectSchema() {
        val json = McpToolDescriptor(
            name = "phase4.test",
            description = "test",
            inputSchemaJson = "not-json",
        ).toJsonObject()

        assertEquals("object", json.getJSONObject("inputSchema").getString("type"))
        assertFalse(json.getBoolean("mutates"))
    }
}
