package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class TagsSearchTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "tags.search"
    override val description: String = "搜索本地标签，并返回标签关联便签数量。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string" },
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
        examples = listOf("搜索客户相关标签", "有哪些标签包含 Phase4"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "tags.search 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val query = parser.optionalString("query")
        val limit = parser.int("limit", 20).coerceIn(1, 50)
        val includeArchived = parser.boolean("include_archived", false)
        val includeDeleted = parser.boolean("include_deleted", false)
        val tags = noteUseCases.listTags().first()
        val notes = buildList {
            addAll(noteUseCases.listNotes().first())
            if (includeArchived) addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted) addAll(noteUseCases.listDeletedNotes().first())
        }.distinctBy { it.id }
        val normalizedQuery = query.trim().lowercase()
        val results = tags
            .filter { tag ->
                normalizedQuery.isBlank() ||
                    tag.name.lowercase().contains(normalizedQuery) ||
                    tag.normalizedName.lowercase().contains(normalizedQuery)
            }
            .sortedWith(compareBy<Tag> { it.name.lowercase() }.thenBy { it.id })
            .take(limit)
        val resultJson = JSONObject()
            .put("query", query)
            .put("count", results.size)
            .put("results", JSONArray().also { array ->
                results.forEach { tag ->
                    array.put(tag.toJson(notes))
                }
            })
            .toString()
        return McpToolResult.success(
            message = "找到 ${results.size} 个标签",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedTagIds = results.map { it.id },
        )
    }

    private fun Tag.toJson(notes: List<Note>): JSONObject {
        val linkedNotes = notes.filter { note -> note.tags.any { it.id == id || it.normalizedName == normalizedName } }
        return JSONObject()
            .put("tag_id", id)
            .put("name", name)
            .put("normalized_name", normalizedName)
            .put("color", color ?: JSONObject.NULL)
            .put("linked_note_count", linkedNotes.size)
            .put("sample_note_ids", JSONArray(linkedNotes.take(8).map { it.id }))
            .put("updated_at", updatedAt)
    }
}
