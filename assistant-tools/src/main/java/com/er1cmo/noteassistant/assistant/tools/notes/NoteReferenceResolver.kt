package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

class NoteReferenceResolver @Inject constructor(
    private val noteUseCases: NoteUseCases,
) {
    suspend fun resolve(request: NoteResolveRequest): NoteResolveResult {
        val searchTerms = request.query.toAssistantSearchTerms()
        val pool = loadPool(request.scope)
            .filterByType(request.type)
            .filterByTags(request.tags)
            .filterDone(request.includeDone)
            .distinctBy { it.id }

        val exactTitle = request.exactTitle.cleanVoiceReference()
        if (exactTitle.isNotBlank()) {
            val titleResult = resolveTitle(pool, exactTitle, request.limit)
            if (titleResult.matches.isNotEmpty()) return titleResult
        }

        for (term in searchTerms) {
            val titleResult = resolveTitle(pool, term, request.limit)
            if (titleResult.matches.isNotEmpty()) return titleResult.copy(
                requestedText = request.query,
                normalizedText = term,
            )
        }

        for (term in searchTerms) {
            val results = noteUseCases.searchNotes(notes = pool, query = term, limit = 0)
            if (results.isNotEmpty()) {
                val notes = results.map { it.note }.distinctBy { it.id }
                return NoteResolveResult(
                    requestedText = request.query,
                    normalizedText = term,
                    strategy = if (term == request.query.cleanVoiceReference()) "full_text" else "full_text_intent_cleaned",
                    totalMatches = notes.size,
                    matches = notes.take(request.limit),
                    resultIsLimited = notes.size > request.limit,
                    scores = results.associate { it.note.id to it.score },
                    matchedFields = results.associate { result -> result.note.id to result.matchedFields.map { it.name.lowercase() } },
                    poolSize = pool.size,
                )
            }
        }

        return NoteResolveResult(
            requestedText = request.query,
            normalizedText = searchTerms.firstOrNull().orEmpty(),
            strategy = "no_match",
            totalMatches = 0,
            matches = emptyList(),
            resultIsLimited = false,
            scores = emptyMap(),
            matchedFields = emptyMap(),
            poolSize = pool.size,
        )
    }

    suspend fun loadPool(scope: NoteResolveScope): List<Note> = buildList {
        when (scope) {
            NoteResolveScope.Active -> addAll(noteUseCases.listNotes().first())
            NoteResolveScope.Archived -> addAll(noteUseCases.listArchivedNotes().first())
            NoteResolveScope.Deleted -> addAll(noteUseCases.listDeletedNotes().first())
            NoteResolveScope.ActiveAndArchived -> {
                addAll(noteUseCases.listNotes().first())
                addAll(noteUseCases.listArchivedNotes().first())
            }
            NoteResolveScope.All -> {
                addAll(noteUseCases.listNotes().first())
                addAll(noteUseCases.listArchivedNotes().first())
                addAll(noteUseCases.listDeletedNotes().first())
            }
        }
    }.distinctBy { it.id }

    private fun resolveTitle(
        notes: List<Note>,
        text: String,
        limit: Int,
    ): NoteResolveResult {
        val normalized = text.visibleTitleNormalize()
        val exact = notes.filter { it.title.visibleTitleNormalize() == normalized }
            .sortedByRecent()
        if (exact.isNotEmpty()) {
            return NoteResolveResult(
                requestedText = text,
                normalizedText = text,
                strategy = "title_exact",
                totalMatches = exact.size,
                matches = exact.take(limit),
                resultIsLimited = exact.size > limit,
                scores = exact.associate { it.id to 1200 },
                matchedFields = exact.associate { it.id to listOf("title_exact") },
                poolSize = notes.size,
            )
        }
        val contains = notes.filter { it.title.visibleTitleNormalize().contains(normalized) }
            .sortedByRecent()
        if (contains.isNotEmpty()) {
            return NoteResolveResult(
                requestedText = text,
                normalizedText = text,
                strategy = "title_contains",
                totalMatches = contains.size,
                matches = contains.take(limit),
                resultIsLimited = contains.size > limit,
                scores = contains.associate { it.id to 950 },
                matchedFields = contains.associate { it.id to listOf("title_contains") },
                poolSize = notes.size,
            )
        }
        return NoteResolveResult(
            requestedText = text,
            normalizedText = text,
            strategy = "title_no_match",
            totalMatches = 0,
            matches = emptyList(),
            resultIsLimited = false,
            scores = emptyMap(),
            matchedFields = emptyMap(),
            poolSize = notes.size,
        )
    }
}

internal enum class NoteResolveScope {
    Active,
    Archived,
    Deleted,
    ActiveAndArchived,
    All,
}

internal data class NoteResolveRequest(
    val query: String,
    val exactTitle: String = "",
    val scope: NoteResolveScope = NoteResolveScope.All,
    val limit: Int = 20,
    val tags: List<String> = emptyList(),
    val type: String = "",
    val includeDone: Boolean? = null,
)

internal data class NoteResolveResult(
    val requestedText: String,
    val normalizedText: String,
    val strategy: String,
    val totalMatches: Int,
    val matches: List<Note>,
    val resultIsLimited: Boolean,
    val scores: Map<Long, Int>,
    val matchedFields: Map<Long, List<String>>,
    val poolSize: Int,
) {
    fun toJson(kind: String = "resolve"): JSONObject = JSONObject()
        .putAssistantNoteReferenceRule()
        .put("kind", kind)
        .put("query", requestedText)
        .put("normalized_query", normalizedText)
        .put("match_strategy", strategy)
        .put("pool_size", poolSize)
        .put("count", matches.size)
        .put("total_matching_count", totalMatches)
        .put("result_is_limited", resultIsLimited)
        .put("results", matches.toJsonArrayWithMeta(this))
        .put("assistant_next_step_hint", nextStepHint())

    fun nextStepHint(): String = when {
        totalMatches == 0 -> "No matching note was found. Do not loop list_recent/search. Ask the user for a more visible title or a different keyword."
        resultIsLimited -> "Too many notes matched. Ask the user to narrow the title or query before any mutation."
        matches.size == 1 -> "Exactly one note matched. For mutation tools, pass note_ref/title from this item or its note_id from this exact result."
        else -> "Multiple notes matched. If the user asked for all related notes, use a high-risk tool with query and allow_multiple=true so the app can show a confirmation preview. Otherwise ask the user to clarify."
    }
}

internal fun String.toNoteResolveScope(defaultScope: NoteResolveScope = NoteResolveScope.All): NoteResolveScope = when (trim().lowercase()) {
    "active" -> NoteResolveScope.Active
    "archived" -> NoteResolveScope.Archived
    "deleted", "trash" -> NoteResolveScope.Deleted
    "active_archived", "deletable" -> NoteResolveScope.ActiveAndArchived
    "all" -> NoteResolveScope.All
    else -> defaultScope
}

private fun List<Note>.filterByType(type: String): List<Note> = when (type.trim().lowercase()) {
    "normal", "普通" -> filter { it.type.name.equals("Normal", ignoreCase = true) }
    "todo", "待办" -> filter { it.type.name.equals("Todo", ignoreCase = true) }
    else -> this
}

private fun List<Note>.filterByTags(tags: List<String>): List<Note> {
    if (tags.isEmpty()) return this
    return filter { note ->
        tags.all { wanted ->
            note.tags.any { tag ->
                tag.name.equals(wanted.trimStart('#'), ignoreCase = true)
            }
        }
    }
}

private fun List<Note>.filterDone(includeDone: Boolean?): List<Note> {
    return when (includeDone) {
        null, true -> this
        false -> filter { !it.isDone }
    }
}

private fun List<Note>.sortedByRecent(): List<Note> =
    sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })

private fun List<Note>.toJsonArrayWithMeta(result: NoteResolveResult): JSONArray = JSONArray().also { array ->
    forEach { note ->
        array.put(
            note.toAssistantNoteResultJson()
                .put("score", result.scores[note.id] ?: JSONObject.NULL)
                .put("matched_fields", JSONArray(result.matchedFields[note.id].orEmpty())),
        )
    }
}
