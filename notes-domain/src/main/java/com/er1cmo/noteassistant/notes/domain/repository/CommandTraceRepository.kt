package com.er1cmo.noteassistant.notes.domain.repository

import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation
import kotlinx.coroutines.flow.Flow

interface CommandTraceRepository {
    fun observeRecentCommandLogs(limit: Int = 50): Flow<List<AssistantCommandLog>>
    fun observeRevisionsForNote(noteId: Long): Flow<List<NoteRevision>>

    suspend fun getCommandLog(id: Long): AssistantCommandLog?
    suspend fun insertCommandLog(log: AssistantCommandLog): Long
    suspend fun updateCommandLog(log: AssistantCommandLog)

    suspend fun insertRevision(revision: NoteRevision): Long
    suspend fun listRevisionsForNote(noteId: Long): List<NoteRevision>

    suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation)
    suspend fun getPendingConfirmation(confirmationId: String): PendingConfirmation?
    suspend fun listPendingConfirmations(limit: Int = 20): List<PendingConfirmation>
    suspend fun updatePendingConfirmation(pendingConfirmation: PendingConfirmation)
    suspend fun markExpiredPendingConfirmations(nowMillis: Long): Int

    suspend fun <T> runInTraceTransaction(block: suspend CommandTraceTransaction.() -> T): T
}

interface CommandTraceTransaction {
    suspend fun insertCommandLog(log: AssistantCommandLog): Long
    suspend fun updateCommandLog(log: AssistantCommandLog)
    suspend fun insertRevision(revision: NoteRevision): Long
    suspend fun insertPendingConfirmation(pendingConfirmation: PendingConfirmation)
    suspend fun updatePendingConfirmation(pendingConfirmation: PendingConfirmation)
}
