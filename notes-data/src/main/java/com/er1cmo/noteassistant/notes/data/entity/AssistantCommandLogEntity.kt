package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "assistant_command_log",
    indices = [
        Index(value = ["created_at"]),
        Index(value = ["tool_name"]),
        Index(value = ["status"]),
        Index(value = ["source"]),
    ],
)
data class AssistantCommandLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(name = "conversation_id") val conversationId: String?,
    val source: String,
    @ColumnInfo(name = "user_text") val userText: String?,
    @ColumnInfo(name = "recognized_text") val recognizedText: String?,
    @ColumnInfo(name = "normalized_intent") val normalizedIntent: String?,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    @ColumnInfo(name = "confirmation_status") val confirmationStatus: String,
    @ColumnInfo(name = "result_json") val resultJson: String?,
    @ColumnInfo(name = "affected_note_ids_json") val affectedNoteIdsJson: String?,
    @ColumnInfo(name = "affected_tag_ids_json") val affectedTagIdsJson: String?,
    val status: String,
    @ColumnInfo(name = "error_code") val errorCode: String?,
    @ColumnInfo(name = "error_message") val errorMessage: String?,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "completed_at") val completedAt: Long?,
)
