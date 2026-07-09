package com.er1cmo.noteassistant.assistant.tools.ui

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject
import org.json.JSONObject

class UiShowTagTool @Inject constructor(
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.show_tag"
    override val description: String = "显示指定标签相关便签。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "tag_id": { "type": "integer" },
                "tag_name": { "type": "string" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("显示客户标签"),
    )
    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())
    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error -> return McpToolResult.invalidJson(name, argumentsJson, "参数不是有效 JSON：${error.message ?: "解析失败"}") }
        val tagId = parser.optionalLong("tag_id")?.takeIf { it > 0 }
        val tagName = parser.optionalString("tag_name", "")
        uiCommandBus.emit(UiCommand.ShowTag(tagId, tagName))
        return McpToolResult.success("已显示标签", JSONObject().put("shown", true).put("tag_id", tagId ?: JSONObject.NULL).put("tag_name", tagName).toString(), name, McpRiskLevel.Low, affectedTagIds = tagId?.let { listOf(it) } ?: emptyList())
    }
}
