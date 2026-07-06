package com.er1cmo.noteassistant.notes.domain.repository

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeActiveNotes(): Flow<List<Note>>
    suspend fun getNote(id: Long): Note?
    suspend fun createNote(
        title: String,
        content: String,
        type: NoteType,
        color: String?,
    ): Long
    suspend fun updateNote(
        id: Long,
        title: String,
        content: String,
        type: NoteType,
        color: String?,
    )
    suspend fun ensureDemoData()
}
