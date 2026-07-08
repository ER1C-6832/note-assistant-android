package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class TagsListTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "tags.list"
    override val description: String = "列出全部标签。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer", "minimum": 1, "maximum": 100 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("列出所有标签"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(toolName = name, argumentsJson = argumentsJson, message = "tags.list 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val limit = parser.int("limit", 100).coerceIn(1, 100)
        val tags = noteUseCases.listTags().first().sortedBy { it.name }.take(limit)
        val resultJson = JSONObject()
            .put("count", tags.size)
            .put("results", JSONArray().also { array ->
                tags.forEach { tag ->
                    array.put(JSONObject().put("tag_id", tag.id).put("name", tag.name).put("normalized_name", tag.normalizedName))
                }
            })
            .toString()
        return McpToolResult.success(message = "已列出 ${tags.size} 个标签", resultJson = resultJson, toolName = name, risk = McpRiskLevel.Low, affectedTagIds = tags.map { it.id })
    }
}
