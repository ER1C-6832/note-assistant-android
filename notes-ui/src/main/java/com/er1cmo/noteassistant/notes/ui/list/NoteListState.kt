package com.er1cmo.noteassistant.notes.ui.list

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.Tag

data class NoteListState(
    val notes: List<Note> = emptyList(),
    val deletedNotes: List<Note> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val isLoading: Boolean = true,
)
