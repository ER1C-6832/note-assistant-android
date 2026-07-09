package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import javax.inject.Inject

class NotesResolveTool @Inject constructor(
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.resolve"
    override val description: String =
        "把用户可见的话术解析成便签候选。用于删除、追加、标记完成、打开前定位目标。不要反复调用 list_recent/search；一次 resolve 没结果就要求用户澄清。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "用户说的关键词，例如 王总相关便签" },
                "note_ref": { "type": "string", "description": "用户可见标题或标题关键词" },
                "note_title": { "type": "string", "description": "用户可见标题" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段" },
                "exact_title": { "type": "string", "description": "精确标题" },
                "scope": { "type": "string", "enum": ["all", "active", "archived", "deleted", "active_archived"] },
                "tags": { "type": "array", "items": { "type": "string" } },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "include_done": { "type": "boolean" },
                "limit": { "type": "integer", "minimum": 1, "maximum": 50 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "解析王总相关便签：{\"query\":\"王总相关便签\",\"scope\":\"all\"}",
            "解析标题为 1 的便签：{\"exact_title\":\"1\"}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.resolve 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val query = raw.resolveQuery()
        val exactTitle = raw.resolveExactTitle()
        if (query.isBlank() && exactTitle.isBlank()) {
            return McpToolResult.failed(
                message = "缺少 query、note_ref、note_title、title 或 exact_title",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = McpToolResult.ERROR_VALIDATION,
                risk = McpRiskLevel.Low,
            )
        }
        val request = NoteResolveRequest(
            query = query.ifBlank { exactTitle },
            exactTitle = exactTitle,
            scope = parser.optionalString("scope", "all").toNoteResolveScope(defaultScope = NoteResolveScope.All),
            limit = parser.int("limit", 20).coerceIn(1, 50),
            tags = parser.stringList("tags"),
            type = parser.optionalString("type", ""),
            includeDone = if (raw.has("include_done")) parser.boolean("include_done", true) else null,
        )
        val result = resolver.resolve(request)
        return McpToolResult.success(
            message = "已解析便签候选：匹配 ${result.totalMatches} 条，返回 ${result.matches.size} 条",
            resultJson = result.toJson(kind = "resolve").toString(),
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = result.matches.map { it.id },
        )
    }
}

private fun org.json.JSONObject.resolveQuery(): String {
    return listOf(
        optString("query", ""),
        optString("note_ref", ""),
        optString("note_title", ""),
        optString("title", ""),
        optString("exact_title", ""),
    ).firstOrNull { it.isNotBlank() }?.cleanVoiceReference().orEmpty()
}

private fun org.json.JSONObject.resolveExactTitle(): String {
    return listOf(
        optString("exact_title", ""),
        optString("note_title", ""),
        optString("title", ""),
    ).firstOrNull { it.isNotBlank() }?.cleanVoiceReference().orEmpty()
}
