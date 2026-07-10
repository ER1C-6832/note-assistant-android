package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.Phase4ExtendedCommandService
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import javax.inject.Inject

class NotesRestoreTool @Inject constructor(
    private val commandService: Phase4ExtendedCommandService,
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.restore"
    override val description: String =
        "只用于从最近删除恢复便签，不是恢复历史版本。语音入口优先传 note_ref，并只在 deleted 范围解析目标。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "最近删除中用户可见的标题或唯一关键词" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string" },
                "title": { "type": "string", "description": "目标标题兼容字段" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前最近删除结果时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "id_is_internal": { "type": "boolean" },
                "allow_multiple": { "type": "boolean" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf(
            "恢复最近删除里的验收删除恢复：{\"note_ref\":\"验收删除恢复\"}",
            "恢复当前结果 ID：{\"note_id\":12,\"id_is_internal\":true}",
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
                scope = NoteResolveScope.Deleted,
                supportsMultiple = true,
                referenceFields = listOf("note_ref", "note_title", "target_title", "exact_title", "query", "title"),
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
