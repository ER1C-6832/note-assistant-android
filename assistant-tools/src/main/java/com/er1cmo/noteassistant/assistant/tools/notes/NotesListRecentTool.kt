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

class NotesListRecentTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.list_recent"
    override val description: String =
        "列出最近更新的便签，返回完整内容和用户可见引用。它只用于上下文；要找标题为某个值的便签，请调用 notes.search exact_title。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "limit": { "type": "integer", "minimum": 1, "maximum": 100 },
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
        examples = listOf(
            "最近记了什么",
            "列出最近二十条：{\"limit\":20}",
            "如果标题没有出现在最近结果里，应继续调用 notes.search exact_title",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.list_recent 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val scope = parser.optionalString("scope", "active").ifBlank { "active" }.lowercase()
        val includeArchived = if (raw.has("include_archived")) parser.boolean("include_archived", false) else scope == "all" || scope == "archived"
        val includeDeleted = if (raw.has("include_deleted")) parser.boolean("include_deleted", false) else scope == "all" || scope == "deleted"
        val notes = loadRecentPool(scope, includeArchived, includeDeleted).sortedByRecent()
        val limited = notes.take(limit)
        val resultJson = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("kind", "recent")
            .put("scope", scope)
            .put("include_archived", includeArchived)
            .put("include_deleted", includeDeleted)
            .put("count", limited.size)
            .put("total_matching_count", notes.size)
            .put("result_is_limited", notes.size > limited.size)
            .put("results", limited.toAssistantNoteResultsJsonArray())
            .put(
                "assistant_next_step_hint",
                "If the user named a title and it is not in this limited recent list, call notes.search with exact_title instead of guessing note_id.",
            )
            .toString()
        return McpToolResult.success(
            message = "已列出最近 ${limited.size} 条便签；当前范围共有 ${notes.size} 条",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = limited.map { it.id },
        )
    }

    private suspend fun loadRecentPool(
        scope: String,
        includeArchived: Boolean,
        includeDeleted: Boolean,
    ): List<Note> {
        return buildList {
            if (scope != "archived" && scope != "deleted") addAll(noteUseCases.listNotes().first())
            if (includeArchived || scope == "archived") addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted || scope == "deleted") addAll(noteUseCases.listDeletedNotes().first())
        }.distinctBy { it.id }
    }
}

private fun List<Note>.sortedByRecent(): List<Note> = sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
