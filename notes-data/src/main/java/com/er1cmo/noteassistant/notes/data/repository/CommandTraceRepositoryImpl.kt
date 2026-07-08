package com.er1cmo.noteassistant.notes.data.repository

import androidx.room.withTransaction
import com.er1cmo.noteassistant.core.common.dispatchers.AppDispatchers
import com.er1cmo.noteassistant.notes.data.dao.AssistantCommandLogDao
import com.er1cmo.noteassistant.notes.data.dao.NoteRevisionDao
import com.er1cmo.noteassistant.notes.data.dao.PendingConfirmationDao
import com.er1cmo.noteassistant.notes.data.db.NoteDatabase
import com.er1cmo.noteassistant.notes.data.mapper.toDomain
import com.er1cmo.noteassistant.notes.data.mapper.toEntity
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceTransaction
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
        commandLogDao.observeRecentCommandLogs(limit).map { logs -> logs.map { it.toDomain() } }

    override fun observeRevisionsForNote(noteId: Long): Flow<List<NoteRevision>> =
        revisionDao.observeRevisionsForNote(noteId).map { revisions -> revisions.map { it.toDomain() } }

    override suspend fun getCommandLog(id: Long): AssistantCommandLog? = withContext(dispatchers.io) {
        commandLogDao.getCommandLogById(id)?.toDomain()
    }

    override suspend fun insertCommandLog(log: AssistantCommandLog): Long = withContext(dispatchers.io) {
        commandLogDao.insertCommandLog(log.toEntity())
    }

    override suspend fun updateCommandLog(log: AssistantCommandLog) = withContext(dispatchers.io) {
        commandLogDao.updateCommandLog(log.toEntity())
    }

    override suspend fun insertRevision(revision: NoteRevision): Long = withContext(dispatchers.io) {
        revisionDao.insertRevision(revision.toEntity())
    }

    override suspend fun listRevisionsForNote(noteId: Long): List<NoteRevision> = withContext(dispatchers.io) {
        revisionDao.listRevisionsForNote(noteId).map { it.toDomain() }
    }

    override suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation) = withContext(dispatchers.io) {
        pendingConfirmationDao.insertPendingConfirmation(pendingConfirmation.toEntity())
    }

    override suspend fun getPendingConfirmation(confirmationId: String): PendingConfirmation? = withContext(dispatchers.io) {
        pendingConfirmationDao.getPendingConfirmationById(confirmationId)?.toDomain()
    }

    override suspend fun listPendingConfirmations(limit: Int, onlyPending: Boolean): List<PendingConfirmation> = withContext(dispatchers.io) {
        pendingConfirmationDao.listPendingConfirmations(limit = limit, onlyPending = onlyPending).map { it.toDomain() }
    }

    override suspend fun updatePendingConfirmation(pendingConfirmation: PendingConfirmation) = withContext(dispatchers.io) {
        pendingConfirmationDao.updatePendingConfirmation(pendingConfirmation.toEntity())
    }

    override suspend fun markExpiredPendingConfirmations(nowMillis: Long): Int = withContext(dispatchers.io) {
        pendingConfirmationDao.markExpired(nowMillis)
    }

    override suspend fun <T> runInTraceTransaction(block: suspend CommandTraceTransaction.() -> T): T = withContext(dispatchers.io) {
        database.withTransaction {
            TransactionImpl().block()
        }
    }

    private inner class TransactionImpl : CommandTraceTransaction {
        override suspend fun insertCommandLog(log: AssistantCommandLog): Long = commandLogDao.insertCommandLog(log.toEntity())

        override suspend fun updateCommandLog(log: AssistantCommandLog) = commandLogDao.updateCommandLog(log.toEntity())

        override suspend fun insertRevision(revision: NoteRevision): Long = revisionDao.insertRevision(revision.toEntity())

        override suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation) =
            pendingConfirmationDao.insertPendingConfirmation(pendingConfirmation.toEntity())

        override suspend fun updatePendingConfirmation(pendingConfirmation: PendingConfirmation) =
            pendingConfirmationDao.updatePendingConfirmation(pendingConfirmation.toEntity())
    }
}
