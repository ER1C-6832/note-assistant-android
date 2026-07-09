package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import org.json.JSONArray
import org.json.JSONObject

internal const val ASSISTANT_NOTE_REFERENCE_RULE: String =
    "note_id is an internal app id from this tool result. For user-visible speech such as title '1', prefer note_ref/note_title/title/query and do not invent note_id from spoken numbers."

private const val NOTE_CONTENT_LIMIT = 1200
private const val NOTE_SNIPPET_LIMIT = 160

internal fun List<Note>.toAssistantNoteResultsJsonArray(): JSONArray = JSONArray().also { array ->
    forEach { note -> array.put(note.toAssistantNoteResultJson()) }
}

internal fun Note.toAssistantNoteResultJson(): JSONObject {
    val visibleTitle = title.ifBlank { "未命名便签" }
    val contentPreview = content.trim().take(NOTE_CONTENT_LIMIT)
    return JSONObject()
        .put("note_id", id)
        .put("note_ref", visibleTitle)
        .put("user_visible_title", visibleTitle)
        .put("title", title)
        .put("content", contentPreview)
        .put("content_truncated", content.length > NOTE_CONTENT_LIMIT)
        .put("snippet", content.trim().take(NOTE_SNIPPET_LIMIT))
        .put("tags", JSONArray(tags.map { it.name }))
        .put("tag_names", JSONArray(tags.map { it.name }))
        .put("type", type.storageValue())
        .put("done", isDone)
        .put("done_at", doneAt ?: JSONObject.NULL)
        .put("pinned", pinned)
        .put("archived", archived)
        .put("deleted", deleted)
        .put("color", color ?: JSONObject.NULL)
        .put("created_at", createdAt)
        .put("updated_at", updatedAt)
        .put("assistant_reference", userVisibleReferenceJson(visibleTitle))
}

internal fun JSONObject.putAssistantNoteReferenceRule(): JSONObject =
    put("assistant_note_reference_rule", ASSISTANT_NOTE_REFERENCE_RULE)

private fun Note.userVisibleReferenceJson(visibleTitle: String): JSONObject = JSONObject()
    .put("preferred_ref", visibleTitle)
    .put("safe_read_arguments", JSONObject().put("note_ref", visibleTitle).put("scope", "all"))
    .put("safe_delete_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("internal_note_id", id)
    .put("internal_id_rule", "Use note_id only if it came from this exact result, not from a spoken title or number.")

private fun NoteType.storageValue(): String = when (this) {
    NoteType.Normal -> "normal"
    NoteType.Todo -> "todo"
}
