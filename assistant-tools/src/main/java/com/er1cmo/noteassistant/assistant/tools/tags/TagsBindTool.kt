package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class TagsBindTool @Inject constructor(
    private val commandService: NoteCommandService,
) : McpTool {
    override val name: String = "tags.bind"
    override val description: String = "给一条或多条便签添加、移除或替换标签。replace/remove 或批量操作可能要求确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "tags": { "type": "array", "items": { "type": "string" } },
                "tag_text": { "type": "string" },
                "mode": { "type": "string", "enum": ["add", "remove", "replace"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf("给这条便签加上客户标签", "把 note_id=12 的标签替换成 GateD", "从这条便签移除客户标签"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "tags.bind 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val mode = parser.optionalString("mode", "add").ifBlank { "add" }.lowercase()
        if (mode !in setOf("add", "remove", "replace")) {
            return McpToolResult.failed(
                message = "tags.bind mode 只能是 add、remove 或 replace",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "validation_error",
                risk = McpRiskLevel.High,
            )
        }
        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = parser.raw().put("mode", mode).toString(),
            source = context.toCommandSource(),
        )
        return commandResult.toMcpToolResult(toolName = name, argumentsJson = parser.raw().toString())
    }
}
