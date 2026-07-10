package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteEditSource
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.Tag
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesArchiveTagTargetTest {
    @Test
    fun spokenTagExpressionBecomesTagTargetInsteadOfTitleKeyword() {
        val target = JSONObject("""{"query":"把标签是客户那一条归档","archived":true}""")
            .toArchiveTagTarget()

        requireNotNull(target)
        assertEquals(listOf("客户"), target.tagNames)
        assertEquals("", target.noteReference)
        assertFalse(target.multipleIntent)
    }

    @Test
    fun customerWordInTitleDoesNotSatisfyCustomerTagCondition() {
        val titleOnly = note(id = 1L, title = "验收客户回访", tags = listOf(tag("销售")))
        val trulyTagged = note(id = 2L, title = "联系王总", tags = listOf(tag("客户")))

        assertFalse(titleOnly.matchesAllArchiveTags(listOf("客户")))
        assertTrue(trulyTagged.matchesAllArchiveTags(listOf("客户")))
    }

    @Test
    fun tagAndSpecificTitleAreBothKeptAsConstraints() {
        val target = JSONObject(
            """{"tag_name":"客户","note_ref":"验收客户回访","archived":true}""",
        ).toArchiveTagTarget()

        requireNotNull(target)
        assertEquals(listOf("客户"), target.tagNames)
        assertEquals("验收客户回访", target.noteReference)
    }

    @Test
    fun allTaggedNotesEnablesMultipleIntent() {
        val target = JSONObject(
            """{"query":"把客户标签下所有便签归档","archived":true}""",
        ).toArchiveTagTarget()

        requireNotNull(target)
        assertEquals(listOf("客户"), target.tagNames)
        assertTrue(target.multipleIntent)
        assertEquals("", target.noteReference)
    }

    @Test
    fun archiveAndUnarchiveUseOppositeScopes() {
        assertEquals(NoteResolveScope.Active, archiveResolveScope(archived = true))
        assertEquals(NoteResolveScope.Archived, archiveResolveScope(archived = false))
    }

    private fun note(id: Long, title: String, tags: List<Tag>): Note = Note(
        id = id,
        title = title,
        content = "测试正文",
        type = NoteType.Normal,
        isDone = false,
        doneAt = null,
        pinned = false,
        archived = false,
        deleted = false,
        color = null,
        createdAt = 1L,
        updatedAt = 2L,
        lastEditedSource = NoteEditSource.Manual,
        tags = tags,
    )

    private fun tag(name: String): Tag = Tag(
        id = name.hashCode().toLong(),
        name = name,
        normalizedName = name.lowercase(),
        createdAt = 1L,
        updatedAt = 1L,
    )
}
