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
import org.json.JSONObject

class TagsBindTool @Inject constructor(
    private val commandService: NoteCommandService,
) : McpTool {
    override val name: String = "tags.bind"
    override val description: String = "给一条或多条便签添加标签。Phase4 Gate B 仅开放 add 模式；replace/delete 后续 Gate C/D 开放。"
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
                "mode": { "type": "string", "enum": ["add"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("给这条便签加上客户标签", "给 note_id=12 添加 Phase4 标签"),
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
        if (mode != "add") {
            return McpToolResult.blocked(
                toolName = name,
                message = "Phase4 Gate B 只允许 tags.bind add；$mode 模式将在高风险确认阶段开放。",
                argumentsJson = argumentsJson,
                resultJson = JSONObject()
                    .put("blocked", true)
                    .put("allowed_mode", "add")
                    .put("requested_mode", mode)
                    .toString(),
                errorCode = "mode_not_enabled_in_gate_b",
            )
        }
        val normalizedArguments = JSONObject(parser.raw().toString())
            .put("mode", "add")
            .toString()
        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = normalizedArguments,
            source = context.toCommandSource(),
        )
        return commandResult.toMcpToolResult(toolName = name, argumentsJson = normalizedArguments)
    }
}
