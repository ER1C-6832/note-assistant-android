package com.er1cmo.noteassistant.notes.data.repository

import com.er1cmo.noteassistant.core.common.dispatchers.AppDispatchers
import com.er1cmo.noteassistant.core.common.time.TimeProvider
import com.er1cmo.noteassistant.notes.data.dao.NoteDao
import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.data.mapper.splitTags
import com.er1cmo.noteassistant.notes.data.mapper.toDomain
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.repository.NoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NoteRepositoryImpl @Inject constructor(
    private val noteDao: NoteDao,
    private val dispatchers: AppDispatchers,
    private val timeProvider: TimeProvider,
) : NoteRepository {
    override fun observeActiveNotes(): Flow<List<Note>> = noteDao
        .observeActiveNotes()
        .map { entities -> entities.map { it.toDomain() } }

    override fun observeDeletedNotes(): Flow<List<Note>> = noteDao
        .observeDeletedNotes()
        .map { entities -> entities.map { it.toDomain() } }

    override suspend fun getNote(id: Long): Note? = withContext(dispatchers.io) {
        noteDao.getNoteById(id)?.toDomain()
    }

    override suspend fun createNote(
        title: String,
        content: String,
        type: NoteType,
        color: String?,
        tagText: String,
    ): Long = withContext(dispatchers.io) {
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            NoteEntity(
                title = title.cleanedTitle(),
                content = content.trim(),
                type = type.toStorageValue(),
                isDone = false,
                doneAt = null,
                color = color,
                tagText = tagText.cleanedTagText(),
                createdAt = now,
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
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
        val now = timeProvider.nowMillis()
        val normalizedIsDone = if (type == NoteType.Normal) false else existing.isDone
        val normalizedDoneAt = if (type == NoteType.Normal || !normalizedIsDone) null else existing.doneAt
        noteDao.insertNote(
            existing.copy(
                title = title.cleanedTitle(),
                content = content.trim(),
                type = type.toStorageValue(),
                isDone = normalizedIsDone,
                doneAt = normalizedDoneAt,
                color = color,
                tagText = tagText.cleanedTagText(),
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
    }

    override suspend fun toggleTodoDone(id: Long, done: Boolean): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted || existing.type.lowercase() != "todo") return@withContext false
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                isDone = done,
                doneAt = if (done) now else null,
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun setPinned(id: Long, pinned: Boolean): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (existing.deleted) return@withContext false
        if (existing.pinned == pinned) return@withContext true
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                pinned = pinned,
                updatedAt = now,
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
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun restoreDeleted(id: Long): Boolean = withContext(dispatchers.io) {
        val existing = noteDao.getNoteById(id) ?: return@withContext false
        if (!existing.deleted) return@withContext true
        val now = timeProvider.nowMillis()
        noteDao.insertNote(
            existing.copy(
                deleted = false,
                deletedAt = null,
                updatedAt = now,
                lastEditedSource = "manual",
            ),
        )
        true
    }

    override suspend fun ensureDemoData() = withContext(dispatchers.io) {
        if (noteDao.countNotes() > 0) return@withContext
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

    private fun String.cleanedTitle(): String = trim().ifBlank { "未命名便签" }

    private fun String.cleanedTagText(): String = splitTags().joinToString("、")

    private fun NoteType.toStorageValue(): String = when (this) {
        NoteType.Todo -> "todo"
        NoteType.Normal -> "normal"
    }
}
