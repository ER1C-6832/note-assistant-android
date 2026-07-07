package com.er1cmo.noteassistant.notes.data.mapper

import com.er1cmo.noteassistant.notes.data.entity.AssistantCommandLogEntity
import com.er1cmo.noteassistant.notes.data.entity.NoteRevisionEntity
import com.er1cmo.noteassistant.notes.data.entity.PendingConfirmationEntity
import com.er1cmo.noteassistant.notes.domain.model.command.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.command.CommandErrorCode
import com.er1cmo.noteassistant.notes.domain.model.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.model.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.model.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.model.command.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.command.PendingConfirmation
import com.er1cmo.noteassistant.notes.domain.model.command.RiskLevel
import com.er1cmo.noteassistant.notes.domain.model.command.ToolName

fun NoteRevisionEntity.toDomain(): NoteRevision = NoteRevision(
    id = id,
    noteId = noteId,
    titleSnapshot = titleSnapshot,
    contentSnapshot = contentSnapshot,
    tagsSnapshotJson = tagsSnapshotJson,
    typeSnapshot = typeSnapshot,
    isDoneSnapshot = isDoneSnapshot,
    pinnedSnapshot = pinnedSnapshot,
    archivedSnapshot = archivedSnapshot,
    deletedSnapshot = deletedSnapshot,
    colorSnapshot = colorSnapshot,
    createdAt = createdAt,
    source = CommandSource.fromStorageValue(source),
    reason = reason,
    commandLogId = commandLogId,
)

fun NoteRevision.toEntity(): NoteRevisionEntity = NoteRevisionEntity(
    id = id,
    noteId = noteId,
    titleSnapshot = titleSnapshot,
    contentSnapshot = contentSnapshot,
    tagsSnapshotJson = tagsSnapshotJson,
    typeSnapshot = typeSnapshot,
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

fun AssistantCommandLogEntity.toDomain(): AssistantCommandLog = AssistantCommandLog(
    id = id,
    conversationId = conversationId,
    source = CommandSource.fromStorageValue(source),
    userText = userText,
    recognizedText = recognizedText,
    normalizedIntent = normalizedIntent,
    toolName = ToolName.fromStorageValue(toolName),
    argumentsJson = argumentsJson,
    riskLevel = RiskLevel.fromStorageValue(riskLevel),
    confirmationStatus = ConfirmationStatus.fromStorageValue(confirmationStatus),
    resultJson = resultJson,
    affectedNoteIdsJson = affectedNoteIdsJson,
    affectedTagIdsJson = affectedTagIdsJson,
    status = CommandStatus.fromStorageValue(status),
    errorCode = CommandErrorCode.fromStorageValue(errorCode),
    errorMessage = errorMessage,
    createdAt = createdAt,
    completedAt = completedAt,
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

fun PendingConfirmationEntity.toDomain(): PendingConfirmation = PendingConfirmation(
    confirmationId = confirmationId,
    commandLogId = commandLogId,
    toolName = ToolName.fromStorageValue(toolName),
    argumentsJson = argumentsJson,
    riskLevel = RiskLevel.fromStorageValue(riskLevel),
    previewJson = previewJson,
    createdAt = createdAt,
    expiresAt = expiresAt,
    source = CommandSource.fromStorageValue(source),
    status = ConfirmationStatus.fromStorageValue(status),
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
