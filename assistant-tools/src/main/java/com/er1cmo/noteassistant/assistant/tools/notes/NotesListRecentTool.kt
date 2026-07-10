package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class NotesListRecentTool @Inject constructor(
    private val commandService: NoteCommandService,
) : McpTool {
    override val name: String = "notes.list_recent"
    override val description: String =
        "列出最近更新的便签并直接返回标题和内容摘要。只用于最近/刚才语境；查关键词请用 notes.resolve 或 notes.search。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer", "minimum": 1, "maximum": 50 },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "列出最近五条便签：{\"limit\":5}",
            "查找关键词时不要用 list_recent；用 notes.resolve 或 notes.search",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = argumentsJson,
            source = context.toCommandSource(),
        )
        val result = commandResult.toMcpToolResult(toolName = name, argumentsJson = argumentsJson)
        if (result.status != "success") return result
        return result.copy(message = buildRecentSummary(result.resultJson))
    }

    private fun buildRecentSummary(resultJson: String?): String {
        val root = runCatching { JSONObject(resultJson ?: "{}") }.getOrElse { return "已列出最近便签，详细标题和内容见工具结果。" }
        val results = root.optJSONArray("results") ?: JSONArray()
        if (results.length() == 0) return "最近没有可列出的便签。"
        val items = buildList {
            for (index in 0 until minOf(results.length(), 6)) {
                val item = results.optJSONObject(index) ?: continue
                val title = item.optString("title", "").ifBlank { "未命名便签" }
                val snippet = item.optString("snippet", "").replace(Regex("\\s+"), " ").ifBlank { "正文为空" }.take(72)
                add("${index + 1}.《$title》：$snippet")
            }
        }
        return buildString {
            append("最近便签共 ")
            append(results.length())
            append(" 条。")
            append(items.joinToString("；"))
            if (results.length() > items.size) append("；其余 ${results.length() - items.size} 条见工具结果。")
        }
    }
}
