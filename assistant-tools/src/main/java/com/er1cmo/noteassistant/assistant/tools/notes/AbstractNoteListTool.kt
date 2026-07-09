package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

abstract class AbstractNoteListTool(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    abstract val listKind: String
    abstract val title: String
    abstract suspend fun loadNotes(): List<Note>

    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val description: String get() = title
    override val descriptor: McpToolDescriptor get() = McpToolDescriptor(
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
        examples = listOf(title),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(name, argumentsJson, "参数不是有效 JSON：${error.message ?: "解析失败"}")
        }
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val notes = loadNotes().sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id }).take(limit)
        return McpToolResult.success(
            message = "已列出 ${notes.size} 条便签",
            resultJson = JSONObject()
                .put("kind", listKind)
                .put("count", notes.size)
                .put("results", notes.toJsonArray())
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }

    protected suspend fun activeNotes(): List<Note> = noteUseCases.listNotes().first()
    protected suspend fun archivedNotes(): List<Note> = noteUseCases.listArchivedNotes().first()
    protected suspend fun deletedNotes(): List<Note> = noteUseCases.listDeletedNotes().first()

    private fun List<Note>.toJsonArray(): JSONArray = JSONArray().also { array ->
        forEach { note ->
            array.put(
                JSONObject()
                    .put("note_id", note.id)
                    .put("title", note.title)
                    .put("snippet", note.content.take(80))
                    .put("tags", JSONArray(note.tags.map { it.name }))
                    .put("type", if (note.type == NoteType.Todo) "todo" else "normal")
                    .put("done", note.isDone)
                    .put("pinned", note.pinned)
                    .put("archived", note.archived)
                    .put("deleted", note.deleted)
                    .put("updated_at", note.updatedAt),
            )
        }
    }
}
