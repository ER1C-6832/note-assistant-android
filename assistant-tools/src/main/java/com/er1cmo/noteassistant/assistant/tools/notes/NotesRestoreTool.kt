package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesRestoreTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.restore"
    override val description: String = "从最近删除中恢复一条或多条便签。超过 5 条会升级为高风险确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "note_ids": { "type": "array", "items": { "type": "integer" } }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf("恢复刚才删除的便签", "恢复 note_id=12"),
    )
}
