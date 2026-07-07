package com.er1cmo.noteassistant.notes.ui.list

import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.Tag

data class NoteListState(
    val notes: List<Note> = emptyList(),
    val deletedNotes: List<Note> = emptyList(),
    val archivedNotes: List<Note> = emptyList(),
    val tags: List<Tag> = emptyList(),
    val homeBackgroundColor: String = SettingsRepository.DEFAULT_HOME_BACKGROUND,
    val tagDrawerBackgroundColor: String = SettingsRepository.DEFAULT_TAG_DRAWER_BACKGROUND,
    val isLoading: Boolean = true,
)
