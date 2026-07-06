package com.er1cmo.noteassistant.notes.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NoteColorPickerViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val noteId: Long? = savedStateHandle.get<Long>("noteId")

    private val _state = MutableStateFlow(NoteColorPickerState())
    val state: StateFlow<NoteColorPickerState> = _state

    init {
        noteId?.let { id ->
            viewModelScope.launch {
                noteUseCases.observeNote(id).collect { note ->
                    _state.update { it.copy(note = note, isLoading = false) }
                }
            }
        } ?: _state.update { it.copy(isLoading = false) }
    }

    fun selectColor(hex: String) {
        val note = _state.value.note ?: return
        if (note.color == hex) return
        viewModelScope.launch {
            noteUseCases.updateNote(
                id = note.id,
                title = note.title,
                content = note.content,
                type = note.type,
                color = hex,
                tagText = note.tags.joinToString("、") { it.name },
            )
        }
    }
}

data class NoteColorPickerState(
    val note: Note? = null,
    val isLoading: Boolean = true,
)
