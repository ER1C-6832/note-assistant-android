package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import javax.inject.Inject

class NotesRestoreTool @Inject constructor(
    commandService: Phase4ExtendedCommandService,
) : AbstractPhase4ExtendedCommandTool(commandService) {
    override val name: String = "notes.restore"
    override val description: String = "从最近删除中恢复一个或多个便签。"
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
        examples = listOf("恢复最近删除里的那条便签"),
    )
}
