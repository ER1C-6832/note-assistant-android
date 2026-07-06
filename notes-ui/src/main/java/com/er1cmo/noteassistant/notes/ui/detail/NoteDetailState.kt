package com.er1cmo.noteassistant.notes.ui.detail

import com.er1cmo.noteassistant.notes.domain.model.Note

data class NoteDetailState(
    val note: Note? = null,
    val titleInput: String = "",
    val contentInput: String = "",
    val tagTextInput: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val message: String? = null,
) {
    val isDirty: Boolean
        get() = note != null && (
            titleInput != note.title ||
                contentInput != note.content ||
                tagTextInput != note.tags.joinToString("、") { it.name }
            )
}
