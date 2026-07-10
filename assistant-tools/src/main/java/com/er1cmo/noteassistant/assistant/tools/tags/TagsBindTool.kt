package com.er1cmo.noteassistant.assistant.tools.tags

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.assistant.tools.notes.NoteReferenceResolver
import com.er1cmo.noteassistant.assistant.tools.notes.NoteResolveScope
import com.er1cmo.noteassistant.assistant.tools.notes.PreparedNoteArguments
import com.er1cmo.noteassistant.assistant.tools.notes.prepareResolvedNoteArguments
import com.er1cmo.noteassistant.assistant.tools.notes.withResolvedNoteTargets
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import javax.inject.Inject

class TagsBindTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "tags.bind"
    override val description: String =
        "给明确目标便签添加、移除或替换标签。note_ref 定位便签，tags/tag_text 指定标签；不要复用旧 note_id。replace 或批量操作会确认。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "目标便签的用户可见标题或唯一关键词" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "id_is_internal": { "type": "boolean" },
                "allow_multiple": { "type": "boolean" },
                "tags": { "type": "array", "items": { "type": "string" } },
                "tag_text": { "type": "string" },
                "mode": { "type": "string", "enum": ["add", "remove", "replace"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf(
            "给验收手柄记录加标签：{\"note_ref\":\"验收手柄记录\",\"tags\":[\"验收客户\"],\"mode\":\"add\"}",
            "从验收手柄记录移除标签：{\"note_ref\":\"验收手柄记录\",\"tags\":[\"验收客户\"],\"mode\":\"remove\"}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        return when (
            val prepared = prepareResolvedNoteArguments(
                toolName = name,
                argumentsJson = argumentsJson,
                context = context,
                risk = riskLevel,
                resolver = resolver,
                scope = NoteResolveScope.ActiveAndArchived,
                supportsMultiple = true,
                referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query"),
            )
        ) {
            is PreparedNoteArguments.Failed -> prepared.result
            is PreparedNoteArguments.Ready -> commandService.execute(
                toolName = name,
                argumentsJson = prepared.argumentsJson,
                source = context.toCommandSource(),
            ).toMcpToolResult(
                toolName = name,
                argumentsJson = prepared.argumentsJson,
            ).withResolvedNoteTargets(prepared.notes)
        }
    }
}
