package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val type: String = "normal",
    @ColumnInfo(name = "content_format") val contentFormat: String = "plain",
    @ColumnInfo(name = "is_done") val isDone: Boolean = false,
    @ColumnInfo(name = "done_at") val doneAt: Long? = null,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    @ColumnInfo(name = "archived_at") val archivedAt: Long? = null,
    @ColumnInfo(name = "deleted_at") val deletedAt: Long? = null,
    val color: String? = null,
    @ColumnInfo(name = "reminder_at") val reminderAt: Long? = null,
    @ColumnInfo(name = "sort_order") val sortOrder: Long? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
    @ColumnInfo(name = "last_edited_source") val lastEditedSource: String = "manual",
    @ColumnInfo(name = "source_conversation_id") val sourceConversationId: String? = null,
)
