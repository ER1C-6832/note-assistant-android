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

class UiShowNoteListTool @Inject constructor(
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.show_note_list"
    override val description: String = "显示便签列表。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
  "type": "object",
  "additionalProperties": true
}
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("显示便签列表。"),
    )
    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())
    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        
        uiCommandBus.emit(UiCommand.ShowNoteList)
        return McpToolResult.success("显示便签列表。", JSONObject().put("shown", true).put("tool", name).toString(), name, McpRiskLevel.Low)
    }
}
