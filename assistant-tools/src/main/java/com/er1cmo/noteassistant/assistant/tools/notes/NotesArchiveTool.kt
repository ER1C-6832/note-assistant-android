package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class NotesArchiveTool @Inject constructor(
    commandService: NoteCommandService,
    resolver: NoteReferenceResolver,
) : AbstractResolvedNoteCommandTool(
    commandService = commandService,
    resolver = resolver,
    targetScope = NoteResolveScope.ActiveAndArchived,
    supportsMultipleTargets = true,
    referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query", "title"),
) {
    override val name: String = "notes.archive"
    override val description: String =
        "归档或取消归档便签；这不是创建工具。用户说归档/已归档/恢复到普通列表时使用本工具。语音入口优先传 note_ref。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见标题或唯一关键词" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string" },
                "title": { "type": "string", "description": "目标标题兼容字段" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "id_is_internal": { "type": "boolean" },
                "allow_multiple": { "type": "boolean" },
                "archived": { "type": "boolean", "description": "true 归档；false 取消归档" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf(
            "归档验收归档记录：{\"note_ref\":\"验收归档记录\",\"archived\":true}",
            "恢复到普通列表：{\"note_ref\":\"验收归档记录\",\"archived\":false}",
        ),
    )
}
