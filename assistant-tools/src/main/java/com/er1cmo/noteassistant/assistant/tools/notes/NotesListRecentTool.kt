package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesListRecentTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.list_recent"
    override val description: String =
        "列出最近更新的便签，只用于“刚才那条/最近那条”。不要用它反复查关键词；查找王总相关、标题、内容时直接用 notes.resolve 或 notes.search。"
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
            "打开刚才那条前先列出最近便签",
            "查找关键词时不要用 list_recent；用 notes.resolve {\"query\":\"王总相关便签\"}",
        ),
    )
}
