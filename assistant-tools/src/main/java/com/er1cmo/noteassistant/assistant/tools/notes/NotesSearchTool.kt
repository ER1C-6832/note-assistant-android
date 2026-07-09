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
import org.json.JSONArray
import org.json.JSONObject

class NotesSearchTool @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : McpTool {
    override val name: String = "notes.search"
    override val description: String =
        "搜索本地便签并返回完整候选。默认搜索 active、archived、deleted。像“王总相关便签”会自动清理为“王总”再搜。不要反复调用 search/list_recent；没结果就用 notes.resolve 或要求用户澄清。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Low
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "query": { "type": "string" },
                "note_ref": { "type": "string", "description": "用户可见标题或关键词，推荐语音入口使用" },
                "note_title": { "type": "string", "description": "用户可见标题" },
                "title": { "type": "string", "description": "用户可见标题，兼容字段" },
                "exact_title": { "type": "string", "description": "需要精确匹配的标题" },
                "tags": { "type": "array", "items": { "type": "string" } },
                "type": { "type": "string", "enum": ["normal", "todo"] },
                "include_done": { "type": "boolean" },
                "include_archived": { "type": "boolean" },
                "include_deleted": { "type": "boolean" },
                "scope": { "type": "string", "enum": ["all", "active", "archived", "deleted"] },
                "limit": { "type": "integer", "minimum": 1, "maximum": 100 }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Low,
        mutates = false,
        confirmation = McpToolDescriptor.CONFIRMATION_NOT_REQUIRED,
        examples = listOf(
            "查找王总相关便签：{\"query\":\"王总相关便签\",\"scope\":\"all\"}",
            "按用户可见标题搜索：{\"exact_title\":\"1\"}",
            "同时搜索最近删除：{\"query\":\"1\",\"scope\":\"all\"}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val parser = ToolArgumentParser.parse(argumentsJson).getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.search 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val raw = parser.raw()
        val query = raw.searchReference()
        val exactTitle = raw.exactTitleReference()
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val scope = parser.optionalString("scope", "all").ifBlank { "all" }.lowercase()
        val includeArchived = raw.optionalBooleanByScope(
            explicitName = "include_archived",
            defaultValue = scope == "all" || scope == "archived",
        )
        val includeDeleted = raw.optionalBooleanByScope(
            explicitName = "include_deleted",
            defaultValue = scope == "all" || scope == "deleted",
        )
        val pool = loadSearchPool(
            scope = scope,
            includeArchived = includeArchived,
            includeDeleted = includeDeleted,
        )
        val filteredPool = pool
            .filterByType(parser.optionalString("type", ""))
            .filterByTags(parser.stringList("tags"))
            .filterDone(parser)

        val searchOutcome = when {
            exactTitle.isNotBlank() -> searchExactTitle(filteredPool, exactTitle, query, limit)
            query.isNotBlank() -> searchQueryWithIntentFallback(filteredPool, query, limit)
            else -> SearchOutcome(
                strategy = "recent_all_in_scope",
                normalizedQuery = "",
                totalMatches = filteredPool.size,
                notes = filteredPool.sortedByRecent().take(limit),
                resultIsLimited = filteredPool.size > limit,
                scores = emptyMap(),
                matchedFields = emptyMap(),
            )
        }

        val result = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("query", query)
        if (exactTitle.isBlank()) {
            result.put("exact_title", JSONObject.NULL)
        } else {
            result.put("exact_title", exactTitle)
        }
        val resultJson = result
            .put("normalized_query", searchOutcome.normalizedQuery)
            .put("scope", scope)
            .put("include_archived", includeArchived)
            .put("include_deleted", includeDeleted)
            .put("match_strategy", searchOutcome.strategy)
            .put("count", searchOutcome.notes.size)
            .put("total_matching_count", searchOutcome.totalMatches)
            .put("result_is_limited", searchOutcome.resultIsLimited)
            .put("results", searchOutcome.notes.toAssistantNoteResultsJsonArrayWithSearchMeta(searchOutcome))
            .put("assistant_next_step_hint", assistantNextStepHint(searchOutcome))
            .toString()
        return McpToolResult.success(
            message = "搜索完成，找到 ${searchOutcome.totalMatches} 条便签，返回 ${searchOutcome.notes.size} 条",
            resultJson = resultJson,
            toolName = name,
            risk = McpRiskLevel.Low,
            affectedNoteIds = searchOutcome.notes.map { it.id },
        )
    }

    private suspend fun loadSearchPool(
        scope: String,
        includeArchived: Boolean,
        includeDeleted: Boolean,
    ): List<Note> {
        return buildList {
            if (scope != "archived" && scope != "deleted") addAll(noteUseCases.listNotes().first())
            if (includeArchived || scope == "archived") addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted || scope == "deleted") addAll(noteUseCases.listDeletedNotes().first())
        }.distinctBy { it.id }
    }

    private fun searchExactTitle(
        notes: List<Note>,
        exactTitle: String,
        fallbackQuery: String,
        limit: Int,
    ): SearchOutcome {
        val normalized = exactTitle.visibleTitleNormalize()
        val exactMatches = notes.filter { it.title.visibleTitleNormalize() == normalized }
            .sortedByRecent()
        if (exactMatches.isNotEmpty()) {
            return SearchOutcome(
                strategy = "exact_title",
                normalizedQuery = exactTitle,
                totalMatches = exactMatches.size,
                notes = exactMatches.take(limit),
                resultIsLimited = exactMatches.size > limit,
                scores = exactMatches.associate { it.id to 1200 },
                matchedFields = exactMatches.associate { it.id to listOf("title_exact") },
            )
        }
        val containsMatches = notes.filter { it.title.visibleTitleNormalize().contains(normalized) }
            .sortedByRecent()
        if (containsMatches.isNotEmpty()) {
            return SearchOutcome(
                strategy = "title_contains_after_exact_miss",
                normalizedQuery = exactTitle,
                totalMatches = containsMatches.size,
                notes = containsMatches.take(limit),
                resultIsLimited = containsMatches.size > limit,
                scores = containsMatches.associate { it.id to 950 },
                matchedFields = containsMatches.associate { it.id to listOf("title_contains") },
            )
        }
        return searchQueryWithIntentFallback(notes, fallbackQuery.ifBlank { exactTitle }, limit)
            .copy(strategy = "full_text_after_exact_title_miss")
    }

    private fun searchQueryWithIntentFallback(notes: List<Note>, query: String, limit: Int): SearchOutcome {
        val terms = query.toAssistantSearchTerms().ifEmpty { listOf(query.cleanVoiceReference()) }
        terms.forEachIndexed { index, term ->
            val results = noteUseCases.searchNotes(notes = notes, query = term, limit = 0)
            if (results.isNotEmpty()) {
                return SearchOutcome(
                    strategy = if (index == 0) "full_text" else "full_text_intent_cleaned",
                    normalizedQuery = term,
                    totalMatches = results.size,
                    notes = results.map { it.note }.distinctBy { it.id }.take(limit),
                    resultIsLimited = results.size > limit,
                    scores = results.associate { it.note.id to it.score },
                    matchedFields = results.associate { result -> result.note.id to result.matchedFields.map { it.name.lowercase() } },
                )
            }
        }
        return SearchOutcome(
            strategy = "no_match",
            normalizedQuery = terms.firstOrNull().orEmpty(),
            totalMatches = 0,
            notes = emptyList(),
            resultIsLimited = false,
            scores = emptyMap(),
            matchedFields = emptyMap(),
        )
    }
}

private data class SearchOutcome(
    val strategy: String,
    val normalizedQuery: String,
    val totalMatches: Int,
    val notes: List<Note>,
    val resultIsLimited: Boolean,
    val scores: Map<Long, Int>,
    val matchedFields: Map<Long, List<String>>,
)

private fun JSONObject.searchReference(): String {
    return listOf(
        optString("query", ""),
        optString("note_ref", ""),
        optString("note_title", ""),
        optString("title", ""),
        optString("exact_title", ""),
    ).firstOrNull { it.isNotBlank() }?.cleanVoiceReference().orEmpty()
}

private fun JSONObject.exactTitleReference(): String {
    return listOf(
        optString("exact_title", ""),
        optString("note_title", ""),
        optString("title", ""),
    ).firstOrNull { it.isNotBlank() }?.cleanVoiceReference().orEmpty()
}

private fun JSONObject.optionalBooleanByScope(explicitName: String, defaultValue: Boolean): Boolean {
    return if (has(explicitName) && !isNull(explicitName)) optBoolean(explicitName, defaultValue) else defaultValue
}

private fun List<Note>.filterByType(type: String): List<Note> {
    return when (type.trim().lowercase()) {
        "normal", "普通" -> filter { it.type.name.equals("Normal", ignoreCase = true) }
        "todo", "待办" -> filter { it.type.name.equals("Todo", ignoreCase = true) }
        else -> this
    }
}

private fun List<Note>.filterByTags(tags: List<String>): List<Note> {
    if (tags.isEmpty()) return this
    return filter { note ->
        tags.all { wanted -> note.tags.any { it.name.equals(wanted.trimStart('#'), ignoreCase = true) } }
    }
}

private fun List<Note>.filterDone(parser: ToolArgumentParser): List<Note> {
    val raw = parser.raw()
    if (!raw.has("include_done")) return this
    val includeDone = parser.boolean("include_done", true)
    return if (includeDone) this else filter { !it.isDone }
}

private fun List<Note>.sortedByRecent(): List<Note> = sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })

private fun List<Note>.toAssistantNoteResultsJsonArrayWithSearchMeta(outcome: SearchOutcome): JSONArray = JSONArray().also { array ->
    forEach { note ->
        val item = note.toAssistantNoteResultJson()
            .put("score", outcome.scores[note.id] ?: JSONObject.NULL)
            .put("matched_fields", JSONArray(outcome.matchedFields[note.id].orEmpty()))
        array.put(item)
    }
}

private fun assistantNextStepHint(outcome: SearchOutcome): String = when {
    outcome.totalMatches == 0 -> "No matching note was found. Do not loop search/list tools. Ask the user for a more specific visible title or keyword."
    outcome.resultIsLimited -> "Results are limited. Ask the user to narrow the title/query before mutation."
    outcome.notes.size == 1 -> "One note matched. For mutation tools, pass note_ref/title from this result or the note_id from this exact result."
    else -> "Multiple notes matched. If the user asked for all related notes, call notes.delete with query and allow_multiple=true so the app can show a confirmation preview. Otherwise ask the user to clarify."
}
