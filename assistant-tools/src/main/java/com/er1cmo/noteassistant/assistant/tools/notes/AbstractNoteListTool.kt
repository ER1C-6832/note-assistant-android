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
import kotlinx.coroutines.flow.first
import org.json.JSONObject

abstract class AbstractNoteListTool(
    private val noteUseCases: NoteUseCases,
    private val uiCommandBus: UiCommandBus? = null,
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
        val notes = loadNotes()
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            .take(limit)
        buildUiCommand(notes = notes, parser = parser)?.let { command ->
            uiCommandBus?.emit(command)
        }
        return McpToolResult.success(
            message = notes.toAssistantReadableListMessage(spokenLabel()),
            resultJson = JSONObject()
                .putAssistantNoteReferenceRule()
                .put("kind", listKind)
                .put("count", notes.size)
                .put("results", notes.toAssistantNoteResultsJsonArray())
                .put("ui_effect", uiEffectName())
                .put("assistant_next_step_hint", nextStepHint(notes))
                .toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = notes.map { it.id },
        )
    }

    protected suspend fun activeNotes(): List<Note> = noteUseCases.listNotes().first()
    protected suspend fun archivedNotes(): List<Note> = noteUseCases.listArchivedNotes().first()
    protected suspend fun deletedNotes(): List<Note> = noteUseCases.listDeletedNotes().first()

    protected open fun buildUiCommand(notes: List<Note>, parser: ToolArgumentParser): UiCommand? = null

    protected open fun uiEffectName(): String = "none"

    protected open fun spokenLabel(): String = title.substringBefore('，').substringBefore('。')

    protected open fun nextStepHint(notes: List<Note>): String {
        return if (notes.isEmpty()) {
            "The list result is empty. Do not loop search/list tools; answer that there are no notes in this scope."
        } else {
            "The list result already contains titles and content summaries. Summarize these results and do not call another list/search tool."
        }
    }
}
