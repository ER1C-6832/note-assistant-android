package com.er1cmo.noteassistant.notes.ui.editor

import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.ui.components.NoteColorPalette

data class NoteEditorState(
    val noteId: Long? = null,
    val title: String = "",
    val content: String = "",
    val tagText: String = "",
    val type: NoteType = NoteType.Normal,
    val color: String = NoteColorPalette.default.hex,
    val pinned: Boolean = false,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)
