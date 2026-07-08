package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

abstract class AbstractNoteListTool(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    abstract override val name: String
    abstract override val description: String
    abstract fun loadNotes(noteUseCases: NoteUseCases, limit: Int): Flow<List<Note>>

    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor
        get() = McpToolDescriptor(
            name = name,
            description = description,
            inputSchemaJson = """
                {
                  "type": "object",
                  "properties": {
                    "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
                  },
                  "additionalProperties": false
                }
            """.trimIndent(),
            riskLevel = McpRiskLevel.Low,
            mutates = false,
            confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "$name 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val limit = parser.int("limit", 20).coerceIn(1, 50)
        val notes = loadNotes(noteUseCases, limit)
            .first()
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            .take(limit)
        return McpToolResult.success(
            message = "已列出 ${notes.size} 条便签",
            resultJson = notes.toNoteListResultJson(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }
}
