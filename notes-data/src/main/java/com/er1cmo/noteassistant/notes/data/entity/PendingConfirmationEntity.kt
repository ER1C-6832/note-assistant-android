package com.er1cmo.noteassistant.notes.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "pending_confirmations",
    indices = [Index("command_log_id"), Index("expires_at"), Index("status")],
)
data class PendingConfirmationEntity(
    @PrimaryKey @ColumnInfo(name = "confirmation_id") val confirmationId: String,
    @ColumnInfo(name = "command_log_id") val commandLogId: Long,
    @ColumnInfo(name = "tool_name") val toolName: String,
    @ColumnInfo(name = "arguments_json") val argumentsJson: String,
    @ColumnInfo(name = "risk_level") val riskLevel: String,
    @ColumnInfo(name = "preview_json") val previewJson: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
    @ColumnInfo(name = "expires_at") val expiresAt: Long,
    val source: String,
    val status: String,
)
