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

class UiShowConfirmationTool @Inject constructor(
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "ui.show_confirmation"
    override val description: String = "在 App 内显示待确认操作提示。它不执行命令，只展示已有 confirmation_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "confirmation_id": { "type": "string" },
                "title": { "type": "string" },
                "message": { "type": "string" }
              },
              "required": ["confirmation_id"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("显示刚才待确认的删除操作", "展示 confirmation_id=... 的确认提示"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "ui.show_confirmation 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val confirmationId = runCatching { parser.requireString("confirmation_id") }.getOrElse { error ->
            return McpToolResult.failed(
                message = error.message ?: "缺少 confirmation_id",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "validation_error",
                risk = McpRiskLevel.Low,
            )
        }
        val title = parser.optionalString("title", "待确认操作").ifBlank { "待确认操作" }
        val message = parser.optionalString("message", "请确认是否执行这个高风险操作。")
            .ifBlank { "请确认是否执行这个高风险操作。" }
        uiCommandBus.emit(UiCommand.ShowConfirmation(confirmationId = confirmationId, title = title, message = message))
        return McpToolResult.success(
            message = "已显示待确认操作：$confirmationId",
            resultJson = JSONObject()
                .put("shown", true)
                .put("confirmation_id", confirmationId)
                .put("title", title)
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
        )
    }
}
