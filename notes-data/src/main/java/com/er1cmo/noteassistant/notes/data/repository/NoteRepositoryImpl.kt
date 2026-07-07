package com.er1cmo.noteassistant.notes.data.repository

import com.er1cmo.noteassistant.core.common.dispatchers.AppDispatchers
import com.er1cmo.noteassistant.core.common.time.TimeProvider
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.dao.NoteTagDao
import com.er1cmo.noteassistant.notes.data.dao.TagDao
import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteTagCrossRefEntity
import com.er1cmo.noteassistant.notes.data.entity.TagEntity
import com.er1cmo.noteassistant.notes.data.mapper.cleanedTagName
import com.er1cmo.noteassistant.notes.data.mapper.normalizedTagName
import com.er1cmo.noteassistant.notes.data.mapper.splitTags
import com.er1cmo.noteassistant.notes.data.mapper.toDomain
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val tagDao: TagDao,
    private val noteTagDao: NoteTagDao,
    private val dispatchers: AppDispatchers,
    private val timeProvider: TimeProvider,
) : NoteRepository {
    override fun observeActiveNotes(): Flow<List<Note>> = combine(
        noteDao.observeActiveNotes(),
        tagDao.observeTags(),
        noteTagDao.observeAll(),
    ) { notes, tags, refs -> notes.map { it.toDomain(tagsForNote(it.id, tags, refs)) } }

    override fun observeArchivedNotes(): Flow<List<Note>> = combine(
        noteDao.observeArchivedNotes(),
        tagDao.observeTags(),
        noteTagDao.observeAll(),
    ) { notes, tags, refs -> notes.map { it.toDomain(tagsForNote(it.id, tags, refs)) } }

    override fun observeDeletedNotes(): Flow<List<Note>> = combine(
        noteDao.observeDeletedNotes(),
        tagDao.observeTags(),
        noteTagDao.observeAll(),
    ) { notes, tags, refs -> notes.map { it.toDomain(tagsForNote(it.id, tags, refs)) } }

    override fun observeTags(): Flow<List<Tag>> = tagDao.observeTags().map { tags -> tags.map { it.toDomain() } }

    override fun observeNote(id: Long): Flow<Note?> = combine(
        noteDao.observeNoteById(id),
        tagDao.observeTags(),
        noteTagDao.observeAll(),
    ) { note, tags, refs -> note?.toDomain(tagsForNote(note.id, tags, refs)) }

    override suspend fun getNote(id: Long): Note? = withContext(dispatchers.io) {
        val note = noteDao.getNoteById(id) ?: return@withContext null
        val tags = tagDao.listTags()
        val refs = noteTagDao.listForNote(id)
        note.toDomain(tagsForNote(id, tags, refs))
    }

    override suspend fun createNote(
        title: String,
        content: String,
        type: NoteType,
        color: String?,
        tagText: String,
    ): Long = withContext(dispatchers.io) {
        val now = timeProvider.nowMillis()
        val cleanedTagText = tagText.cleanedTagText()
        val id = noteDao.insertNote(
            NoteEntity(
                title = title.cleanedTitle(),
                content = content.trim(),
                type = type.toStorageValue(),
                isDone = false,
                doneAt = null,
                color = color,
                tagText = cleanedTagText,
                createdAt = now,
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
        syncNoteTags(noteId = id, cleanedTagText = cleanedTagText, now = now)
        id
    }

    override suspend fun updateNote(
        id: Long,
        title: String,
        content: String,
        type: NoteType,
        color: String?,
        tagText: String,
    ) = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext
        if (existing.deleted) return@withContext
        val cleanedTitle = title.cleanedTitle()
        val cleanedContent = content.trim()
        val cleanedTagText = tagText.cleanedTagText()
        val now = timeProvider.nowMillis()
        val normalizedIsDone = if (type == NoteType.Normal) false else existing.isDone
        val normalizedDoneAt = if (type == NoteType.Normal || !normalizedIsDone) null else existing.doneAt
        val shouldRefreshUpdatedAt = cleanedTitle != existing.title ||
            cleanedContent != existing.content ||
            cleanedTagText != existing.tagText
        noteDao.insertNote(
            existing.copy(
                title = cleanedTitle,
                content = cleanedContent,
                type = type.toStorageValue(),
                isDone = normalizedIsDone,
                doneAt = normalizedDoneAt,
                color = color,
                tagText = cleanedTagText,
                updatedAt = if (shouldRefreshUpdatedAt) now else existing.updatedAt,
                lastEditedSource = "manual",
            ),
        )
        syncNoteTags(noteId = existing.id, cleanedTagText = cleanedTagText, now = now)
    }

    override suspend fun toggleTodoDone(id: Long, done: Boolean): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted || existing.type.lowercase() != "todo") return@withContext false
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                isDone = done,
                doneAt = if (done) now else null,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted) return@withContext false
        if (existing.pinned == pinned) return@withContext true
        noteDao.insertNote(existing.copy(pinned = pinned, lastEditedSource = "manual"))
        true
    }

    override suspend fun setArchived(id: Long, archived: Boolean): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted) return@withContext false
        if (existing.archived == archived) return@withContext true
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                archived = archived,
                archivedAt = if (archived) now else null,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun softDelete(id: Long): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted) return@withContext true
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                deleted = true,
                deletedAt = now,
                archived = false,
                archivedAt = null,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun restoreDeleted(id: Long): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (!existing.deleted) return@withContext true
        noteDao.insertNote(existing.copy(deleted = false, deletedAt = null, lastEditedSource = "manual"))
        true
    }

    override suspend fun permanentlyDelete(id: Long): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (!existing.deleted) return@withContext false
        noteTagDao.deleteForNote(id)
        noteDao.deleteNoteById(id) > 0
    }

    override suspend fun createTag(name: String): Boolean = withContext(dispatchers.io) {
        val cleaned = name.cleanedTagName()
        if (cleaned.isBlank()) return@withContext false
        val now = timeProvider.nowMillis()
        ensureTagId(name = cleaned, now = now) != null
    }

    override suspend fun renameTag(id: Long, name: String): Boolean = withContext(dispatchers.io) {
        val existing = tagDao.getTagById(id) ?: return@withContext false
        val cleaned = name.cleanedTagName()
        if (cleaned.isBlank()) return@withContext false
        val normalized = cleaned.normalizedTagName()
        val conflict = tagDao.getTagByNormalizedName(normalized)
        if (conflict != null && conflict.id != id) return@withContext false
        val now = timeProvider.nowMillis()
        tagDao.updateTag(existing.copy(name = cleaned, normalizedName = normalized, updatedAt = now))
        replaceTagTextName(oldName = existing.name, newName = cleaned)
        true
    }

    override suspend fun deleteTag(id: Long): Boolean = withContext(dispatchers.io) {
        val existing = tagDao.getTagById(id) ?: return@withContext false
        noteTagDao.deleteForTag(id)
        tagDao.deleteTagById(id)
        removeTagTextName(existing.name)
        true
    }

    override suspend fun ensureDemoData() = withContext(dispatchers.io) {
        if (noteDao.countNotes() == 0) {
            val now = timeProvider.nowMillis()
            noteDao.insertNotes(
                listOf(
                    NoteEntity(
                        title = "联系王总",
                        content = "明天上午十点联系王总，确认屏幕报价。",
                        type = "todo",
                        color = "#FFF2B8",
                        tagText = "客户",
                        pinned = true,
                        createdAt = now - 1000L * 60 * 60,
                        updatedAt = now,
                        lastEditedSource = "manual",
                    ),
                    NoteEntity(
                        title = "屏幕校色记录",
                        content = "记录 27 寸屏幕亮度、色温和边框间隙。",
                        type = "normal",
                        color = "#E7F0FF",
                        tagText = "硬件",
                        createdAt = now - 1000L * 60 * 60 * 24,
                        updatedAt = now - 1000L * 60 * 60 * 24,
                        lastEditedSource = "manual",
                    ),
                    NoteEntity(
                        title = "手柄测试记录",
                        content = "测试三款游戏手柄的按键回弹与握持手感。",
                        type = "normal",
                        color = "#E4F6EC",
                        tagText = "硬件",
                        createdAt = now - 1000L * 60 * 60 * 30,
                        updatedAt = now - 1000L * 60 * 60 * 30,
                        lastEditedSource = "manual",
                    ),
                    NoteEntity(
                        title = "便携屏到货清点",
                        content = "便携屏、支架和电源适配器到货清点。",
                        type = "normal",
                        color = "#F9E4EF",
                        tagText = "生活",
                        createdAt = now - 1000L * 60 * 60 * 48,
                        updatedAt = now - 1000L * 60 * 60 * 48,
                        lastEditedSource = "manual",
                    ),
                ),
            )
        }
        backfillFormalTagsFromTagText()
    }

    private fun syncNoteTags(noteId: Long, cleanedTagText: String, now: Long) {
        val tagIds = cleanedTagText.splitTags().mapNotNull { ensureTagId(it, now) }
        noteTagDao.deleteForNote(noteId)
        if (tagIds.isNotEmpty()) {
            noteTagDao.insertAll(tagIds.distinct().map { tagId -> NoteTagCrossRefEntity(noteId = noteId, tagId = tagId, createdAt = now) })
        }
    }

    private fun ensureTagId(name: String, now: Long): Long? {
        val cleaned = name.cleanedTagName()
        if (cleaned.isBlank()) return null
        val normalized = cleaned.normalizedTagName()
        tagDao.getTagByNormalizedName(normalized)?.let { return it.id }
        val insertedId = tagDao.insertTag(
            TagEntity(
                name = cleaned,
                normalizedName = normalized,
                createdAt = now,
                updatedAt = now,
            ),
        )
        return if (insertedId > 0) insertedId else tagDao.getTagByNormalizedName(normalized)?.id
    }

    private fun backfillFormalTagsFromTagText() {
        val now = timeProvider.nowMillis()
        noteDao.listAllNotes().forEach { note ->
            val cleanedTagText = note.tagText.cleanedTagText()
            if (cleanedTagText.isNotBlank()) {
                syncNoteTags(noteId = note.id, cleanedTagText = cleanedTagText, now = now)
                if (cleanedTagText != note.tagText) {
                    noteDao.insertNote(note.copy(tagText = cleanedTagText))
                }
            }
        }
    }

    private fun replaceTagTextName(oldName: String, newName: String) {
        val oldNormalized = oldName.normalizedTagName()
        noteDao.listAllNotes().forEach { note ->
            val currentTags = note.tagText.splitTags()
            if (currentTags.any { it.normalizedTagName() == oldNormalized }) {
                val replaced = currentTags.map { if (it.normalizedTagName() == oldNormalized) newName else it }.joinToString("、")
                noteDao.insertNote(note.copy(tagText = replaced))
            }
        }
    }

    private fun removeTagTextName(name: String) {
        val normalized = name.normalizedTagName()
        noteDao.listAllNotes().forEach { note ->
            val remaining = note.tagText.splitTags().filterNot { it.normalizedTagName() == normalized }.joinToString("、")
            if (remaining != note.tagText) {
                noteDao.insertNote(note.copy(tagText = remaining))
            }
        }
    }

    private fun tagsForNote(
        noteId: Long,
        tags: List<TagEntity>,
        refs: List<NoteTagCrossRefEntity>,
    ): List<TagEntity> {
        val ids = refs.filter { it.noteId == noteId }.map { it.tagId }.toSet()
        return tags.filter { it.id in ids }.sortedBy { it.normalizedName }
    }

    private fun String.cleanedTitle(): String = trim().ifBlank { "未命名便签" }

    private fun String.cleanedTagText(): String = splitTags().joinToString("、")

    private fun NoteType.toStorageValue(): String = when (this) {
        NoteType.Todo -> "todo"
        NoteType.Normal -> "normal"
    }
}
