package com.er1cmo.noteassistant.notes.ui.editor

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
class NoteEditorViewModel @Inject constructor(
    private val noteUseCases: NoteUseCases,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val noteId: Long? = savedStateHandle.get<Long>("noteId")
    private val initialTag: String = savedStateHandle.get<String>("tag").orEmpty().trim()

    private val _state = MutableStateFlow(
        NoteEditorState(
            noteId = noteId,
            tagText = if (noteId == null) initialTag else "",
            isLoading = noteId != null,
        ),
    )
    val state: StateFlow<NoteEditorState> = _state

    init {
        noteId?.let { id ->
            viewModelScope.launch {
                val note = noteUseCases.getNote(id)
                if (note != null) {
                    _state.value = NoteEditorState(
                        noteId = note.id,
                        title = note.title,
                        content = note.content,
                        tagText = note.tags.joinToString("、") { it.name },
                        type = note.type,
                        color = note.color ?: NoteEditorState().color,
                        isLoading = false,
                    )
                } else {
                    _state.update { it.copy(isLoading = false) }
                }
            }
        }
    }

    fun updateTitle(title: String) {
        _state.update { it.copy(title = title, saved = false) }
    }

    fun updateContent(content: String) {
        _state.update { it.copy(content = content, saved = false) }
    }

    fun updateTagText(tagText: String) {
        _state.update { it.copy(tagText = tagText, saved = false) }
    }

    fun updateType(type: NoteType) {
        _state.update { it.copy(type = type, saved = false) }
    }

    fun updateColor(color: String) {
        _state.update { it.copy(color = color, saved = false) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving || current.title.isBlank() && current.content.isBlank()) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true) }
            if (current.noteId == null) {
                noteUseCases.createNote(
                    title = current.title,
                    content = current.content,
                    type = current.type,
                    color = current.color,
                    tagText = current.tagText,
                )
            } else {
                noteUseCases.updateNote(
                    id = current.noteId,
                    title = current.title,
                    content = current.content,
                    type = current.type,
                    color = current.color,
                    tagText = current.tagText,
                )
            }
            _state.update { it.copy(isSaving = false, saved = true) }
        }
    }
}
