package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.notes.domain.model.Note
import org.json.JSONArray
import org.json.JSONObject

internal val DEFAULT_NOTE_REFERENCE_FIELDS: List<String> = listOf(
    "note_ref",
    "note_title",
    "target_title",
    "exact_title",
    "query",
)

internal sealed interface PreparedNoteArguments {
    data class Ready(
        val argumentsJson: String,
        val notes: List<Note>,
    ) : PreparedNoteArguments

    data class Failed(
        val result: McpToolResult,
    ) : PreparedNoteArguments
}

/**
 * Resolves a user-visible note reference immediately before a tool executes.
 *
 * The visible reference is authoritative. A stale note_id supplied alongside a
 * unique title/query is discarded instead of being allowed to mutate the wrong
 * note. Voice-only numeric ids are rejected unless the caller explicitly marks
 * them as ids obtained from the current tool result.
 */
internal suspend fun prepareResolvedNoteArguments(
    toolName: String,
    argumentsJson: String,
    context: McpToolContext,
    risk: McpRiskLevel,
    scope: NoteResolveScope,
    resolver: NoteReferenceResolver? = null,
    supportsMultiple: Boolean = false,
    referenceFields: List<String> = DEFAULT_NOTE_REFERENCE_FIELDS,
    exactTitleFields: List<String> = listOf("exact_title", "note_title", "target_title"),
    lookup: suspend (NoteResolveRequest) -> NoteResolveResult = { request -> requireNotNull(resolver).resolve(request) },
    poolLoader: suspend (NoteResolveScope) -> List<Note> = { requestedScope -> requireNotNull(resolver).loadPool(requestedScope) },
): PreparedNoteArguments {
    val args = runCatching { JSONObject(argumentsJson.trim().ifBlank { "{}" }) }.getOrElse { error ->
        return PreparedNoteArguments.Failed(
            McpToolResult.invalidJson(
                toolName = toolName,
                argumentsJson = argumentsJson,
                message = "$toolName 参数不是有效 JSON：${error.message ?: "解析失败"}",
            ),
        )
    }

    val visibleReference = referenceFields
        .asSequence()
        .map { field -> args.optString(field, "").cleanVoiceReference() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val exactTitle = exactTitleFields
        .asSequence()
        .map { field -> args.optString(field, "").cleanVoiceReference() }
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
    val requestedIds = args.noteIdsForResolution()

    if (visibleReference.isNotBlank()) {
        val maxMatches = args.optInt("max_matches", if (supportsMultiple) 20 else 5).coerceIn(1, 20)
        val resolution = lookup(
            NoteResolveRequest(
                query = visibleReference,
                exactTitle = exactTitle,
                scope = scope,
                limit = maxMatches,
            ),
        )

        if (resolution.totalMatches == 0 || resolution.matches.isEmpty()) {
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = "没有找到与“$visibleReference”匹配的目标便签。已停止执行，不会改动其他便签。",
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    errorCode = "note_reference_not_found",
                    risk = risk,
                ).copy(resultJson = resolution.toJson(kind = "target_resolution_no_match").toString()),
            )
        }
        if (resolution.resultIsLimited) {
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = "匹配“$visibleReference”的便签超过 ${resolution.matches.size} 条，请缩小标题或关键词后重试。",
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    errorCode = "too_many_note_matches",
                    risk = risk,
                ).copy(resultJson = resolution.toJson(kind = "target_resolution_limited").toString()),
            )
        }

        val allowMultiple = args.optBoolean("allow_multiple", false) || visibleReference.hasMultipleIntent()
        if (resolution.totalMatches > 1 && (!supportsMultiple || !allowMultiple)) {
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = "找到 ${resolution.totalMatches} 条匹配“$visibleReference”的便签，请说出更具体标题后再执行。",
                    toolName = toolName,
                    argumentsJson = argumentsJson,
                    errorCode = "ambiguous_note_reference",
                    risk = risk,
                ).copy(resultJson = resolution.toJson(kind = "target_resolution_ambiguous").toString()),
            )
        }

        val selected = if (supportsMultiple && allowMultiple) {
            resolution.matches
        } else {
            listOf(resolution.matches.first())
        }
        val rewritten = JSONObject(args.toString())
        rewritten.remove("note_id")
        rewritten.remove("note_ids")
        referenceFields.forEach { field -> rewritten.remove(field) }
        rewritten.remove("allow_multiple")
        rewritten.remove("max_matches")
        rewritten.remove("force_note_id")
        rewritten.remove("id_is_internal")
        if (selected.size == 1) {
            rewritten.put("note_id", selected.first().id)
        } else {
            rewritten.put("note_ids", JSONArray(selected.map { it.id }))
        }
        rewritten.put("resolved_note_ref", visibleReference)
        rewritten.put("resolved_by", resolution.strategy)
        rewritten.put("resolved_target_titles", JSONArray(selected.map { it.title.ifBlank { "未命名便签" } }))
        if (requestedIds.isNotEmpty() && requestedIds.toSet() != selected.map { it.id }.toSet()) {
            rewritten.put("discarded_conflicting_note_ids", JSONArray(requestedIds))
        }
        return PreparedNoteArguments.Ready(
            argumentsJson = rewritten.toString(),
            notes = selected,
        )
    }

    if (requestedIds.isEmpty()) {
        return PreparedNoteArguments.Failed(
            McpToolResult.failed(
                message = "缺少目标便签。请提供 note_ref、note_title、exact_title、query，或明确标记为内部 ID 的 note_id。",
                toolName = toolName,
                argumentsJson = argumentsJson,
                errorCode = "missing_note_reference",
                risk = risk,
            ),
        )
    }

    val internalIdExplicit = args.optBoolean("id_is_internal", false) || args.optBoolean("force_note_id", false)
    if (context.source == McpToolContext.SOURCE_VOICE && !internalIdExplicit) {
        return PreparedNoteArguments.Failed(
            McpToolResult.failed(
                message = "语音入口收到未验证的 note_id，已安全停止。请改用 note_ref/title，或仅在该 ID 来自当前工具结果时传 id_is_internal=true。",
                toolName = toolName,
                argumentsJson = argumentsJson,
                errorCode = "unsafe_voice_note_id",
                risk = risk,
            ),
        )
    }

    val pool = poolLoader(scope)
    val poolById = pool.associateBy { it.id }
    val notes = requestedIds.mapNotNull(poolById::get)
    if (notes.size != requestedIds.distinct().size) {
        return PreparedNoteArguments.Failed(
            McpToolResult.failed(
                message = "指定的便签 ID 不存在或不在当前工具允许的范围内，已停止执行。",
                toolName = toolName,
                argumentsJson = argumentsJson,
                errorCode = "note_target_out_of_scope",
                risk = risk,
            ),
        )
    }
    if (!supportsMultiple && notes.size != 1) {
        return PreparedNoteArguments.Failed(
            McpToolResult.failed(
                message = "该工具一次只能操作一条便签，请指定唯一目标。",
                toolName = toolName,
                argumentsJson = argumentsJson,
                errorCode = "multiple_targets_not_supported",
                risk = risk,
            ),
        )
    }

    val rewritten = JSONObject(args.toString())
    rewritten.remove("force_note_id")
    rewritten.remove("id_is_internal")
    return PreparedNoteArguments.Ready(
        argumentsJson = rewritten.toString(),
        notes = notes,
    )
}

internal fun McpToolResult.withResolvedNoteTargets(notes: List<Note>): McpToolResult {
    if (notes.isEmpty()) return this
    val targetText = notes.take(3).joinToString("、") { "《${it.title.ifBlank { "未命名便签" }}》" }
        .let { summary -> if (notes.size > 3) "$summary 等 ${notes.size} 条" else summary }
    val nextMessage = if (notes.any { note -> note.title.isNotBlank() && message.contains(note.title) }) {
        message
    } else {
        "$message：$targetText"
    }
    val mergedResult = runCatching {
        val base = resultJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        base.put(
            "resolved_targets",
            JSONArray().also { array ->
                notes.forEach { note ->
                    array.put(
                        JSONObject()
                            .put("note_id", note.id)
                            .put("note_ref", note.title.ifBlank { "未命名便签" }),
                    )
                }
            },
        )
        base.toString()
    }.getOrElse {
        JSONObject()
            .put("original_result", resultJson ?: "")
            .put("resolved_note_ids", JSONArray(notes.map { it.id }))
            .toString()
    }
    return copy(
        message = nextMessage,
        resultJson = mergedResult,
        affectedNoteIds = if (affectedNoteIds.isEmpty()) notes.map { it.id } else affectedNoteIds,
    )
}

private fun JSONObject.noteIdsForResolution(): List<Long> {
    val ids = mutableListOf<Long>()
    optJSONArray("note_ids")?.let { array ->
        for (index in 0 until array.length()) {
            array.optLong(index, 0L).takeIf { it > 0L }?.let(ids::add)
        }
    }
    optLong("note_id", 0L).takeIf { it > 0L }?.let(ids::add)
    return ids.distinct()
}

private fun String.hasMultipleIntent(): Boolean = listOf(
    "全部",
    "所有",
    "这些",
    "相关",
    "一批",
).any(::contains)
