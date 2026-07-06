package com.er1cmo.noteassistant.notes.ui.list

import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantState
import com.er1cmo.noteassistant.notes.domain.model.Note

data class NoteListState(
    val notes: List<Note> = emptyList(),
    val assistantState: AssistantState = AssistantState(),
    val isLoading: Boolean = true,
)
