package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
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
    private val uiCommandBus: UiCommandBus,
) : McpTool {
    override val name: String = "notes.search"
    override val description: String =
        "搜索本地便签并同步显示搜索结果。语音入口说“搜索客户/屏幕”时直接调用本工具即可，不需要再额外调用 ui.show_search。"
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
            "用户说搜索客户：{\"query\":\"客户\"}，工具会自动显示搜索结果",
            "按用户可见标题搜索：{\"exact_title\":\"1\"}",
            "用户说全部便签有哪些：{\"query\":\"全部便签有哪些\",\"scope\":\"active\"}",
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
        val broadListIntent = exactTitle.isBlank() && query.isBroadNoteListRequest()
        val limit = parser.int("limit", 20).coerceIn(1, 100)
        val explicitScope = parser.optionalString("scope", "")
        val scope = explicitScope.ifBlank { if (broadListIntent) "active" else "all" }.lowercase()
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
        val typedPool = pool.filterByType(parser.optionalString("type", ""))
        val taggedPool = typedPool.filterByTags(parser.stringList("tags"))
        val filteredPool = taggedPool.filterDone(parser)

        val searchOutcome = when {
            broadListIntent -> broadList(filteredPool, limit)
            exactTitle.isNotBlank() -> searchExactTitle(filteredPool, exactTitle, query, limit)
            query.isNotBlank() -> searchQueryWithIntentFallback(filteredPool, query, limit)
            else -> SearchOutcome(
                strategy = "recent_all_in_scope",
                totalMatches = filteredPool.size,
                notes = filteredPool.sortedByRecent().take(limit),
                scores = emptyMap(),
                matchedFields = emptyMap(),
            )
        }

        emitSearchUiCommand(
            scope = scope,
            broadListIntent = broadListIntent,
            query = exactTitle.ifBlank { query },
        )

        val result = JSONObject()
            .putAssistantNoteReferenceRule()
            .put("query", query)
        if (exactTitle.isBlank()) {
            result.put("exact_title", JSONObject.NULL)
        } else {
            result.put("exact_title", exactTitle)
        }
        val resultJson = result
            .put("scope", scope)
            .put("include_archived", includeArchived)
            .put("include_deleted", includeDeleted)
            .put("broad_list_intent", broadListIntent)
            .put("match_strategy", searchOutcome.strategy)
            .put("count", searchOutcome.notes.size)
            .put("total_matching_count", searchOutcome.totalMatches)
            .put("result_is_limited", searchOutcome.totalMatches > searchOutcome.notes.size)
            .put("results", searchOutcome.notes.toAssistantNoteResultsJsonArrayWithSearchMeta(searchOutcome))
            .put(
                "assistant_next_step_hint",
                nextStepHint(searchOutcome, broadListIntent),
            )
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

    private fun broadList(notes: List<Note>, limit: Int): SearchOutcome {
        val sorted = notes.sortedByRecent()
        return SearchOutcome(
            strategy = "broad_list_intent",
            totalMatches = sorted.size,
            notes = sorted.take(limit),
            scores = sorted.associate { it.id to 100 },
            matchedFields = sorted.associate { it.id to listOf("list_scope") },
        )
    }

    private fun searchExactTitle(
        notes: List<Note>,
        exactTitle: String,
        fallbackQuery: String,
        limit: Int,
    ): SearchOutcome {
        val normalized = exactTitle.visibleNormalize()
        val exactMatches = notes.filter { it.title.visibleNormalize() == normalized }
            .sortedByRecent()
        if (exactMatches.isNotEmpty()) {
            return SearchOutcome(
                strategy = "exact_title",
                totalMatches = exactMatches.size,
                notes = exactMatches.take(limit),
                scores = exactMatches.associate { it.id to 1200 },
                matchedFields = exactMatches.associate { it.id to listOf("title_exact") },
            )
        }
        val containsMatches = notes.filter { it.title.visibleNormalize().contains(normalized) }
            .sortedByRecent()
        if (containsMatches.isNotEmpty()) {
            return SearchOutcome(
                strategy = "title_contains_after_exact_miss",
                totalMatches = containsMatches.size,
                notes = containsMatches.take(limit),
                scores = containsMatches.associate { it.id to 950 },
                matchedFields = containsMatches.associate { it.id to listOf("title_contains") },
            )
        }
        return searchQueryWithIntentFallback(notes, fallbackQuery.ifBlank { exactTitle }, limit)
            .copy(strategy = "full_text_after_exact_title_miss")
    }

    private fun searchQueryWithIntentFallback(notes: List<Note>, query: String, limit: Int): SearchOutcome {
        val terms = query.toAssistantSearchTerms()
        terms.forEachIndexed { index, term ->
            val outcome = searchQuery(notes, term, limit)
            if (outcome.totalMatches > 0) {
                return if (index == 0) outcome else outcome.copy(strategy = "full_text_intent_cleaned")
            }
        }
        return SearchOutcome(
            strategy = "no_match_stop",
            totalMatches = 0,
            notes = emptyList(),
            scores = emptyMap(),
            matchedFields = emptyMap(),
        )
    }

    private fun searchQuery(notes: List<Note>, query: String, limit: Int): SearchOutcome {
        val results = noteUseCases.searchNotes(notes = notes, query = query, limit = 0)
        return SearchOutcome(
            strategy = "full_text",
            totalMatches = results.size,
            notes = results.map { it.note }.take(limit),
            scores = results.associate { it.note.id to it.score },
            matchedFields = results.associate { result -> result.note.id to result.matchedFields.map { it.name.lowercase() } },
        )
    }

    private fun emitSearchUiCommand(
        scope: String,
        broadListIntent: Boolean,
        query: String,
    ) {
        when {
            scope == "archived" -> uiCommandBus.emit(UiCommand.ShowArchive)
            scope == "deleted" -> uiCommandBus.emit(UiCommand.ShowTrash)
            broadListIntent -> uiCommandBus.emit(UiCommand.ShowNoteList)
            query.isNotBlank() -> uiCommandBus.emit(UiCommand.ShowSearch(query.cleanVoiceReference()))
        }
    }

    private fun nextStepHint(searchOutcome: SearchOutcome, broadListIntent: Boolean): String = when {
        broadListIntent -> "The user asked to list notes. Results were returned and the note list UI was updated. Do not call more search/list tools."
        searchOutcome.totalMatches == 0 -> "No matching note was found. Stop calling search/list tools and ask the user for a clearer keyword or title."
        else -> "Search results were returned and the UI search state was updated. Use note_ref/title from these results for follow-up actions; do not call ui.show_search again."
    }
}

private data class SearchOutcome(
    val strategy: String,
    val totalMatches: Int,
    val notes: List<Note>,
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
        optString("note_ref", ""),
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

private fun String.visibleNormalize(): String = cleanVoiceReference().lowercase().replace(Regex("\\s+"), "")
