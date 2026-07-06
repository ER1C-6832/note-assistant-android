package com.er1cmo.noteassistant.notes.data.mapper

import com.er1cmo.noteassistant.notes.data.entity.NoteEntity
import com.er1cmo.noteassistant.notes.data.entity.TagEntity
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteEditSource
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.Tag

fun NoteEntity.toDomain(formalTags: List<TagEntity> = emptyList()): Note = Note(
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
    tags = if (formalTags.isNotEmpty()) {
        formalTags.map { it.toDomain() }
    } else {
        tagText.toDomainTags(createdAt = createdAt, updatedAt = updatedAt)
    },
)

fun TagEntity.toDomain(): Tag = Tag(
    id = id,
    name = name,
    normalizedName = normalizedName,
    color = color,
    createdAt = createdAt,
    updatedAt = updatedAt,
)

private fun String.toDomainTags(createdAt: Long, updatedAt: Long): List<Tag> = splitTags()
    .mapIndexed { index, name ->
        Tag(
            id = -(index + 1L),
            name = name,
            normalizedName = name.normalizedTagName(),
            color = null,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )
    }

internal fun String.splitTags(): List<String> = trim()
    .split(Regex("[\\s,，、#]+"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
    .filterNot { it.isReservedNoteTypeTag() }
    .distinctBy { it.normalizedTagName() }

internal fun String.cleanedTagName(): String = trim().removePrefix("#").trim()

internal fun String.normalizedTagName(): String = cleanedTagName().lowercase()

private fun String.isReservedNoteTypeTag(): Boolean = when (normalizedTagName()) {
    "待办", "todo", "普通", "normal" -> true
    else -> false
}
