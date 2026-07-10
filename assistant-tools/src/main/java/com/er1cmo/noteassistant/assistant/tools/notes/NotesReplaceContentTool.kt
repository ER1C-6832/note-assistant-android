package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesReplaceContentTool @Inject constructor(
    commandService: NoteCommandService,
    resolver: NoteReferenceResolver,
) : AbstractResolvedNoteCommandTool(
    commandService = commandService,
    resolver = resolver,
    targetScope = NoteResolveScope.ActiveAndArchived,
    supportsMultipleTargets = false,
    referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query", "title"),
) {
    override val name: String = "notes.replace_content"
    override val description: String =
        "覆盖唯一目标便签正文。语音入口必须用用户可见标题定位；高风险，确认前绝不修改。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见目标标题或唯一关键词" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string" },
                "title": { "type": "string", "description": "目标标题兼容字段" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "id_is_internal": { "type": "boolean" },
                "content": { "type": "string" }
              },
              "required": ["content"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.High,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_REQUIRED,
        examples = listOf(
            "替换验收手柄记录正文：{\"note_ref\":\"验收手柄记录\",\"content\":\"手柄测试已经重新安排\"}",
        ),
    )
}
