package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesAppendTool @Inject constructor(
    commandService: NoteCommandService,
    resolver: NoteReferenceResolver,
) : AbstractResolvedNoteCommandTool(
    commandService = commandService,
    resolver = resolver,
    targetScope = NoteResolveScope.ActiveAndArchived,
    supportsMultipleTargets = false,
    referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query", "title"),
) {
    override val name: String = "notes.append"
    override val description: String =
        "向唯一目标便签追加内容，不覆盖原正文。语音入口优先传 note_ref/note_title；不要复用旧 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见标题或唯一标题关键词，语音入口优先使用" },
                "note_title": { "type": "string", "description": "用户可见标题" },
                "target_title": { "type": "string", "description": "目标便签标题" },
                "exact_title": { "type": "string", "description": "精确目标标题" },
                "query": { "type": "string", "description": "用于唯一定位目标的关键词" },
                "title": { "type": "string", "description": "目标标题兼容字段；不是新标题" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "id_is_internal": { "type": "boolean", "description": "note_id 确实来自当前工具结果时设为 true" },
                "content": { "type": "string" },
                "separator": { "type": "string", "enum": ["newline", "space"] }
              },
              "required": ["content"],
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "给验收客户回访补充一句：{\"note_ref\":\"验收客户回访\",\"content\":\"快递单号待同步\"}",
            "仅当 ID 来自当前结果：{\"note_id\":12,\"id_is_internal\":true,\"content\":\"补充内容\"}",
        ),
    )
}
