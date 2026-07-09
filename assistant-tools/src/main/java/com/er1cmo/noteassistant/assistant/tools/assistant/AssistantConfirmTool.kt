package com.er1cmo.noteassistant.assistant.tools.assistant

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import javax.inject.Inject

class AssistantConfirmTool @Inject constructor(
    private val commandService: Phase4ExtendedCommandService,
) : McpTool {
    override val name: String = "assistant.confirm"
    override val description: String = "确认执行 pending confirmation。confirmation_id 可省略；若当前只有一个待确认操作，会确认最近这个操作。"
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
        val confirmationId = resolveConfirmationId(
            explicitConfirmationId = parser.optionalString("confirmation_id"),
            argumentsJson = argumentsJson,
        ) ?: return pendingConfirmationAmbiguous(argumentsJson)
        val result = commandService.confirmPendingCommand(confirmationId)
        return result.toMcpToolResult(toolName = name, argumentsJson = argumentsJson)
    }

    private suspend fun resolveConfirmationId(
        explicitConfirmationId: String,
        argumentsJson: String,
    ): String? {
        if (explicitConfirmationId.isNotBlank()) return explicitConfirmationId
        val pending = commandService.listPendingConfirmations(limit = 2)
        return when (pending.size) {
            1 -> pending.first().confirmationId
            else -> null
        }
    }

    private suspend fun pendingConfirmationAmbiguous(argumentsJson: String): McpToolResult {
        val pending = commandService.listPendingConfirmations(limit = 2)
        val message = when (pending.size) {
            0 -> "没有待确认操作可确认"
            else -> "有多个待确认操作，请指定 confirmation_id"
        }
        return McpToolResult.failed(
            message = message,
            toolName = name,
            argumentsJson = argumentsJson,
            errorCode = "confirmation_id_required",
            risk = McpRiskLevel.High,
        )
    }
}
