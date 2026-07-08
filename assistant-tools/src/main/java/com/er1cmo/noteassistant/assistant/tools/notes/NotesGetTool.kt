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
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class NotesGetTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.get"
    override val description: String = "按 note_id 读取一条便签的完整字段。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "include_deleted": { "type": "boolean" }
              },
              "required": ["note_id"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("读取 note_id=123 的便签"),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.get 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val noteId = runCatching { parser.requireLong("note_id") }.getOrElse { error ->
            return McpToolResult.failed(
                message = error.message ?: "缺少 note_id",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "validation_error",
                risk = McpRiskLevel.Low,
            )
        }
        val includeDeleted = parser.boolean("include_deleted", false)
        val note = noteUseCases.getNote(noteId)
            ?: return McpToolResult.failed(
                message = "没有找到便签：$noteId",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "not_found",
                risk = McpRiskLevel.Low,
            )
        if (note.deleted && !includeDeleted) {
            return McpToolResult.failed(
                message = "便签已在最近删除中",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "already_deleted",
                risk = McpRiskLevel.Low,
            )
        }
        return McpToolResult.success(
            message = "已读取便签：${note.title.ifBlank { "未命名便签" }}",
            resultJson = note.toToolResultJson().toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = listOf(note.id),
        )
    }

    private fun Note.toToolResultJson(): JSONObject = JSONObject()
        .put("note_id", id)
        .put("title", title)
        .put("content", content)
        .put("type", type.storageValue())
        .put("done", isDone)
        .put("pinned", pinned)
        .put("archived", archived)
        .put("deleted", deleted)
        .put("color", color ?: JSONObject.NULL)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("tags", JSONArray(tags.map { it.name }))

    private fun NoteType.storageValue(): String = when (this) {
        NoteType.Normal -> "normal"
        NoteType.Todo -> "todo"
    }
}
