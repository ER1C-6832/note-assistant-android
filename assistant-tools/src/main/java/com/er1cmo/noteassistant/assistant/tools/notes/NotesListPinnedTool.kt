package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
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
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "notes.list_pinned"
    override val description: String = "列出置顶便签，并同步显示置顶列表。"
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
                "include_deleted": { "type": "boolean" }
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
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val includeArchived = parser.boolean("include_archived", false)
        val includeDeleted = parser.boolean("include_deleted", false)
        val notes = buildList {
            addAll(noteUseCases.listNotes().first())
            if (includeArchived) addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted) addAll(noteUseCases.listDeletedNotes().first())
        }
            .distinctBy { it.id }
            .filter { it.pinned }
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            .take(limit)

        uiCommandBus.emit(UiCommand.ShowPinned)
        val resultJson = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("kind", "pinned")
            .put("count", notes.size)
            .put("results", notes.toAssistantNoteResultsJsonArray())
            .put("ui_effect", "show_pinned")
            .put("assistant_next_step_hint", "Pinned notes were listed and the pinned UI was shown. Do not call an extra ui.show_pinned tool.")
            .toString()
        return McpToolResult.success(
            message = "已列出 ${notes.size} 条置顶便签",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }
}
