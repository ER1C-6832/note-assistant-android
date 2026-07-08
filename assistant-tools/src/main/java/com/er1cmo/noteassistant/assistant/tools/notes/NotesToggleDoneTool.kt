package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesToggleDoneTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.toggle_done"
    override val description: String = "设置待办便签的完成状态。仅适用于 todo 类型便签。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "done": { "type": "boolean" }
              },
              "required": ["note_id"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf("把这个待办标记完成", "把 note_id=9 设为未完成"),
    )
}
