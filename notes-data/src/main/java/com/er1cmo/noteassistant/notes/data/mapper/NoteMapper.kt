package com.er1cmo.noteassistant.notes.data.mapper

import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteEditSource
import com.er1cmo.noteassistant.notes.domain.model.NoteType

fun NoteEntity.toDomain(): Note = Note(
    id = id,
    title = title,
    content = content,
    type = when (type.lowercase()) {
        "todo" -> NoteType.Todo
        else -> NoteType.Normal
    },
    isDone = isDone,
    doneAt = doneAt,
    pinned = pinned,
    archived = archived,
    deleted = deleted,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
    lastEditedSource = when (lastEditedSource.lowercase()) {
        "voice" -> NoteEditSource.Voice
        "import" -> NoteEditSource.Import
        "system" -> NoteEditSource.System
        else -> NoteEditSource.Manual
    },
)
