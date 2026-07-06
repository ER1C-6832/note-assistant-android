package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity

@Entity(
    tableName = "note_tag_cross_ref",
    primaryKeys = ["note_id", "tag_id"],
)
data class NoteTagCrossRefEntity(
    @ColumnInfo(name = "note_id") val noteId: Long,
    @ColumnInfo(name = "tag_id") val tagId: Long,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
