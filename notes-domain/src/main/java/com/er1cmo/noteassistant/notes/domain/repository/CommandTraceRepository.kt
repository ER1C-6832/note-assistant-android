package com.er1cmo.noteassistant.notes.domain.repository

import com.er1cmo.noteassistant.notes.domain.model.command.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.model.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.model.command.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.command.PendingConfirmation
import kotlinx.coroutines.flow.Flow

interface CommandTraceRepository {
    fun observeRecentCommandLogs(limit: Int = 50): Flow<List<AssistantCommandLog>>
    fun observeRevisionsForNote(noteId: Long): Flow<List<NoteRevision>>
    fun observePendingConfirmations(nowMillis: Long): Flow<List<PendingConfirmation>>

    suspend fun getCommandLog(id: Long): AssistantCommandLog?
    suspend fun insertCommandLog(log: AssistantCommandLog): Long
    suspend fun updateCommandLog(log: AssistantCommandLog): Boolean
    suspend fun updateCommandLogCompletion(
        id: Long,
        status: CommandStatus,
        confirmationStatus: ConfirmationStatus,
        resultJson: String?,
        affectedNoteIdsJson: String?,
        affectedTagIdsJson: String?,
        errorCode: String?,
        errorMessage: String?,
        completedAt: Long,
    ): Boolean

    suspend fun getRevision(id: Long): NoteRevision?
    suspend fun insertRevision(revision: NoteRevision): Long
    suspend fun insertRevisions(revisions: List<NoteRevision>): List<Long>

    suspend fun getPendingConfirmation(confirmationId: String): PendingConfirmation?
    suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation)
    suspend fun updatePendingConfirmationStatus(
        confirmationId: String,
        status: ConfirmationStatus,
    ): Boolean
    suspend fun markExpiredPendingConfirmations(nowMillis: Long): Int

    suspend fun <T> runInTraceTransaction(block: suspend () -> T): T
}
