package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assistant_command_log",
    indices = [Index("created_at"), Index("tool_name"), Index("status")],
)
data class AssistantCommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String? = null,
    val source: String,
    @ColumnInfo(name = "user_text") val userText: String? = null,
    @ColumnInfo(name = "recognized_text") val recognizedText: String? = null,
    @ColumnInfo(name = "normalized_intent") val normalizedIntent: String? = null,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    @ColumnInfo(name = "confirmation_status") val confirmationStatus: String,
    @ColumnInfo(name = "result_json") val resultJson: String? = null,
    @ColumnInfo(name = "affected_note_ids_json") val affectedNoteIdsJson: String? = null,
    @ColumnInfo(name = "affected_tag_ids_json") val affectedTagIdsJson: String? = null,
    val status: String,
    @ColumnInfo(name = "error_code") val errorCode: String? = null,
    @ColumnInfo(name = "error_message") val errorMessage: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long? = null,
)
