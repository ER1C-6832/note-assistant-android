package com.er1cmo.noteassistant.notes.data.mapper

import com.er1cmo.noteassistant.notes.data.entity.AssistantCommandLogEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteRevisionEntity
import com.er1cmo.noteassistant.notes.data.entity.PendingConfirmationEntity
import com.er1cmo.noteassistant.notes.domain.command.CommandErrorCode
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.command.RiskLevel
import com.er1cmo.noteassistant.notes.domain.command.ToolName
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation

fun NoteRevision.toEntity(): NoteRevisionEntity = NoteRevisionEntity(
    id = id,
    noteId = noteId,
    titleSnapshot = titleSnapshot,
    contentSnapshot = contentSnapshot,
    tagsSnapshotJson = tagsSnapshotJson,
    typeSnapshot = typeSnapshot.storageValue,
    isDoneSnapshot = isDoneSnapshot,
    pinnedSnapshot = pinnedSnapshot,
    archivedSnapshot = archivedSnapshot,
    deletedSnapshot = deletedSnapshot,
    colorSnapshot = colorSnapshot,
    createdAt = createdAt,
    source = source.storageValue,
    reason = reason,
    commandLogId = commandLogId,
)

fun NoteRevisionEntity.toDomain(): NoteRevision = NoteRevision(
    id = id,
    noteId = noteId,
    titleSnapshot = titleSnapshot,
    contentSnapshot = contentSnapshot,
    tagsSnapshotJson = tagsSnapshotJson,
    typeSnapshot = if (typeSnapshot.lowercase() == "todo") NoteType.Todo else NoteType.Normal,
    isDoneSnapshot = isDoneSnapshot,
    pinnedSnapshot = pinnedSnapshot,
    archivedSnapshot = archivedSnapshot,
    deletedSnapshot = deletedSnapshot,
    colorSnapshot = colorSnapshot,
    createdAt = createdAt,
    source = CommandSource.fromStorage(source),
    reason = reason,
    commandLogId = commandLogId,
)

fun AssistantCommandLog.toEntity(): AssistantCommandLogEntity = AssistantCommandLogEntity(
    id = id,
    conversationId = conversationId,
    source = source.storageValue,
    userText = userText,
    recognizedText = recognizedText,
    normalizedIntent = normalizedIntent,
    toolName = toolName.storageValue,
    argumentsJson = argumentsJson,
    riskLevel = riskLevel.storageValue,
    confirmationStatus = confirmationStatus.storageValue,
    resultJson = resultJson,
    affectedNoteIdsJson = affectedNoteIdsJson,
    affectedTagIdsJson = affectedTagIdsJson,
    status = status.storageValue,
    errorCode = errorCode?.storageValue,
    errorMessage = errorMessage,
    createdAt = createdAt,
    completedAt = completedAt,
)

fun AssistantCommandLogEntity.toDomain(): AssistantCommandLog = AssistantCommandLog(
    id = id,
    conversationId = conversationId,
    source = CommandSource.fromStorage(source),
    userText = userText,
    recognizedText = recognizedText,
    normalizedIntent = normalizedIntent,
    toolName = ToolName.fromStorage(toolName),
    argumentsJson = argumentsJson,
    riskLevel = RiskLevel.fromStorage(riskLevel),
    confirmationStatus = ConfirmationStatus.fromStorage(confirmationStatus),
    resultJson = resultJson,
    affectedNoteIdsJson = affectedNoteIdsJson,
    affectedTagIdsJson = affectedTagIdsJson,
    status = CommandStatus.fromStorage(status),
    errorCode = CommandErrorCode.fromStorage(errorCode),
    errorMessage = errorMessage,
    createdAt = createdAt,
    completedAt = completedAt,
)

fun PendingConfirmation.toEntity(): PendingConfirmationEntity = PendingConfirmationEntity(
    confirmationId = confirmationId,
    commandLogId = commandLogId,
    toolName = toolName.storageValue,
    argumentsJson = argumentsJson,
    riskLevel = riskLevel.storageValue,
    previewJson = previewJson,
    createdAt = createdAt,
    expiresAt = expiresAt,
    source = source.storageValue,
    status = status.storageValue,
)

fun PendingConfirmationEntity.toDomain(): PendingConfirmation = PendingConfirmation(
    confirmationId = confirmationId,
    commandLogId = commandLogId,
    toolName = ToolName.fromStorage(toolName),
    argumentsJson = argumentsJson,
    riskLevel = RiskLevel.fromStorage(riskLevel),
    previewJson = previewJson,
    createdAt = createdAt,
    expiresAt = expiresAt,
    source = CommandSource.fromStorage(source),
    status = ConfirmationStatus.fromStorage(status),
)

private val NoteType.storageValue: String
    get() = when (this) {
        NoteType.Normal -> "normal"
        NoteType.Todo -> "todo"
    }
