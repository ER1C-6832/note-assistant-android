package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesConvertTypeTool @Inject constructor(
    commandService: NoteCommandService,
) : AbstractNoteCommandTool(commandService) {
    override val name: String = "notes.convert_type"
    override val description: String =
        "在普通便签和待办便签之间转换。用于把普通便签变成待办，或把待办变回普通便签。转换为待办时可同时设置 done。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer" },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "target_type": { "type": "string", "enum": ["normal", "todo"] },
                "done": { "type": "boolean", "description": "仅转换为 todo 时有效，可同时设置完成状态。" }
              },
              "required": ["note_id"],
              "additionalProperties": true
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "把这条普通便签转成待办：{\"note_id\":123,\"target_type\":\"todo\"}",
            "把这条便签转成待办并标记完成：{\"note_id\":123,\"target_type\":\"todo\",\"done\":true}",
        ),
    )
}
