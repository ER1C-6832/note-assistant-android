package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import org.json.JSONArray
import org.json.JSONObject

internal fun List<Note>.toNoteListResultJson(): String = JSONObject()
    .put("count", size)
    .put("results", JSONArray().also { array -> forEach { array.put(it.toNoteSummaryJson()) } })
    .toString()

internal fun Note.toNoteSummaryJson(): JSONObject = JSONObject()
    .put("note_id", id)
    .put("title", title)
    .put("snippet", content.take(120))
    .put("type", type.toStorageValue())
    .put("done", isDone)
    .put("pinned", pinned)
    .put("archived", archived)
    .put("deleted", deleted)
    .put("updated_at", updatedAt)
    .put("tags", JSONArray(tags.map { it.name }))

internal fun NoteType.toStorageValue(): String = when (this) {
    NoteType.Normal -> "normal"
    NoteType.Todo -> "todo"
}
