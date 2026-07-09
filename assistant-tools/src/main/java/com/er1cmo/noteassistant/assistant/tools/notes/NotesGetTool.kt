package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONObject

class NotesGetTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.get"
    override val description: String =
        "读取一条便签。note_id 是内部 ID；语音入口可使用 note_ref/note_title/title/query 通过用户可见标题读取。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_id": { "type": "integer", "description": "内部便签 ID，仅当来自工具结果时使用" },
                "note_ref": { "type": "string", "description": "用户可见标题或关键词" },
                "note_title": { "type": "string", "description": "用户可见标题" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段" },
                "query": { "type": "string", "description": "标题关键词，必须唯一匹配" },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" },
                "scope": { "type": "string", "enum": ["all", "active", "archived", "deleted"] }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "读取工具结果里的内部 ID：{\"note_id\":123}",
            "读取标题为 1 的便签：{\"note_ref\":\"1\",\"scope\":\"all\"}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.get 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val noteId = parser.optionalLong("note_id")?.takeIf { it > 0L }
        val ref = raw.userVisibleReference()
        val note = when {
            ref.isNotBlank() -> resolveByVisibleReference(parser, ref, argumentsJson) ?: return ambiguousOrNotFound(ref, parser, argumentsJson)
            noteId != null -> noteUseCases.getNote(noteId) ?: return McpToolResult.failed(
                message = "没有找到便签：$noteId。note_id 是内部 ID；如果用户说的是标题，请改用 note_ref/title/query。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "not_found",
                risk = McpRiskLevel.Low,
            )
            else -> return McpToolResult.failed(
                message = "缺少 note_id 或 note_ref/title/query",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = McpToolResult.ERROR_VALIDATION,
                risk = McpRiskLevel.Low,
            )
        }
        val includeDeleted = raw.optionalBooleanByScope("include_deleted", defaultValue = ref.isNotBlank())
        if (note.deleted && !includeDeleted) {
            return McpToolResult.failed(
                message = "便签《${note.title.ifBlank { "未命名便签" }}》在最近删除中；如需读取请传 include_deleted=true 或 scope=deleted/all。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "already_deleted",
                risk = McpRiskLevel.Low,
            )
        }
        val resultJson = note.toAssistantNoteResultJson()
            .putAssistantNoteReferenceRule()
            .put("resolved_by", if (ref.isNotBlank()) "user_visible_reference" else "internal_note_id")
            .toString()
        return McpToolResult.success(
            message = "已读取便签：${note.title.ifBlank { "未命名便签" }}",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = listOf(note.id),
        )
    }

    private suspend fun resolveByVisibleReference(
        parser: ToolArgumentParser,
        ref: String,
        argumentsJson: String,
    ): Note? {
        val candidates = loadReferencePool(parser)
        val normalized = ref.visibleNormalize()
        val exact = candidates.filter { it.title.visibleNormalize() == normalized }
        if (exact.size == 1) return exact.first()
        if (exact.size > 1) return null
        val contains = candidates.filter { it.title.visibleNormalize().contains(normalized) }
        return if (contains.size == 1) contains.first() else null
    }

    private suspend fun ambiguousOrNotFound(
        ref: String,
        parser: ToolArgumentParser,
        argumentsJson: String,
    ): McpToolResult {
        val candidates = loadReferencePool(parser)
        val normalized = ref.visibleNormalize()
        val matches = candidates.filter { it.title.visibleNormalize() == normalized || it.title.visibleNormalize().contains(normalized) }
            .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
        if (matches.isEmpty()) {
            return McpToolResult.failed(
                message = "没有找到标题匹配“$ref”的便签。已搜索 active/archived/deleted 范围；请确认标题或用 notes.search。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "note_reference_not_found",
                risk = McpRiskLevel.Low,
            )
        }
        val resultJson = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("candidate_count", matches.size)
            .put("results", matches.take(8).toAssistantNoteResultsJsonArray())
            .toString()
        return McpToolResult.failed(
            message = "找到 ${matches.size} 条标题匹配“$ref”的便签，请说得更具体。",
            toolName = name,
            argumentsJson = argumentsJson,
            errorCode = "ambiguous_note_reference",
            risk = McpRiskLevel.Low,
        ).copy(resultJson = resultJson)
    }

    private suspend fun loadReferencePool(parser: ToolArgumentParser): List<Note> {
        val raw = parser.raw()
        val scope = parser.optionalString("scope", "all").ifBlank { "all" }.lowercase()
        val includeArchived = raw.optionalBooleanByScope("include_archived", scope == "all" || scope == "archived")
        val includeDeleted = raw.optionalBooleanByScope("include_deleted", scope == "all" || scope == "deleted")
        return buildList {
            if (scope != "archived" && scope != "deleted") addAll(noteUseCases.listNotes().first())
            if (includeArchived || scope == "archived") addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted || scope == "deleted") addAll(noteUseCases.listDeletedNotes().first())
        }.distinctBy { it.id }
    }
}

private fun JSONObject.userVisibleReference(): String {
    return listOf(
        optString("note_ref", ""),
        optString("note_title", ""),
        optString("title", ""),
        optString("query", ""),
    ).firstOrNull { it.isNotBlank() }?.cleanUserReference().orEmpty()
}

private fun JSONObject.optionalBooleanByScope(explicitName: String, defaultValue: Boolean): Boolean {
    return if (has(explicitName) && !isNull(explicitName)) optBoolean(explicitName, defaultValue) else defaultValue
}

private fun String.visibleNormalize(): String = cleanUserReference().lowercase().replace(Regex("\\s+"), "")

private fun String.cleanUserReference(): String = trim()
    .trim('"')
    .trim('\'')
    .trim()
