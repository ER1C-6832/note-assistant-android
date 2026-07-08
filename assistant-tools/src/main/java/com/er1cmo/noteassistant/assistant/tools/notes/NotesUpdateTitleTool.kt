package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesUpdateTitleTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.update_title"
    override val description: String = "修改一条便签的标题。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "title": { "type": "string" }
              },
              "required": ["note_id", "title"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("把刚才那条便签标题改成客户回访", "把 note_id=12 的标题改一下"),
    )
}
