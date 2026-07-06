package com.er1cmo.noteassistant.notes.ui.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.notes.domain.model.Note
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
        val id = noteId
        if (id == null) {
            _state.update { it.copy(isLoading = false, message = "便签已不存在") }
        } else {
            viewModelScope.launch {
                noteUseCases.observeNote(id).collect { note ->
                    val current = _state.value
                    if (note == null) {
                        _state.update { it.copy(note = null, isLoading = false, message = "便签已不存在") }
                    } else if (!current.isDirty || current.isSaving || current.note?.id != note.id) {
                        _state.value = note.toState(isSaving = false, message = current.message)
                    } else {
                        _state.update { it.copy(note = note, isLoading = false) }
                    }
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _state.update { it.copy(titleInput = value, message = null) }
    }

    fun updateContent(value: String) {
        _state.update { it.copy(contentInput = value, message = null) }
    }

    fun updateTagText(value: String) {
        _state.update { it.copy(tagTextInput = value, message = null) }
    }

    fun saveTextFields() {
        val current = _state.value
        val note = current.note ?: return
        if (current.isSaving || !current.isDirty) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            noteUseCases.updateNote(
                id = note.id,
                title = current.titleInput,
                content = current.contentInput,
                type = note.type,
                color = note.color,
                tagText = current.tagTextInput,
            )
            val updated = noteUseCases.getNote(note.id)
            if (updated != null) {
                _state.value = updated.toState(isSaving = false, message = "已保存")
            } else {
                _state.update { it.copy(isSaving = false, message = "便签已不存在") }
            }
        }
    }

    fun changeType(type: NoteType) {
        val current = _state.value
        val note = current.note ?: return
        if (current.isSaving || note.type == type) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, message = null) }
            noteUseCases.updateNote(
                id = note.id,
                title = current.titleInput,
                content = current.contentInput,
                type = type,
                color = note.color,
                tagText = current.tagTextInput,
            )
            val updated = noteUseCases.getNote(note.id)
            if (updated != null) {
                _state.value = updated.toState(isSaving = false, message = "类型已更新")
            } else {
                _state.update { it.copy(isSaving = false, message = "便签已不存在") }
            }
        }
    }

    fun toggleDone() {
        val note = _state.value.note ?: return
        if (note.type != NoteType.Todo || note.deleted) return
        viewModelScope.launch {
            noteUseCases.toggleTodoDone(note.id, !note.isDone)
        }
    }

    fun togglePinned() {
        val note = _state.value.note ?: return
        if (note.deleted) return
        viewModelScope.launch {
            noteUseCases.setNotePinned(note.id, !note.pinned)
        }
    }

    fun softDelete() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            noteUseCases.softDeleteNote(note.id)
        }
    }

    fun restore() {
        val note = _state.value.note ?: return
        viewModelScope.launch {
            noteUseCases.restoreDeletedNote(note.id)
        }
    }

    private fun Note.toState(isSaving: Boolean, message: String? = null): NoteDetailState = NoteDetailState(
        note = this,
        titleInput = title,
        contentInput = content,
        tagTextInput = tags.joinToString("、") { it.name },
        isLoading = false,
        isSaving = isSaving,
        message = message,
    )
}
