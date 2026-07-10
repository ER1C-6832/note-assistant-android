package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import org.json.JSONArray
import org.json.JSONObject

internal const val ASSISTANT_NOTE_REFERENCE_RULE: String =
    "note_id is an internal app id from this tool result. For user-visible speech such as title '1' or keyword '手柄', prefer note_ref/note_title/title/query. Do not invent note_id from spoken numbers, and do not reuse an old note_id if the current user reference points to a different note."

private const val NOTE_CONTENT_LIMIT = 1200
private const val NOTE_SNIPPET_LIMIT = 160
private const val SPOKEN_SNIPPET_LIMIT = 72

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

internal fun Note.toAssistantReadableMessage(prefix: String = "已读取便签"): String {
    val visibleTitle = title.ifBlank { "未命名便签" }
    val body = content.toSingleLine().ifBlank { "（正文为空）" }
    val status = when {
        deleted -> "最近删除"
        archived -> "已归档"
        type == NoteType.Todo && isDone -> "待办·已完成"
        type == NoteType.Todo -> "待办·未完成"
        else -> "普通便签"
    }
    val tagText = tags.takeIf { it.isNotEmpty() }?.joinToString("、") { "#${it.name}" }
    return buildString {
        append(prefix)
        append("《")
        append(visibleTitle)
        append("》；")
        append(status)
        tagText?.let {
            append("；标签 ")
            append(it)
        }
        append("；正文：")
        append(body.take(NOTE_CONTENT_LIMIT))
        if (content.length > NOTE_CONTENT_LIMIT) append("……")
    }
}

internal fun List<Note>.toAssistantReadableListMessage(label: String): String {
    if (isEmpty()) return "$label：没有便签。"
    val items = take(6).mapIndexed { index, note ->
        val title = note.title.ifBlank { "未命名便签" }
        val snippet = note.content.toSingleLine().ifBlank { "正文为空" }.take(SPOKEN_SNIPPET_LIMIT)
        val doneText = when {
            note.type == NoteType.Todo && note.isDone -> "，已完成"
            note.type == NoteType.Todo -> "，未完成"
            else -> ""
        }
        "${index + 1}.《$title》$doneText：$snippet"
    }
    return buildString {
        append(label)
        append("，共 ")
        append(size)
        append(" 条。")
        append(items.joinToString("；"))
        if (size > items.size) append("；其余 ${size - items.size} 条已在结果中返回。")
    }
}

private fun Note.userVisibleReferenceJson(visibleTitle: String): JSONObject = JSONObject()
    .put("preferred_ref", visibleTitle)
    .put("safe_read_arguments", JSONObject().put("note_ref", visibleTitle).put("scope", "all"))
    .put("safe_delete_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_toggle_done_arguments", JSONObject().put("note_ref", visibleTitle).put("done", true).put("auto_convert_to_todo", true))
    .put("safe_convert_type_arguments", JSONObject().put("note_ref", visibleTitle).put("target_type", "todo"))
    .put("safe_append_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_update_title_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_replace_content_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_pin_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_archive_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("safe_open_arguments", JSONObject().put("note_ref", visibleTitle))
    .put("internal_note_id", id)
    .put("internal_id_rule", "Use note_id only if it came from this exact result and pass id_is_internal=true; otherwise use note_ref.")

private fun NoteType.storageValue(): String = when (this) {
    NoteType.Normal -> "normal"
    NoteType.Todo -> "todo"
}

private fun String.toSingleLine(): String = trim()
    .replace(Regex("\\s+"), " ")
