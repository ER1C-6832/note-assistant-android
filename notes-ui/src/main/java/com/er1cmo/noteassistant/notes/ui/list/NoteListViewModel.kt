package com.er1cmo.noteassistant.notes.ui.list

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.notes.domain.model.NoteType
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
        noteUseCases.listTags(),
    ) { notes, deletedNotes, tags ->
        NoteListState(
            notes = notes,
            deletedNotes = deletedNotes,
            tags = tags,
            isLoading = false,
        )
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
            noteUseCases.toggleTodoDone(noteId, done)
        }
    }

    fun setPinned(noteIds: Set<Long>, pinned: Boolean) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteUseCases.setNotePinned(it, pinned) }
        }
    }

    fun setTodoDone(noteIds: Set<Long>, done: Boolean) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteId ->
                val note = noteUseCases.getNote(noteId)
                if (note?.type == NoteType.Todo && !note.deleted) {
                    noteUseCases.toggleTodoDone(noteId, done)
                }
            }
        }
    }

    fun softDelete(noteIds: Set<Long>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteUseCases.softDeleteNote(it) }
        }
    }

    fun restoreDeleted(noteIds: Set<Long>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteUseCases.restoreDeletedNote(it) }
        }
    }

    fun permanentlyDelete(noteIds: Set<Long>) {
        if (noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteUseCases.permanentlyDeleteNote(it) }
        }
    }

    fun addTagToNotes(noteIds: Set<Long>, tagText: String) {
        val newTags = tagText.toTagNames()
        if (noteIds.isEmpty() || newTags.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteId ->
                val note = noteUseCases.getNote(noteId) ?: return@forEach
                if (note.deleted) return@forEach
                val mergedTags = (note.tags.map { it.name } + newTags)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase() }
                    .joinToString("、")
                noteUseCases.updateNote(
                    id = note.id,
                    title = note.title,
                    content = note.content,
                    type = note.type,
                    color = note.color,
                    tagText = mergedTags,
                )
            }
        }
    }

    fun createTag(name: String) {
        viewModelScope.launch {
            noteUseCases.createTag(name)
        }
    }

    fun renameTag(id: Long, name: String) {
        viewModelScope.launch {
            noteUseCases.renameTag(id, name)
        }
    }

    fun deleteTag(id: Long) {
        viewModelScope.launch {
            noteUseCases.deleteTag(id)
        }
    }

    private fun String.toTagNames(): List<String> = trim()
        .split(Regex("[\\s,，、#]+"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .filterNot { it.lowercase() in setOf("待办", "todo", "普通", "normal") }
        .distinctBy { it.lowercase() }
}
