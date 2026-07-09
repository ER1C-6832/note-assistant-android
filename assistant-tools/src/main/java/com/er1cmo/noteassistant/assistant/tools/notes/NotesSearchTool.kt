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
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import org.json.JSONObject

class NotesSearchTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.search"
    override val description: String =
        "搜索本地便签，默认排除归档和最近删除。结果会返回标题、正文内容、标签和状态；note_id 是内部 ID，不要把用户说的数字直接当 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string" },
                "tags": { "type": "array", "items": { "type": "string" } },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "include_done": { "type": "boolean" },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" },
                "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
              },
              "additionalProperties": true
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "搜索客户相关的便签",
            "搜索待办里包含样品的便签",
            "如果用户说删除标题为 1 的便签，先搜索 query=1，再用 note_ref=结果标题调用删除",
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
            .put("query", original.optString("query", JSONObject(argumentsJson.ifBlank { "{}" }).optString("query", "")))
            .put("count", notes.size)
            .put("source_count", original.optInt("count", notes.size))
            .putAssistantNoteReferenceRule()
            .put("results", notes.toAssistantNoteResultsJsonArray())
        return mapped.copy(resultJson = enriched.toString())
    }
}
