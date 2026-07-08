package com.er1cmo.noteassistant.assistant.tools.assistant

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class AssistantListPendingConfirmationsTool @Inject constructor(
    private val commandTraceRepository: CommandTraceRepository,
) : McpTool {
    override val name: String = "assistant.list_pending_confirmations"
    override val description: String = "列出待确认操作，供语音确认或设置页调试使用。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer", "minimum": 1, "maximum": 50 },
                "only_pending": { "type": "boolean" }
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
            return McpToolResult.invalidJson(toolName = name, argumentsJson = argumentsJson, message = "assistant.list_pending_confirmations 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val limit = parser.int("limit", 20).coerceIn(1, 50)
        val onlyPending = parser.boolean("only_pending", true)
        val pending = commandTraceRepository.listPendingConfirmations(limit = limit, onlyPending = onlyPending)
        val resultJson = JSONObject()
            .put("count", pending.size)
            .put("results", JSONArray().also { array ->
                pending.forEach { item ->
                    array.put(
                        JSONObject()
                            .put("confirmation_id", item.confirmationId)
                            .put("command_log_id", item.commandLogId)
                            .put("tool_name", item.toolName.storageValue)
                            .put("risk", item.riskLevel.storageValue)
                            .put("status", item.status.storageValue)
                            .put("preview", runCatching { JSONObject(item.previewJson) }.getOrDefault(JSONObject().put("preview", item.previewJson)))
                            .put("created_at", item.createdAt)
                            .put("expires_at", item.expiresAt),
                    )
                }
            })
            .toString()
        val pendingCount = pending.count { it.status == ConfirmationStatus.Pending }
        return McpToolResult.success(
            message = "待确认操作 $pendingCount 个，返回 ${pending.size} 条记录",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
        )
    }
}
