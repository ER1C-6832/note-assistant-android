package com.er1cmo.noteassistant.notes.domain.model

data class Note(
    val id: Long,
    val title: String,
    val content: String,
    val type: NoteType,
    val isDone: Boolean,
    val doneAt: Long?,
    val pinned: Boolean,
    val archived: Boolean,
    val deleted: Boolean,
    val color: String?,
    val createdAt: Long,
    val updatedAt: Long,
    val lastEditedSource: NoteEditSource,
    val tags: List<Tag> = emptyList(),
)
