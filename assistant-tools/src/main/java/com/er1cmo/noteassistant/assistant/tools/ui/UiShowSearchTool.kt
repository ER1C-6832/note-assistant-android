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

class UiShowSearchTool @Inject constructor(
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.show_search"
    override val description: String = "显示搜索结果或搜索页。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
  "type": "object",
  "properties": {
    "query": { "type": "string" }
  },
  "additionalProperties": false
}
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("显示搜索结果或搜索页。"),
    )
    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())
    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error -> return McpToolResult.invalidJson(name, argumentsJson, "参数不是有效 JSON：${error.message ?: "解析失败"}") }
        uiCommandBus.emit(UiCommand.ShowSearch(parser.optionalString("query", "")))
        return McpToolResult.success("显示搜索结果或搜索页。", JSONObject().put("shown", true).put("tool", name).toString(), name, McpRiskLevel.Low)
    }
}
