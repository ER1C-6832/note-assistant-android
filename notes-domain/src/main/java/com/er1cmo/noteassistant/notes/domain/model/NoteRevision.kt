package com.er1cmo.noteassistant.notes.domain.model

import com.er1cmo.noteassistant.notes.domain.command.CommandSource

data class NoteRevision(
    val id: Long = 0,
    val noteId: Long,
    val titleSnapshot: String,
    val contentSnapshot: String,
    val tagsSnapshotJson: String,
    val typeSnapshot: NoteType,
    val isDoneSnapshot: Boolean,
    val pinnedSnapshot: Boolean,
    val archivedSnapshot: Boolean,
    val deletedSnapshot: Boolean,
    val colorSnapshot: String?,
    val createdAt: Long,
    val source: CommandSource,
    val reason: String? = null,
    val commandLogId: Long? = null,
)
