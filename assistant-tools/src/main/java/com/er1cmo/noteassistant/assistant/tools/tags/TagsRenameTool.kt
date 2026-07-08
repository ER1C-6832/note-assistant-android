package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class TagsRenameTool @Inject constructor(
    commandService: NoteCommandService,
) : com.er1cmo.noteassistant.assistant.tools.notes.AbstractNoteCommandTool(commandService) {
    override val name: String = "tags.rename"
    override val description: String = "重命名一个标签；如该标签已关联便签，需要确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "tag_id": { "type": "integer" },
                "name": { "type": "string" }
              },
              "required": ["tag_id", "name"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.High,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf("把客户标签改名为客户跟进", "重命名 tag_id=3"),
    )
}
