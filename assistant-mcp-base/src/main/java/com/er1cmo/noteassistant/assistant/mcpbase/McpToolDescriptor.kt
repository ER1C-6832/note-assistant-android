package com.er1cmo.noteassistant.assistant.mcpbase

import org.json.JSONObject

data class McpToolDescriptor(
    val name: String,
    val description: String,
    val inputSchemaJson: String = DEFAULT_OBJECT_SCHEMA,
    val riskLevel: McpRiskLevel = McpRiskLevel.Low,
    val mutates: Boolean = false,
    val confirmation: String = CONFIRMATION_NOT_REQUIRED,
    val examples: List<String> = emptyList(),
) {
    fun toJsonObject(): JSONObject {
        val inputSchema = runCatching { JSONObject(inputSchemaJson) }.getOrElse {
            JSONObject(DEFAULT_OBJECT_SCHEMA)
        }
        return JSONObject()
            .put("name", name)
            .put("description", description)
            .put("inputSchema", inputSchema)
            .put("risk", riskLevel.storageValue)
            .put("mutates", mutates)
            .put("confirmation", confirmation)
            .put("examples", examples)
    }

    companion object {
        const val CONFIRMATION_NOT_REQUIRED = "not_required"
        const val CONFIRMATION_MAY_BE_REQUIRED = "may_be_required"
        const val CONFIRMATION_REQUIRED = "required"
        const val DEFAULT_OBJECT_SCHEMA = "{\"type\":\"object\",\"additionalProperties\":true}"
    }
}
