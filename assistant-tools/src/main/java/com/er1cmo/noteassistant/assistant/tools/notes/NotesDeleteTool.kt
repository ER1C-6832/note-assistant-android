package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class NotesDeleteTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.delete"
    override val description: String = "软删除一个或多个便签。高风险，必须先返回确认请求。语音场景应优先使用 note_ref/note_title 等用户可见标题，不要把用户说的数字直接当内部 note_id。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见的便签标题或标题关键词，推荐语音入口使用" },
                "note_title": { "type": "string", "description": "用户可见的便签标题" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段" },
                "query": { "type": "string", "description": "标题关键词，必须唯一匹配" },
                "note_id": { "type": "integer", "description": "内部便签 ID，仅当用户明确看到并提供 ID 时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "force_note_id": { "type": "boolean", "description": "为 true 时强制把 note_id 当内部 ID" },
                "id_is_internal": { "type": "boolean", "description": "为 true 时强制把 note_id 当内部 ID" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.High,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_REQUIRED,
        examples = listOf(
            "删除标题为 1 的便签：{\"note_ref\":\"1\"}",
            "删除刚才搜索结果里标题为 客户 的便签：{\"note_title\":\"客户\"}",
            "只有系统明确知道内部 ID 时才使用：{\"note_id\":123,\"force_note_id\":true}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val prepared = prepareArguments(argumentsJson = argumentsJson, context = context)
        return when (prepared) {
            is PreparedDeleteArguments.Failed -> prepared.result
            is PreparedDeleteArguments.Ready -> {
                val commandResult = commandService.execute(
                    toolName = name,
                    argumentsJson = prepared.argumentsJson,
                    source = context.toCommandSource(),
                )
                commandResult.toMcpToolResult(toolName = name, argumentsJson = prepared.argumentsJson)
            }
        }
    }

    private suspend fun prepareArguments(
        argumentsJson: String,
        context: McpToolContext,
    ): PreparedDeleteArguments {
        val args = runCatching { JSONObject(argumentsJson.trim().ifBlank { "{}" }) }.getOrElse { error ->
            return PreparedDeleteArguments.Failed(
                McpToolResult.invalidJson(
                    toolName = name,
                    argumentsJson = argumentsJson,
                    message = "notes.delete 参数不是有效 JSON：${error.message ?: "解析失败"}",
                ),
            )
        }

        val explicitRef = args.userVisibleReference()
        if (explicitRef.isNotBlank()) {
            return resolveByUserVisibleReference(args, explicitRef, argumentsJson)
        }

        if (shouldTreatVoiceNumericIdAsTitle(args, context)) {
            val numericRef = args.optLong("note_id", 0L).toString()
            val titleMatches = visibleDeleteCandidates().filter { it.title.trim() == numericRef }
            if (titleMatches.size == 1) {
                return PreparedDeleteArguments.Ready(rewriteForResolvedNotes(args, titleMatches, numericRef, "voice_numeric_title"))
            }
            if (titleMatches.size > 1) {
                return PreparedDeleteArguments.Failed(ambiguousReferenceResult(numericRef, titleMatches))
            }
        }

        return PreparedDeleteArguments.Ready(args.toString())
    }

    private suspend fun resolveByUserVisibleReference(
        args: JSONObject,
        ref: String,
        originalArgumentsJson: String,
    ): PreparedDeleteArguments {
        val candidates = visibleDeleteCandidates()
        val exactMatches = candidates.filter { it.title.equals(ref, ignoreCase = true) }
        if (exactMatches.size == 1) {
            return PreparedDeleteArguments.Ready(rewriteForResolvedNotes(args, exactMatches, ref, "title_exact"))
        }
        if (exactMatches.size > 1) {
            return PreparedDeleteArguments.Failed(ambiguousReferenceResult(ref, exactMatches))
        }

        val titleContainsMatches = candidates.filter { it.title.contains(ref, ignoreCase = true) }
        if (titleContainsMatches.size == 1) {
            return PreparedDeleteArguments.Ready(rewriteForResolvedNotes(args, titleContainsMatches, ref, "title_contains"))
        }
        if (titleContainsMatches.size > 1) {
            return PreparedDeleteArguments.Failed(ambiguousReferenceResult(ref, titleContainsMatches))
        }

        return PreparedDeleteArguments.Failed(
            McpToolResult.failed(
                message = "没有找到标题匹配“$ref”的便签。请先搜索或说出更完整的标题。",
                toolName = name,
                argumentsJson = originalArgumentsJson,
                errorCode = "note_reference_not_found",
                risk = McpRiskLevel.High,
            ),
        )
    }

    private suspend fun visibleDeleteCandidates(): List<Note> {
        return buildList {
            addAll(noteUseCases.listNotes().first())
            addAll(noteUseCases.listArchivedNotes().first())
        }.filter { !it.deleted }
            .distinctBy { it.id }
    }

    private fun shouldTreatVoiceNumericIdAsTitle(args: JSONObject, context: McpToolContext): Boolean {
        if (context.source != McpToolContext.SOURCE_VOICE) return false
        if (!args.has("note_id") || args.optLong("note_id", 0L) <= 0L) return false
        if (args.optBoolean("force_note_id", false) || args.optBoolean("id_is_internal", false)) return false
        if (args.has("note_ids")) return false
        return true
    }

    private fun rewriteForResolvedNotes(
        args: JSONObject,
        notes: List<Note>,
        ref: String,
        resolvedBy: String,
    ): String {
        val rewritten = JSONObject(args.toString())
        rewritten.remove("note_id")
        rewritten.remove("note_ids")
        rewritten.put("note_ids", JSONArray(notes.map { it.id }))
        rewritten.put("resolved_note_ref", ref)
        rewritten.put("resolved_by", resolvedBy)
        rewritten.put(
            "resolved_notes",
            JSONArray().also { array ->
                notes.forEach { note ->
                    array.put(
                        JSONObject()
                            .put("note_id", note.id)
                            .put("title", note.title),
                    )
                }
            },
        )
        return rewritten.toString()
    }

    private fun ambiguousReferenceResult(ref: String, matches: List<Note>): McpToolResult {
        val preview = matches.take(5).joinToString(separator = "；") { note -> "${note.id}:${note.title.ifBlank { "未命名" }}" }
        return McpToolResult.failed(
            message = "找到 ${matches.size} 条标题匹配“$ref”的便签，请说得更具体。候选：$preview",
            toolName = name,
            argumentsJson = JSONObject()
                .put("note_ref", ref)
                .put("candidate_count", matches.size)
                .toString(),
            errorCode = "ambiguous_note_reference",
            risk = McpRiskLevel.High,
        )
    }
}

private sealed interface PreparedDeleteArguments {
    data class Ready(val argumentsJson: String) : PreparedDeleteArguments
    data class Failed(val result: McpToolResult) : PreparedDeleteArguments
}

private fun JSONObject.userVisibleReference(): String {
    return listOf(
        optString("note_ref", ""),
        optString("note_title", ""),
        optString("title", ""),
        optString("query", ""),
    ).firstOrNull { it.isNotBlank() }
        ?.cleanUserReference()
        .orEmpty()
}

private fun String.cleanUserReference(): String = trim()
    .trim('"')
    .trim('\'')
    .trim()
