package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class NotesListPinnedTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.list_pinned"
    override val description: String = "列出置顶便签，返回完整内容和 note_ref，用于语音查询当前固定的重要便签。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer" },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" },
                "scope": { "type": "string", "enum": ["active", "all", "archived", "deleted"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("列出置顶便签", "当前有哪些固定的便签"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "notes.list_pinned 参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val raw = parser.raw()
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val scope = parser.optionalString("scope", "active").ifBlank { "active" }.lowercase()
        val includeArchived = if (raw.has("include_archived")) parser.boolean("include_archived", false) else scope == "all" || scope == "archived"
        val includeDeleted = if (raw.has("include_deleted")) parser.boolean("include_deleted", false) else scope == "all" || scope == "deleted"
        val all = buildList {
            if (scope != "archived" && scope != "deleted") addAll(noteUseCases.listNotes().first())
            if (includeArchived || scope == "archived") addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted || scope == "deleted") addAll(noteUseCases.listDeletedNotes().first())
        }
            .distinctBy { it.id }
            .filter { it.pinned }
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
        val notes = all.take(limit)
        val resultJson = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("kind", "pinned")
            .put("scope", scope)
            .put("include_archived", includeArchived)
            .put("include_deleted", includeDeleted)
            .put("count", notes.size)
            .put("total_matching_count", all.size)
            .put("result_is_limited", all.size > notes.size)
            .put("results", notes.toAssistantNoteResultsJsonArray())
            .toString()
        return McpToolResult.success(
            message = "已列出 ${notes.size} 条置顶便签；当前范围共有 ${all.size} 条",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }
}
