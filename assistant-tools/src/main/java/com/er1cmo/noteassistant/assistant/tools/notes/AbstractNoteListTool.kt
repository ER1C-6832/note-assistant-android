package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import kotlinx.coroutines.flow.first
import org.json.JSONObject

abstract class AbstractNoteListTool(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    abstract val listKind: String
    abstract val title: String
    abstract suspend fun loadNotes(): List<Note>

    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val description: String
        get() = "$title。结果会返回用户可见标题、正文内容、标签和状态；note_id 仅是内部 ID，语音里不要把用户说的数字直接当 note_id。"
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
        val notes = loadNotes()
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            .take(limit)
        return McpToolResult.success(
            message = "已列出 ${notes.size} 条便签",
            resultJson = JSONObject()
                .put("kind", listKind)
                .put("count", notes.size)
                .putAssistantNoteReferenceRule()
                .put("results", notes.toAssistantNoteResultsJsonArray())
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }

    protected suspend fun activeNotes(): List<Note> = noteUseCases.listNotes().first()
    protected suspend fun archivedNotes(): List<Note> = noteUseCases.listArchivedNotes().first()
    protected suspend fun deletedNotes(): List<Note> = noteUseCases.listDeletedNotes().first()
}
