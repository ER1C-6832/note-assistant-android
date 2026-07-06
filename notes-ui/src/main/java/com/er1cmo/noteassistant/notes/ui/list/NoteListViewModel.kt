package com.er1cmo.noteassistant.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteListViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
) : ViewModel() {
    val state = combine(
        noteUseCases.listNotes(),
        noteUseCases.listDeletedNotes(),
    ) { notes, deletedNotes ->
        NoteListState(notes = notes, deletedNotes = deletedNotes, isLoading = false)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = NoteListState(),
    )

    init {
        viewModelScope.launch {
            noteUseCases.seedDemoNotes()
        }
    }

    fun toggleTodoDone(noteId: Long, done: Boolean) {
        viewModelScope.launch {
            noteUseCases.toggleTodoDone(id = noteId, done = done)
        }
    }
}
