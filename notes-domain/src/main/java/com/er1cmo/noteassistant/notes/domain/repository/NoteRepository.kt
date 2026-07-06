package com.er1cmo.noteassistant.notes.domain.repository

import com.er1cmo.noteassistant.notes.domain.model.Note
import kotlinx.coroutines.flow.Flow

interface NoteRepository {
    fun observeActiveNotes(): Flow<List<Note>>
    suspend fun ensureDemoData()
}
