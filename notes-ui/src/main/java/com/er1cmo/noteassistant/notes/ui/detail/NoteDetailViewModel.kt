package com.er1cmo.noteassistant.notes.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val noteId: Long? = savedStateHandle.get<Long>("noteId")

    private val _state = MutableStateFlow(NoteDetailState())
    val state: StateFlow<NoteDetailState> = _state

    init {
        refresh()
    }

    fun toggleDone(done: Boolean) {
        val id = noteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true) }
            noteUseCases.toggleTodoDone(id = id, done = done)
            refresh(isActing = false)
        }
    }

    fun togglePinned() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true) }
            noteUseCases.setNotePinned(id = note.id, pinned = !note.pinned)
            refresh(isActing = false)
        }
    }

    fun softDelete() {
        val id = noteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true) }
            noteUseCases.softDeleteNote(id)
            _state.update { it.copy(isActing = false, closeAfterAction = true) }
        }
    }

    fun restore() {
        val id = noteId ?: return
        viewModelScope.launch {
            _state.update { it.copy(isActing = true) }
            noteUseCases.restoreDeletedNote(id)
            refresh(isActing = false)
        }
    }

    private fun refresh(isActing: Boolean = false) {
        viewModelScope.launch {
            val note = noteId?.let { noteUseCases.getNote(it) }
            val normalizedNote = if (note?.type == NoteType.Normal && note.isDone) {
                note.copy(isDone = false, doneAt = null)
            } else {
                note
            }
            _state.update { it.copy(note = normalizedNote, isLoading = false, isActing = isActing) }
        }
    }
}
