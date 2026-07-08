package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesPinTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.pin"
    override val description: String = "置顶或取消置顶一条或多条便签。批量修改可能由 Phase2 风险策略要求确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "pinned": { "type": "boolean" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf("把这条便签置顶", "取消置顶 note_id=8"),
    )
}
