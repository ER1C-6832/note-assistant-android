package com.er1cmo.noteassistant.assistant.tools.assistant

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class AssistantConfirmTool @Inject constructor(
    private val commandService: NoteCommandService,
) : McpTool {
    override val name: String = "assistant.confirm"
    override val description: String = "确认执行一个 pending confirmation。必须传入 confirmation_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "confirmation_id": { "type": "string" }
              },
              "required": ["confirmation_id"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.High,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("确认执行刚才的操作", "确认 confirmation_id=..."),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "assistant.confirm 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val confirmationId = runCatching { parser.requireString("confirmation_id") }.getOrElse { error ->
            return McpToolResult.failed(
                message = error.message ?: "缺少 confirmation_id",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "validation_error",
                risk = McpRiskLevel.High,
            )
        }
        val result = commandService.confirmPendingCommand(confirmationId)
        return result.toMcpToolResult(toolName = name, argumentsJson = argumentsJson)
    }
}
