package com.er1cmo.noteassistant.notes.data.repository

import androidx.room.withTransaction
import com.er1cmo.noteassistant.core.common.dispatchers.AppDispatchers
import com.er1cmo.noteassistant.notes.data.dao.AssistantCommandLogDao
import com.er1cmo.noteassistant.notes.data.dao.NoteRevisionDao
import com.er1cmo.noteassistant.notes.data.dao.PendingConfirmationDao
import com.er1cmo.noteassistant.notes.data.db.NoteDatabase
import com.er1cmo.noteassistant.notes.data.mapper.toDomain
import com.er1cmo.noteassistant.notes.data.mapper.toEntity
import com.er1cmo.noteassistant.notes.domain.model.command.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.model.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.model.command.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.command.PendingConfirmation
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommandTraceRepositoryImpl @Inject constructor(
    private val database: NoteDatabase,
    private val commandLogDao: AssistantCommandLogDao,
    private val revisionDao: NoteRevisionDao,
    private val pendingConfirmationDao: PendingConfirmationDao,
    private val dispatchers: AppDispatchers,
) : CommandTraceRepository {
    override fun observeRecentCommandLogs(limit: Int): Flow<List<AssistantCommandLog>> =
        commandLogDao.observeRecent(limit).map { logs -> logs.map { it.toDomain() } }

    override fun observeRevisionsForNote(noteId: Long): Flow<List<NoteRevision>> =
        revisionDao.observeForNote(noteId).map { revisions -> revisions.map { it.toDomain() } }

    override fun observePendingConfirmations(nowMillis: Long): Flow<List<PendingConfirmation>> =
        pendingConfirmationDao.observePending(nowMillis).map { pending -> pending.map { it.toDomain() } }

    override suspend fun getCommandLog(id: Long): AssistantCommandLog? = withContext(dispatchers.io) {
        commandLogDao.getCommandLogById(id)?.toDomain()
    }

    override suspend fun insertCommandLog(log: AssistantCommandLog): Long = withContext(dispatchers.io) {
        commandLogDao.insertCommandLog(log.toEntity())
    }

    override suspend fun updateCommandLog(log: AssistantCommandLog): Boolean = withContext(dispatchers.io) {
        commandLogDao.updateCommandLog(log.toEntity()) > 0
    }

    override suspend fun updateCommandLogCompletion(
        id: Long,
        status: CommandStatus,
        confirmationStatus: ConfirmationStatus,
        resultJson: String?,
        affectedNoteIdsJson: String?,
        affectedTagIdsJson: String?,
        errorCode: String?,
        errorMessage: String?,
        completedAt: Long,
    ): Boolean = withContext(dispatchers.io) {
        commandLogDao.updateCompletion(
            id = id,
            status = status.storageValue,
            confirmationStatus = confirmationStatus.storageValue,
            resultJson = resultJson,
            affectedNoteIdsJson = affectedNoteIdsJson,
            affectedTagIdsJson = affectedTagIdsJson,
            errorCode = errorCode,
            errorMessage = errorMessage,
            completedAt = completedAt,
        ) > 0
    }

    override suspend fun getRevision(id: Long): NoteRevision? = withContext(dispatchers.io) {
        revisionDao.getRevisionById(id)?.toDomain()
    }

    override suspend fun insertRevision(revision: NoteRevision): Long = withContext(dispatchers.io) {
        revisionDao.insertRevision(revision.toEntity())
    }

    override suspend fun insertRevisions(revisions: List<NoteRevision>): List<Long> = withContext(dispatchers.io) {
        if (revisions.isEmpty()) emptyList() else revisionDao.insertRevisions(revisions.map { it.toEntity() })
    }

    override suspend fun getPendingConfirmation(confirmationId: String): PendingConfirmation? = withContext(dispatchers.io) {
        pendingConfirmationDao.getById(confirmationId)?.toDomain()
    }

    override suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation) = withContext(dispatchers.io) {
        pendingConfirmationDao.insertPendingConfirmation(pendingConfirmation.toEntity())
    }

    override suspend fun updatePendingConfirmationStatus(
        confirmationId: String,
        status: ConfirmationStatus,
    ): Boolean = withContext(dispatchers.io) {
        pendingConfirmationDao.updateStatus(confirmationId = confirmationId, status = status.storageValue) > 0
    }

    override suspend fun markExpiredPendingConfirmations(nowMillis: Long): Int = withContext(dispatchers.io) {
        pendingConfirmationDao.markExpired(nowMillis)
    }

    override suspend fun <T> runInTraceTransaction(block: suspend () -> T): T = withContext(dispatchers.io) {
        database.withTransaction { block() }
    }
}
