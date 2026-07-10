package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteEditSource
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ResolvedNoteArgumentsTest {
    @Test
    fun visibleReferenceOverridesStaleNumericId() = runBlocking {
        val target = note(42L, "验收客户回访")
        val result = prepareResolvedNoteArguments(
            toolName = "notes.append",
            argumentsJson = """{"note_id":7,"note_ref":"验收客户回访","content":"补充"}""",
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
            risk = McpRiskLevel.Medium,
            scope = NoteResolveScope.ActiveAndArchived,
            lookup = { request -> uniqueResult(request, target) },
            poolLoader = { listOf(target) },
        )

        val ready = result as PreparedNoteArguments.Ready
        val rewritten = JSONObject(ready.argumentsJson)
        assertEquals(42L, rewritten.getLong("note_id"))
        assertEquals(7L, rewritten.getJSONArray("discarded_conflicting_note_ids").getLong(0))
        assertEquals("补充", rewritten.getString("content"))
    }

    @Test
    fun voiceOnlyIdFailsClosedWithoutInternalMarker() = runBlocking {
        val target = note(42L, "验收客户回访")
        val result = prepareResolvedNoteArguments(
            toolName = "notes.append",
            argumentsJson = """{"note_id":42,"content":"补充"}""",
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
            risk = McpRiskLevel.Medium,
            scope = NoteResolveScope.ActiveAndArchived,
            lookup = { request -> uniqueResult(request, target) },
            poolLoader = { listOf(target) },
        )

        val failed = result as PreparedNoteArguments.Failed
        assertEquals("unsafe_voice_note_id", failed.result.errorCode)
    }

    @Test
    fun directIdIsAcceptedWhenExplicitlyMarkedInternal() = runBlocking {
        val target = note(42L, "验收客户回访")
        val result = prepareResolvedNoteArguments(
            toolName = "notes.append",
            argumentsJson = """{"note_id":42,"id_is_internal":true,"content":"补充"}""",
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
            risk = McpRiskLevel.Medium,
            scope = NoteResolveScope.ActiveAndArchived,
            lookup = { request -> uniqueResult(request, target) },
            poolLoader = { listOf(target) },
        )

        val ready = result as PreparedNoteArguments.Ready
        assertEquals(42L, JSONObject(ready.argumentsJson).getLong("note_id"))
    }

    @Test
    fun ambiguousReferenceFailsWhenToolIsSingleTarget() = runBlocking {
        val first = note(1L, "验收重复目标")
        val second = note(2L, "验收重复目标")
        val result = prepareResolvedNoteArguments(
            toolName = "notes.update_title",
            argumentsJson = """{"note_ref":"验收重复目标","new_title":"新标题"}""",
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
            risk = McpRiskLevel.Medium,
            scope = NoteResolveScope.ActiveAndArchived,
            lookup = { request ->
                NoteResolveResult(
                    requestedText = request.query,
                    normalizedText = request.query,
                    strategy = "title_exact",
                    totalMatches = 2,
                    matches = listOf(first, second),
                    resultIsLimited = false,
                    scores = mapOf(1L to 1200, 2L to 1200),
                    matchedFields = mapOf(1L to listOf("title_exact"), 2L to listOf("title_exact")),
                    poolSize = 2,
                )
            },
            poolLoader = { listOf(first, second) },
        )

        val failed = result as PreparedNoteArguments.Failed
        assertEquals("ambiguous_note_reference", failed.result.errorCode)
    }

    @Test
    fun deletedScopeRejectsActiveId() = runBlocking {
        val deleted = note(8L, "验收删除恢复", deleted = true)
        val result = prepareResolvedNoteArguments(
            toolName = "notes.restore",
            argumentsJson = """{"note_id":9,"id_is_internal":true}""",
            context = McpToolContext(source = McpToolContext.SOURCE_VOICE),
            risk = McpRiskLevel.Medium,
            scope = NoteResolveScope.Deleted,
            lookup = { request -> uniqueResult(request, deleted) },
            poolLoader = { listOf(deleted) },
        )

        val failed = result as PreparedNoteArguments.Failed
        assertEquals("note_target_out_of_scope", failed.result.errorCode)
        assertTrue(failed.result.message.contains("范围"))
    }

    private fun uniqueResult(request: NoteResolveRequest, target: Note): NoteResolveResult = NoteResolveResult(
        requestedText = request.query,
        normalizedText = request.query,
        strategy = "title_exact",
        totalMatches = 1,
        matches = listOf(target),
        resultIsLimited = false,
        scores = mapOf(target.id to 1200),
        matchedFields = mapOf(target.id to listOf("title_exact")),
        poolSize = 1,
    )

    private fun note(id: Long, title: String, deleted: Boolean = false): Note = Note(
        id = id,
        title = title,
        content = "测试正文",
        type = NoteType.Normal,
        isDone = false,
        doneAt = null,
        pinned = false,
        archived = false,
        deleted = deleted,
        color = null,
        createdAt = 1L,
        updatedAt = 2L,
        lastEditedSource = NoteEditSource.Manual,
    )
}
