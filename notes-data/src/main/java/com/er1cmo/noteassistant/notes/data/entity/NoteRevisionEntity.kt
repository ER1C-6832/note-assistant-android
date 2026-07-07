package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "note_revisions",
    indices = [
        Index(value = ["note_id"]),
        Index(value = ["command_log_id"]),
        Index(value = ["created_at"]),
    ],
)
data class NoteRevisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "title_snapshot") val titleSnapshot: String,
    @ColumnInfo(name = "content_snapshot") val contentSnapshot: String,
    @ColumnInfo(name = "tags_snapshot_json") val tagsSnapshotJson: String,
    @ColumnInfo(name = "type_snapshot") val typeSnapshot: String,
    @ColumnInfo(name = "is_done_snapshot") val isDoneSnapshot: Boolean,
    @ColumnInfo(name = "pinned_snapshot") val pinnedSnapshot: Boolean,
    @ColumnInfo(name = "archived_snapshot") val archivedSnapshot: Boolean,
    @ColumnInfo(name = "deleted_snapshot") val deletedSnapshot: Boolean,
    @ColumnInfo(name = "color_snapshot") val colorSnapshot: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    val source: String,
    val reason: String?,
    @ColumnInfo(name = "command_log_id") val commandLogId: Long?,
)
