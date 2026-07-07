package com.er1cmo.noteassistant.notes.domain.model

import com.er1cmo.noteassistant.notes.domain.command.CommandErrorCode
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.command.RiskLevel
import com.er1cmo.noteassistant.notes.domain.command.ToolName

data class AssistantCommandLog(
    val id: Long = 0,
    val conversationId: String? = null,
    val source: CommandSource,
    val userText: String? = null,
    val recognizedText: String? = null,
    val normalizedIntent: String? = null,
    val toolName: ToolName,
    val argumentsJson: String,
    val riskLevel: RiskLevel,
    val confirmationStatus: ConfirmationStatus,
    val resultJson: String? = null,
    val affectedNoteIdsJson: String? = null,
    val affectedTagIdsJson: String? = null,
    val status: CommandStatus,
    val errorCode: CommandErrorCode? = null,
    val errorMessage: String? = null,
    val createdAt: Long,
    val completedAt: Long? = null,
)
