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
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.delete"
    override val description: String =
        "软删除便签。支持 query/note_ref/title 这类用户可见引用，会在工具内解析候选并返回 App 确认，不要反复先 search/list。用户说“王总相关便签”时用 query=王总相关便签、allow_multiple=true。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.High
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string", "description": "用户可见关键词或相关内容，例如 王总相关便签。会搜索标题、正文、标签。" },
                "note_ref": { "type": "string", "description": "用户可见标题或标题关键词" },
                "note_title": { "type": "string", "description": "用户可见标题" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段" },
                "exact_title": { "type": "string", "description": "精确标题" },
                "note_id": { "type": "integer", "description": "内部便签 ID，仅当来自工具返回结果时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "allow_multiple": { "type": "boolean", "description": "用户明确说相关/全部/这些时设为 true，匹配多条会进入确认预览" },
                "scope": { "type": "string", "enum": ["active", "archived", "active_archived"] },
                "max_matches": { "type": "integer", "minimum": 1, "maximum": 20 },
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
            "删除王总相关便签：{\"query\":\"王总相关便签\",\"allow_multiple\":true}",
            "删除标题为 1 的便签：{\"exact_title\":\"1\"}",
            "删除刚才工具结果里的具体 note_id：{\"note_id\":123,\"force_note_id\":true}",
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

        val explicitReference = args.userVisibleReference()
        val hasUserVisibleReference = explicitReference.isNotBlank()
        if (hasUserVisibleReference) {
            return resolveByReference(args, explicitReference, argumentsJson)
        }

        if (shouldTreatVoiceNumericIdAsTitle(args, context)) {
            val numericRef = args.optLong("note_id", 0L).toString()
            val resolved = resolver.resolve(
                NoteResolveRequest(
                    query = numericRef,
                    exactTitle = numericRef,
                    scope = NoteResolveScope.ActiveAndArchived,
                    limit = args.optInt("max_matches", 20).coerceIn(1, 20),
                ),
            )
            if (resolved.matches.isNotEmpty()) return rewriteResolvedOrFail(args, resolved, numericRef, argumentsJson)
        }

        val noteIds = args.noteIds()
        if (noteIds.isNotEmpty()) return PreparedDeleteArguments.Ready(args.toString())

        return PreparedDeleteArguments.Failed(
            McpToolResult.failed(
                message = "缺少可删除目标。请提供 query、note_ref、title、exact_title，或来自工具结果的 note_id。",
                toolName = name,
                argumentsJson = argumentsJson,
                errorCode = "missing_note_reference",
                risk = McpRiskLevel.High,
            ),
        )
    }

    private suspend fun resolveByReference(
        args: JSONObject,
        reference: String,
        originalArgumentsJson: String,
    ): PreparedDeleteArguments {
        val scope = args.optString("scope", "active_archived").toNoteResolveScope(defaultScope = NoteResolveScope.ActiveAndArchived)
        val boundedScope = when (scope) {
            NoteResolveScope.Deleted -> NoteResolveScope.ActiveAndArchived
            NoteResolveScope.All -> NoteResolveScope.ActiveAndArchived
            else -> scope
        }
        val exactTitle = args.exactTitleReference()
        val result = resolver.resolve(
            NoteResolveRequest(
                query = reference,
                exactTitle = exactTitle,
                scope = boundedScope,
                limit = args.optInt("max_matches", 20).coerceIn(1, 20),
            ),
        )
        if (result.matches.isNotEmpty()) return rewriteResolvedOrFail(args, result, reference, originalArgumentsJson)

        val deletedResult = resolver.resolve(
            NoteResolveRequest(
                query = reference,
                exactTitle = exactTitle,
                scope = NoteResolveScope.Deleted,
                limit = 5,
            ),
        )
        if (deletedResult.matches.isNotEmpty()) {
            return PreparedDeleteArguments.Failed(
                McpToolResult.failed(
                    message = "找到 ${deletedResult.totalMatches} 条匹配“$reference”的便签，但它们已经在最近删除中，不需要重复删除。",
                    toolName = name,
                    argumentsJson = originalArgumentsJson,
                    errorCode = "already_deleted",
                    risk = McpRiskLevel.High,
                ).copy(resultJson = deletedResult.toJson(kind = "already_deleted_matches").toString()),
            )
        }

        return PreparedDeleteArguments.Failed(
            McpToolResult.failed(
                message = "没有找到可删除的“$reference”。不要反复 search/list，请让用户换一个更具体标题或关键词。",
                toolName = name,
                argumentsJson = originalArgumentsJson,
                errorCode = "note_reference_not_found",
                risk = McpRiskLevel.High,
            ).copy(resultJson = result.toJson(kind = "delete_resolution_no_match").toString()),
        )
    }

    private fun rewriteResolvedOrFail(
        args: JSONObject,
        result: NoteResolveResult,
        ref: String,
        originalArgumentsJson: String,
    ): PreparedDeleteArguments {
        val allowMultiple = args.allowMultipleByIntent(ref)
        if (result.resultIsLimited) {
            return PreparedDeleteArguments.Failed(
                McpToolResult.failed(
                    message = "匹配“$ref”的便签超过 ${result.matches.size} 条，请先缩小关键词后再删除。",
                    toolName = name,
                    argumentsJson = originalArgumentsJson,
                    errorCode = "too_many_note_matches",
                    risk = McpRiskLevel.High,
                ).copy(resultJson = result.toJson(kind = "delete_resolution_limited").toString()),
            )
        }
        if (result.matches.size > 1 && !allowMultiple) {
            return PreparedDeleteArguments.Failed(
                ambiguousReferenceResult(ref, result),
            )
        }
        return PreparedDeleteArguments.Ready(rewriteForResolvedNotes(args, result.matches, ref, result.strategy))
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
        rewritten.remove("query")
        rewritten.remove("note_ref")
        rewritten.remove("note_title")
        rewritten.remove("title")
        rewritten.remove("exact_title")
        rewritten.remove("force_note_id")
        rewritten.remove("id_is_internal")
        rewritten.put("note_ids", JSONArray(notes.map { it.id }))
        rewritten.put("resolved_note_ref", ref)
        rewritten.put("resolved_by", resolvedBy)
        rewritten.put("resolved_notes", notes.toAssistantNoteResultsJsonArray())
        return rewritten.toString()
    }

    private fun ambiguousReferenceResult(ref: String, result: NoteResolveResult): McpToolResult {
        return McpToolResult.failed(
            message = "找到 ${result.totalMatches} 条匹配“$ref”的便签。若要全部删除，请明确说“删除所有相关便签”；否则请说更具体标题。",
            toolName = name,
            argumentsJson = JSONObject()
                .put("query", ref)
                .put("candidate_count", result.totalMatches)
                .toString(),
            errorCode = "ambiguous_note_reference",
            risk = McpRiskLevel.High,
        ).copy(resultJson = result.toJson(kind = "delete_resolution_ambiguous").toString())
    }
}

private sealed interface PreparedDeleteArguments {
    data class Ready(val argumentsJson: String) : PreparedDeleteArguments
    data class Failed(val result: McpToolResult) : PreparedDeleteArguments
}

private fun JSONObject.userVisibleReference(): String {
    return listOf(
        optString("query", ""),
        optString("note_ref", ""),
        optString("note_title", ""),
        optString("title", ""),
        optString("exact_title", ""),
    ).firstOrNull { it.isNotBlank() }
        ?.cleanVoiceReference()
        .orEmpty()
}

private fun JSONObject.exactTitleReference(): String {
    return listOf(
        optString("exact_title", ""),
        optString("note_title", ""),
        optString("title", ""),
    ).firstOrNull { it.isNotBlank() }
        ?.cleanVoiceReference()
        .orEmpty()
}

private fun JSONObject.noteIds(): List<Long> {
    val array = optJSONArray("note_ids")
    if (array != null) {
        return buildList {
            for (index in 0 until array.length()) {
                val id = array.optLong(index, 0L)
                if (id > 0L) add(id)
            }
        }.distinct()
    }
    val id = optLong("note_id", 0L)
    return if (id > 0L) listOf(id) else emptyList()
}

private fun JSONObject.allowMultipleByIntent(ref: String): Boolean {
    if (has("allow_multiple")) return optBoolean("allow_multiple", false)
    val mode = optString("match_mode", "").lowercase()
    if (mode == "related" || mode == "all") return true
    return listOf("相关", "有关", "全部", "所有", "这些", "一批").any { ref.contains(it) }
}
