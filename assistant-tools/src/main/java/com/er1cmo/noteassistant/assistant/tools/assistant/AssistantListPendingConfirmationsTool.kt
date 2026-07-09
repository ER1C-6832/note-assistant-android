package com.er1cmo.noteassistant.assistant.tools.assistant

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class AssistantListPendingConfirmationsTool @Inject constructor(
    private val commandService: Phase4ExtendedCommandService,
) : McpTool {
    override val name: String = "assistant.list_pending_confirmations"
    override val description: String = "列出未过期的 pending confirmation，供语音确认/拒绝恢复上下文。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("列出待确认操作"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "assistant.list_pending_confirmations 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val limit = parser.int("limit", 5).coerceIn(1, 50)
        val pending = commandService.listPendingConfirmations(limit)
        val result = JSONObject()
            .put("count", pending.size)
            .put("results", JSONArray().also { array ->
                pending.forEach { item ->
                    val preview = runCatching { JSONObject(item.previewJson) }.getOrElse { JSONObject().put("summary", item.previewJson.take(120)) }
                    array.put(
                        JSONObject()
                            .put("confirmation_id", item.confirmationId)
                            .put("tool_name", item.toolName.storageValue)
                            .put("summary", preview.optString("summary", item.toolName.storageValue))
                            .put("expires_at", item.expiresAt)
                            .put("command_log_id", item.commandLogId),
                    )
                }
            })
            .toString()
        return McpToolResult.success("当前有 ${pending.size} 个待确认操作", result, name, McpRiskLevel.Low)
    }
}
