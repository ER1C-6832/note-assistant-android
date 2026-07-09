package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import org.json.JSONObject

class NotesListRecentTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.list_recent"
    override val description: String =
        "列出最近更新的便签，用于解析“刚才那条”“最近那条”。结果会返回标题、正文内容、标签和状态；note_id 是内部 ID，不要把用户说的数字直接当 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
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
        examples = listOf(
            "最近记了什么",
            "打开刚才那条前先列出最近便签",
            "删除刚才那条时，从结果的 title/content 判断用户指哪条，再用 note_ref 或该结果里的 note_id 调用删除",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val commandResult = commandService.execute(
            toolName = name,
            argumentsJson = argumentsJson,
            source = context.toCommandSource(),
        )
        val mapped = commandResult.toMcpToolResult(toolName = name, argumentsJson = argumentsJson)
        if (mapped.statusEnum != McpToolStatus.Success && mapped.statusEnum != McpToolStatus.PartialSuccess) {
            return mapped
        }
        val notes = commandResult.affectedNoteIds.mapNotNull { noteUseCases.getNote(it) }
        val original = runCatching { JSONObject(commandResult.resultJson ?: "{}") }.getOrDefault(JSONObject())
        val enriched = JSONObject()
            .put("kind", "recent")
            .put("count", notes.size)
            .put("source_count", original.optInt("count", notes.size))
            .putAssistantNoteReferenceRule()
            .put("results", notes.toAssistantNoteResultsJsonArray())
        return mapped.copy(resultJson = enriched.toString())
    }
}
