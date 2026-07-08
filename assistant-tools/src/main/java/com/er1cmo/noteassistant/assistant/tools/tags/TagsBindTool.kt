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
    override val description: String = "给一条或多条便签添加或替换标签。add 为中风险直接执行；replace 为高风险，必须先返回确认请求。"
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
                "mode": { "type": "string", "enum": ["add", "replace"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf("给这条便签加上客户标签", "把这条便签的标签替换成客户和紧急"),
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
        if (mode != "add" && mode != "replace") {
            return McpToolResult.blocked(
                toolName = name,
                message = "Phase4 Gate C 只允许 tags.bind add/replace；$mode 模式暂未开放。",
                argumentsJson = argumentsJson,
                resultJson = JSONObject()
                    .put("blocked", true)
                    .put("allowed_modes", listOf("add", "replace"))
                    .put("requested_mode", mode)
                    .toString(),
                errorCode = "mode_not_enabled_in_gate_c",
            )
        }
        val normalizedArguments = JSONObject(parser.raw().toString())
            .put("mode", mode)
            .toString()
        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = normalizedArguments,
            source = context.toCommandSource(),
        )
        return commandResult.toMcpToolResult(toolName = name, argumentsJson = normalizedArguments)
    }
}
