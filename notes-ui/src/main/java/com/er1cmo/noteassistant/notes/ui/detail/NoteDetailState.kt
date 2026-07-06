package com.er1cmo.noteassistant.notes.ui.detail

import com.er1cmo.noteassistant.notes.domain.model.Note

data class NoteDetailState(
    val note: Note? = null,
    val isLoading: Boolean = true,
)
