package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import javax.inject.Inject

class NotesClearDoneTool @Inject constructor(
    commandService: Phase4ExtendedCommandService,
) : AbstractPhase4ExtendedCommandTool(commandService) {
    override val name: String = "notes.clear_done"
    override val description: String = "清理已完成待办；支持 archive 或 delete，必须先确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
  "type": "object",
  "properties": {
    "action": { "type": "string", "enum": ["archive", "delete"] },
    "tags": { "type": "array", "items": { "type": "string" } },
    "tag_text": { "type": "string" }
  },
  "additionalProperties": false
}
        """.trimIndent(),
        riskLevel = McpRiskLevel.High,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_REQUIRED,
        examples = listOf("归档所有已完成待办", "删除工作标签下已完成待办"),
    )
}
