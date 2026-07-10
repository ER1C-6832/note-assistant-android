package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject
import org.json.JSONObject

class NotesUpdateTitleTool @Inject constructor(
    commandService: NoteCommandService,
    resolver: NoteReferenceResolver,
) : AbstractResolvedNoteCommandTool(
    commandService = commandService,
    resolver = resolver,
    targetScope = NoteResolveScope.ActiveAndArchived,
    supportsMultipleTargets = false,
    referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query"),
) {
    override val name: String = "notes.update_title"
    override val description: String =
        "修改唯一目标便签的标题。note_ref 是旧标题/目标，new_title 是新标题；不要把旧 note_id 用到当前目标。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "要修改的现有便签标题或唯一关键词" },
                "note_title": { "type": "string", "description": "现有便签标题" },
                "target_title": { "type": "string", "description": "现有目标标题" },
                "exact_title": { "type": "string", "description": "现有精确标题" },
                "query": { "type": "string", "description": "定位现有目标的关键词" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "id_is_internal": { "type": "boolean" },
                "new_title": { "type": "string", "description": "修改后的新标题，优先使用" },
                "title": { "type": "string", "description": "修改后的新标题，兼容字段" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "把验收手柄记录改成验收手柄升级：{\"note_ref\":\"验收手柄记录\",\"new_title\":\"验收手柄升级\"}",
        ),
    )

    override fun normalizeResolvedArguments(arguments: JSONObject): JSONObject {
        val newTitle = arguments.optString("new_title", arguments.optString("title", "")).trim()
        require(newTitle.isNotBlank()) { "缺少 new_title" }
        arguments.put("title", newTitle)
        arguments.remove("new_title")
        return arguments
    }
}
