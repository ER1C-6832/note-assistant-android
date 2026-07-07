package com.er1cmo.noteassistant.notes.domain.model.command

data class PendingConfirmation(
    val confirmationId: String,
    val commandLogId: Long,
    val toolName: ToolName,
    val argumentsJson: String,
    val riskLevel: RiskLevel = RiskLevel.High,
    val previewJson: String,
    val createdAt: Long,
    val expiresAt: Long,
    val source: CommandSource,
    val status: ConfirmationStatus = ConfirmationStatus.Pending,
)
