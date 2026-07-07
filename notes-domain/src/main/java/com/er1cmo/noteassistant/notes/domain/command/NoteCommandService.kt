package com.er1cmo.noteassistant.notes.domain.command

import com.er1cmo.noteassistant.core.common.time.TimeProvider
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import javax.inject.Inject

class NoteCommandService @Inject constructor(
    private val noteUseCases: NoteUseCases,
    private val commandTraceRepository: CommandTraceRepository,
    private val riskPolicy: NoteRiskPolicy,
    private val timeProvider: TimeProvider,
) {
    suspend fun execute(
        toolName: String,
        argumentsJson: String,
        source: CommandSource = CommandSource.LocalToolSimulator,
    ): CommandResult {
        commandTraceRepository.markExpiredPendingConfirmations(timeProvider.nowMillis())
        val tool = ToolName.fromStorage(toolName)
        val arguments = parseArguments(argumentsJson).getOrElse { error ->
            return writeFailedLog(
                tool = tool,
                argumentsJson = argumentsJson,
                source = source,
                riskLevel = RiskLevel.High,
                message = "参数不是有效 JSON：${error.message ?: "解析失败"}",
                errorCode = CommandErrorCode.InvalidJson,
            )
        }

        return runCatching {
            when (tool) {
                ToolName.NotesCreate -> createNote(arguments, argumentsJson, source)
                ToolName.NotesSearch -> searchNotes(arguments, argumentsJson, source)
                ToolName.NotesListRecent -> listRecent(arguments, argumentsJson, source)
                ToolName.NotesAppend -> appendNote(arguments, argumentsJson, source)
                ToolName.NotesUpdateTitle -> updateTitle(arguments, argumentsJson, source)
                ToolName.NotesReplaceContent -> replaceContent(arguments, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.NotesToggleDone -> toggleDone(arguments, argumentsJson, source)
                ToolName.NotesPin -> setPinned(arguments, argumentsJson, source)
                ToolName.NotesArchive -> setArchived(arguments, argumentsJson, source)
                ToolName.NotesDelete -> deleteNotes(arguments, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.NotesRestoreRevision -> restoreRevision(arguments, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.TagsBind -> bindTags(arguments, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.TagsDelete -> deleteTag(arguments, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                else -> unsupported(tool, argumentsJson, source)
            }
        }.getOrElse { error ->
            writeFailedLog(
                tool = tool,
                argumentsJson = argumentsJson,
                source = source,
                riskLevel = RiskLevel.High,
                message = "命令执行失败：${error.message ?: "未知错误"}",
                errorCode = CommandErrorCode.StorageError,
            )
        }
    }

    suspend fun confirmPendingCommand(confirmationId: String): CommandResult {
        commandTraceRepository.markExpiredPendingConfirmations(timeProvider.nowMillis())
        val pending = commandTraceRepository.getPendingConfirmation(confirmationId)
            ?: return CommandResult.failure(
                message = "没有找到待确认操作",
                riskLevel = RiskLevel.High,
                errorCode = CommandErrorCode.NotFound,
            )
        if (pending.status != ConfirmationStatus.Pending) {
            return CommandResult.failure(
                message = "待确认操作当前状态为 ${pending.status.storageValue}，不能再次确认",
                riskLevel = RiskLevel.High,
                commandLogId = pending.commandLogId,
                errorCode = when (pending.status) {
                    ConfirmationStatus.Expired -> CommandErrorCode.ConfirmationExpired
                    ConfirmationStatus.Rejected -> CommandErrorCode.ConfirmationRejected
                    else -> CommandErrorCode.Conflict
                },
            )
        }
        val now = timeProvider.nowMillis()
        if (pending.expiresAt <= now) {
            val expired = pending.copy(status = ConfirmationStatus.Expired)
            commandTraceRepository.updatePendingConfirmation(expired)
            finishLog(
                logId = pending.commandLogId,
                status = CommandStatus.Blocked,
                errorCode = CommandErrorCode.ConfirmationExpired,
                errorMessage = "确认已过期，请重新发起命令",
                confirmationStatus = ConfirmationStatus.Expired,
            )
            return CommandResult.failure(
                message = "确认已过期，请重新发起命令",
                riskLevel = RiskLevel.High,
                commandLogId = pending.commandLogId,
                errorCode = CommandErrorCode.ConfirmationExpired,
            )
        }

        val args = parseArguments(pending.argumentsJson).getOrElse {
            finishLog(
                logId = pending.commandLogId,
                status = CommandStatus.Failed,
                errorCode = CommandErrorCode.InvalidJson,
                errorMessage = "待确认参数不是有效 JSON",
                confirmationStatus = ConfirmationStatus.Confirmed,
            )
            commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Confirmed))
            return CommandResult.failure(
                message = "待确认参数不是有效 JSON",
                riskLevel = RiskLevel.High,
                commandLogId = pending.commandLogId,
                errorCode = CommandErrorCode.InvalidJson,
            )
        }

        commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Confirmed))
        return when (pending.toolName) {
            ToolName.NotesReplaceContent -> replaceContent(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true, pendingPreviewJson = pending.previewJson)
            ToolName.NotesDelete -> deleteNotes(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            ToolName.NotesRestoreRevision -> restoreRevision(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true, pendingPreviewJson = pending.previewJson)
            ToolName.TagsBind -> bindTags(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            ToolName.TagsDelete -> deleteTag(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true, pendingPreviewJson = pending.previewJson)
            ToolName.NotesPin -> setPinned(args.put("confirmed", true), pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            ToolName.NotesArchive -> setArchived(args.put("confirmed", true), pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            else -> {
                finishLog(
                    logId = pending.commandLogId,
                    status = CommandStatus.Failed,
                    errorCode = CommandErrorCode.UnsupportedTool,
                    errorMessage = "这个待确认工具暂不支持确认执行：${pending.toolName.storageValue}",
                    confirmationStatus = ConfirmationStatus.Confirmed,
                )
                CommandResult.failure(
                    message = "这个待确认工具暂不支持确认执行：${pending.toolName.storageValue}",
                    riskLevel = RiskLevel.High,
                    commandLogId = pending.commandLogId,
                    errorCode = CommandErrorCode.UnsupportedTool,
                )
            }
        }
    }

    suspend fun rejectPendingCommand(confirmationId: String): CommandResult {
        val pending = commandTraceRepository.getPendingConfirmation(confirmationId)
            ?: return CommandResult.failure(
                message = "没有找到待确认操作",
                riskLevel = RiskLevel.High,
                errorCode = CommandErrorCode.NotFound,
            )
        commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Rejected))
        finishLog(
            logId = pending.commandLogId,
            status = CommandStatus.Failed,
            errorCode = CommandErrorCode.ConfirmationRejected,
            errorMessage = "用户已拒绝执行",
            confirmationStatus = ConfirmationStatus.Rejected,
        )
        return CommandResult.failure(
            message = "已拒绝执行，未修改任何便签",
            riskLevel = RiskLevel.High,
            commandLogId = pending.commandLogId,
            errorCode = CommandErrorCode.ConfirmationRejected,
        )
    }

    private suspend fun createNote(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val title = args.optString("title", "").trim()
        val content = args.optString("content", "").trim()
        val type = args.optString("type", "normal").toNoteTypeOrNull()
            ?: return validationFailed(ToolName.NotesCreate, rawJson, source, "type 只能是 normal 或 todo")
        val color = args.optString("color", "").trim().ifBlank { null }
        val tagText = args.tagsText()
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesCreate, source = source, affectedNoteCount = 1))
        val logId = insertInitialLog(ToolName.NotesCreate, rawJson, source, risk)
        val noteId = noteUseCases.createNote(title = title, content = content, type = type, color = color, tagText = tagText)
        val resultJson = JSONObject()
            .put("note_id", noteId)
            .put("title", title)
            .put("type", type.storageValue())
            .toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(noteId))
        return CommandResult.success(
            message = "已创建便签：${title.ifBlank { "未命名便签" }}",
            riskLevel = risk,
            commandLogId = logId,
            affectedNoteIds = listOf(noteId),
            resultJson = resultJson,
        )
    }

    private suspend fun searchNotes(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val query = args.optString("query", "")
        val limit = args.optInt("limit", 10).coerceIn(1, 50)
        val includeDeleted = args.optBoolean("include_deleted", false)
        val includeArchived = args.optBoolean("include_archived", false)
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesSearch, source = source))
        val logId = insertInitialLog(ToolName.NotesSearch, rawJson, source, risk)
        val notes = buildList {
            addAll(noteUseCases.listNotes().first())
            if (includeArchived) addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted) addAll(noteUseCases.listDeletedNotes().first())
        }
        val results = noteUseCases.searchNotes(notes = notes, query = query, limit = limit)
        val resultJson = JSONObject()
            .put("query", query)
            .put("count", results.size)
            .put("results", JSONArray().also { array ->
                results.forEach { result ->
                    array.put(
                        JSONObject()
                            .put("note_id", result.note.id)
                            .put("title", result.note.title)
                            .put("snippet", result.note.content.take(80))
                            .put("tags", JSONArray(result.note.tags.map { it.name }))
                            .put("score", result.score),
                    )
                }
            })
            .toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = results.map { it.note.id })
        return CommandResult.success(
            message = "搜索完成，找到 ${results.size} 条便签",
            riskLevel = risk,
            commandLogId = logId,
            affectedNoteIds = results.map { it.note.id },
            resultJson = resultJson,
        )
    }

    private suspend fun listRecent(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val limit = args.optInt("limit", 10).coerceIn(1, 50)
        val includeDeleted = args.optBoolean("include_deleted", false)
        val includeArchived = args.optBoolean("include_archived", false)
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesListRecent, source = source))
        val logId = insertInitialLog(ToolName.NotesListRecent, rawJson, source, risk)
        val notes = buildList {
            addAll(noteUseCases.listNotes().first())
            if (includeArchived) addAll(noteUseCases.listArchivedNotes().first())
            if (includeDeleted) addAll(noteUseCases.listDeletedNotes().first())
        }.sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id }).take(limit)
        val resultJson = notes.toCommandResultJson()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = notes.map { it.id })
        return CommandResult.success(
            message = "已列出最近 ${notes.size} 条便签",
            riskLevel = risk,
            commandLogId = logId,
            affectedNoteIds = notes.map { it.id },
            resultJson = resultJson,
        )
    }

    private suspend fun appendNote(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val noteId = args.optLong("note_id", 0L)
        val appendContent = args.optString("content", "").trim()
        if (noteId <= 0) return validationFailed(ToolName.NotesAppend, rawJson, source, "缺少 note_id")
        if (appendContent.isBlank()) return validationFailed(ToolName.NotesAppend, rawJson, source, "追加内容不能为空")
        val note = noteUseCases.getNote(noteId) ?: return notFound(ToolName.NotesAppend, rawJson, source, noteId)
        if (note.deleted) return validationFailed(ToolName.NotesAppend, rawJson, source, "最近删除中的便签不能追加内容")
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesAppend, source = source, affectedNoteCount = 1))
        val logId = insertInitialLog(ToolName.NotesAppend, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "append_content", listOf(note))
        val nextContent = if (note.content.isBlank()) appendContent else note.content.trimEnd() + "\n" + appendContent
        noteUseCases.updateNote(note.id, note.title, nextContent, note.type, note.color, note.tags.toTagText())
        val resultJson = JSONObject().put("note_id", note.id).put("content_length", nextContent.length).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id))
        return CommandResult.success("已追加内容", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
    }

    private suspend fun updateTitle(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val noteId = args.optLong("note_id", 0L)
        val title = args.optString("title", "").trim()
        if (noteId <= 0) return validationFailed(ToolName.NotesUpdateTitle, rawJson, source, "缺少 note_id")
        val note = noteUseCases.getNote(noteId) ?: return notFound(ToolName.NotesUpdateTitle, rawJson, source, noteId)
        if (note.deleted) return validationFailed(ToolName.NotesUpdateTitle, rawJson, source, "最近删除中的便签不能改标题")
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesUpdateTitle, source = source, affectedNoteCount = 1))
        val logId = insertInitialLog(ToolName.NotesUpdateTitle, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "update_title", listOf(note))
        noteUseCases.updateNote(note.id, title, note.content, note.type, note.color, note.tags.toTagText())
        val resultJson = JSONObject().put("note_id", note.id).put("title", title).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id))
        return CommandResult.success("已更新标题", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
    }

    private suspend fun replaceContent(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
        pendingPreviewJson: String? = null,
    ): CommandResult {
        val noteId = args.optLong("note_id", 0L)
        val newContent = args.optString("content", "")
        if (noteId <= 0) return validationFailed(ToolName.NotesReplaceContent, rawJson, source, "缺少 note_id")
        val note = noteUseCases.getNote(noteId) ?: return notFound(ToolName.NotesReplaceContent, rawJson, source, noteId)
        if (note.deleted) return validationFailed(ToolName.NotesReplaceContent, rawJson, source, "最近删除中的便签不能覆盖正文")
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesReplaceContent, source = source, affectedNoteCount = 1))
        val confirmed = args.optBoolean("confirmed", false) || fromConfirmation
        if (!confirmed) {
            val previewJson = JSONObject()
                .put("summary", "将覆盖便签正文，需要确认")
                .put("note_id", note.id)
                .put("title", note.title)
                .put("old_content_preview", note.content.take(80))
                .put("new_content_preview", newContent.take(80))
                .put("expected_updated_at", note.updatedAt)
                .put("affected_note_ids", listOf(note.id).toJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesReplaceContent, rawJson, source, risk, previewJson, listOf(note.id), emptyList(), "将覆盖《${note.title.ifBlank { "未命名便签" }}》正文，是否确认？")
        }
        if (fromConfirmation && pendingPreviewJson != null) {
            val expectedUpdatedAt = runCatching { JSONObject(pendingPreviewJson).optLong("expected_updated_at", note.updatedAt) }.getOrDefault(note.updatedAt)
            if (expectedUpdatedAt != note.updatedAt) {
                existingLogId?.let {
                    finishLog(
                        logId = it,
                        status = CommandStatus.Blocked,
                        errorCode = CommandErrorCode.Conflict,
                        errorMessage = "便签在确认前已发生变化，请重新发起命令",
                        confirmationStatus = ConfirmationStatus.Confirmed,
                    )
                }
                return CommandResult.failure(
                    message = "便签在确认前已发生变化，请重新发起命令",
                    riskLevel = RiskLevel.High,
                    commandLogId = existingLogId,
                    errorCode = CommandErrorCode.Conflict,
                )
            }
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesReplaceContent, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "replace_content", listOf(note))
        noteUseCases.updateNote(note.id, note.title, newContent, note.type, note.color, note.tags.toTagText())
        val resultJson = JSONObject().put("note_id", note.id).put("content_length", newContent.length).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id), confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success("已覆盖正文", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
    }

    private suspend fun toggleDone(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val noteId = args.optLong("note_id", 0L)
        if (noteId <= 0) return validationFailed(ToolName.NotesToggleDone, rawJson, source, "缺少 note_id")
        val note = noteUseCases.getNote(noteId) ?: return notFound(ToolName.NotesToggleDone, rawJson, source, noteId)
        if (note.deleted) return validationFailed(ToolName.NotesToggleDone, rawJson, source, "最近删除中的便签不能标记完成")
        if (note.type != NoteType.Todo) return validationFailed(ToolName.NotesToggleDone, rawJson, source, "只有待办便签可以标记完成")
        val done = if (args.has("done")) args.optBoolean("done", false) else !note.isDone
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesToggleDone, source = source, affectedNoteCount = 1))
        val logId = insertInitialLog(ToolName.NotesToggleDone, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "toggle_done", listOf(note))
        noteUseCases.toggleTodoDone(note.id, done)
        val resultJson = JSONObject().put("note_id", note.id).put("done", done).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id))
        return CommandResult.success(if (done) "已标记完成" else "已取消完成", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
    }

    private suspend fun setPinned(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long? = null,
        fromConfirmation: Boolean = false,
    ): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesPin, rawJson, source, "缺少 note_id 或 note_ids")
        val pinned = args.optBoolean("pinned", true)
        val riskInput = CommandRiskInput(toolName = ToolName.NotesPin, source = source, affectedNoteCount = noteIds.size)
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将批量修改 ${noteIds.size} 条便签的置顶状态")
                .put("pinned", pinned)
                .put("affected_note_ids", noteIds.toJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesPin, rawJson, source, risk, previewJson, noteIds, emptyList(), "将批量修改 ${noteIds.size} 条便签的置顶状态")
        }
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesPin, rawJson, source, risk)
        if (notes.size > 1) insertRevisionSnapshots(logId, source, "pin_batch", notes)
        notes.forEach { noteUseCases.setNotePinned(it.id, pinned) }
        val affectedIds = notes.map { it.id }
        val resultJson = JSONObject().put("pinned", pinned).put("affected_note_ids", affectedIds.toJsonArray()).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = affectedIds, confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success(if (pinned) "已置顶 ${affectedIds.size} 条便签" else "已取消置顶 ${affectedIds.size} 条便签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
    }

    private suspend fun setArchived(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long? = null,
        fromConfirmation: Boolean = false,
    ): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesArchive, rawJson, source, "缺少 note_id 或 note_ids")
        val archived = args.optBoolean("archived", true)
        val riskInput = CommandRiskInput(toolName = ToolName.NotesArchive, source = source, affectedNoteCount = noteIds.size)
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将批量修改 ${noteIds.size} 条便签的归档状态")
                .put("archived", archived)
                .put("affected_note_ids", noteIds.toJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesArchive, rawJson, source, risk, previewJson, noteIds, emptyList(), "将批量修改 ${noteIds.size} 条便签的归档状态")
        }
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesArchive, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, if (archived) "archive" else "unarchive", notes)
        notes.forEach { noteUseCases.setNoteArchived(it.id, archived) }
        val affectedIds = notes.map { it.id }
        val resultJson = JSONObject().put("archived", archived).put("affected_note_ids", affectedIds.toJsonArray()).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = affectedIds, confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success(if (archived) "已归档 ${affectedIds.size} 条便签" else "已取消归档 ${affectedIds.size} 条便签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
    }

    private suspend fun deleteNotes(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
    ): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesDelete, rawJson, source, "缺少 note_id 或 note_ids")
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        if (notes.isEmpty()) return validationFailed(ToolName.NotesDelete, rawJson, source, "没有可删除的便签")
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesDelete, source = source, affectedNoteCount = notes.size))
        val confirmed = args.optBoolean("confirmed", false) || fromConfirmation
        if (!confirmed) {
            val previewJson = JSONObject()
                .put("summary", "将删除 ${notes.size} 条便签，需要确认")
                .put("affected_note_ids", notes.map { it.id }.toJsonArray())
                .put("preview", notes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesDelete, rawJson, source, risk, previewJson, notes.map { it.id }, emptyList(), "将删除 ${notes.size} 条便签，是否确认？")
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesDelete, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "delete", notes)
        val affectedIds = mutableListOf<Long>()
        notes.forEach { note ->
            if (noteUseCases.softDeleteNote(note.id)) affectedIds += note.id
        }
        val status = if (affectedIds.size == notes.size) CommandStatus.Success else CommandStatus.PartialSuccess
        val resultJson = JSONObject().put("deleted", true).put("affected_note_ids", affectedIds.toJsonArray()).toString()
        finishLog(logId = logId, status = status, resultJson = resultJson, affectedNoteIds = affectedIds, confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success("已删除 ${affectedIds.size} 条便签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
    }

    private suspend fun restoreRevision(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
        pendingPreviewJson: String? = null,
    ): CommandResult {
        val noteId = args.optLong("note_id", 0L)
        val revisionId = args.optLong("revision_id", 0L)
        if (noteId <= 0) return validationFailed(ToolName.NotesRestoreRevision, rawJson, source, "缺少 note_id")
        if (revisionId <= 0) return validationFailed(ToolName.NotesRestoreRevision, rawJson, source, "缺少 revision_id")
        val note = noteUseCases.getNote(noteId) ?: return notFound(ToolName.NotesRestoreRevision, rawJson, source, noteId)
        val revision = commandTraceRepository.listRevisionsForNote(noteId).firstOrNull { it.id == revisionId }
            ?: return writeFailedLog(
                tool = ToolName.NotesRestoreRevision,
                argumentsJson = rawJson,
                source = source,
                riskLevel = RiskLevel.High,
                message = "没有找到 revision：$revisionId",
                errorCode = CommandErrorCode.NotFound,
            )
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesRestoreRevision, source = source, affectedNoteCount = 1))
        val confirmed = args.optBoolean("confirmed", false) || fromConfirmation
        if (!confirmed) {
            val previewJson = JSONObject()
                .put("summary", "将恢复便签到历史版本，需要确认")
                .put("note_id", note.id)
                .put("revision_id", revision.id)
                .put("title", note.title)
                .put("current_content_preview", note.content.take(80))
                .put("revision_content_preview", revision.contentSnapshot.take(80))
                .put("expected_updated_at", note.updatedAt)
                .put("affected_note_ids", listOf(note.id).toJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesRestoreRevision, rawJson, source, risk, previewJson, listOf(note.id), emptyList(), "将把《${note.title.ifBlank { "未命名便签" }}》恢复到 revision#$revisionId，是否确认？")
        }
        if (fromConfirmation && pendingPreviewJson != null) {
            val expectedUpdatedAt = runCatching { JSONObject(pendingPreviewJson).optLong("expected_updated_at", note.updatedAt) }.getOrDefault(note.updatedAt)
            if (expectedUpdatedAt != note.updatedAt) {
                existingLogId?.let {
                    finishLog(
                        logId = it,
                        status = CommandStatus.Blocked,
                        errorCode = CommandErrorCode.Conflict,
                        errorMessage = "便签在确认前已发生变化，请重新发起恢复",
                        confirmationStatus = ConfirmationStatus.Confirmed,
                    )
                }
                return CommandResult.failure(
                    message = "便签在确认前已发生变化，请重新发起恢复",
                    riskLevel = RiskLevel.High,
                    commandLogId = existingLogId,
                    errorCode = CommandErrorCode.Conflict,
                )
            }
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesRestoreRevision, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "restore_revision_current_state", listOf(note))
        if (note.deleted && !revision.deletedSnapshot) {
            noteUseCases.restoreDeletedNote(note.id)
        }
        val editableNote = noteUseCases.getNote(note.id) ?: note
        if (editableNote.deleted && revision.deletedSnapshot) {
            finishLog(
                logId = logId,
                status = CommandStatus.Blocked,
                errorCode = CommandErrorCode.Conflict,
                errorMessage = "当前便签仍在最近删除中，无法恢复到已删除快照",
                confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired,
            )
            return CommandResult.failure("当前便签仍在最近删除中，无法恢复到已删除快照", risk, CommandErrorCode.Conflict, logId)
        }
        noteUseCases.updateNote(
            id = note.id,
            title = revision.titleSnapshot,
            content = revision.contentSnapshot,
            type = revision.typeSnapshot,
            color = revision.colorSnapshot,
            tagText = revision.tagsTextFromSnapshot(),
        )
        val afterText = noteUseCases.getNote(note.id) ?: editableNote
        if (revision.typeSnapshot == NoteType.Todo && afterText.isDone != revision.isDoneSnapshot) {
            noteUseCases.toggleTodoDone(note.id, revision.isDoneSnapshot)
        }
        val afterDone = noteUseCases.getNote(note.id) ?: afterText
        if (afterDone.pinned != revision.pinnedSnapshot) {
            noteUseCases.setNotePinned(note.id, revision.pinnedSnapshot)
        }
        val afterPinned = noteUseCases.getNote(note.id) ?: afterDone
        if (!afterPinned.deleted && afterPinned.archived != revision.archivedSnapshot) {
            noteUseCases.setNoteArchived(note.id, revision.archivedSnapshot)
        }
        if (revision.deletedSnapshot) {
            noteUseCases.softDeleteNote(note.id)
        }
        val resultJson = JSONObject()
            .put("note_id", note.id)
            .put("revision_id", revision.id)
            .put("restored", true)
            .toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id), confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success("已恢复 revision#$revisionId", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
    }

    private suspend fun bindTags(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
    ): CommandResult {
        val noteIds = args.noteIds()
        val tagNames = args.tagNames()
        if (noteIds.isEmpty()) return validationFailed(ToolName.TagsBind, rawJson, source, "缺少 note_id 或 note_ids")
        if (tagNames.isEmpty()) return validationFailed(ToolName.TagsBind, rawJson, source, "缺少 tags")
        val mode = TagBindMode.fromStorage(args.optString("mode", "add")) ?: TagBindMode.Add
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        if (notes.isEmpty()) return validationFailed(ToolName.TagsBind, rawJson, source, "没有可修改标签的便签")
        val riskInput = CommandRiskInput(toolName = ToolName.TagsBind, source = source, affectedNoteCount = notes.size, tagBindMode = mode)
        val risk = riskPolicy.classify(riskInput)
        val confirmed = args.optBoolean("confirmed", false) || fromConfirmation
        if (riskPolicy.requiresConfirmation(riskInput) && !confirmed) {
            val previewJson = JSONObject()
                .put("summary", "将替换 ${notes.size} 条便签的标签，需要确认")
                .put("mode", mode.storageValue)
                .put("tags", JSONArray(tagNames))
                .put("affected_note_ids", notes.map { it.id }.toJsonArray())
                .put("preview", notes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.TagsBind, rawJson, source, risk, previewJson, notes.map { it.id }, emptyList(), "将替换 ${notes.size} 条便签的标签，是否确认？")
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.TagsBind, rawJson, source, risk)
        if (mode == TagBindMode.Replace) insertRevisionSnapshots(logId, source, "replace_tags", notes)
        val affectedIds = mutableListOf<Long>()
        notes.forEach { note ->
            val nextTags = when (mode) {
                TagBindMode.Add -> (note.tags.map { it.name } + tagNames).distinctTagNames()
                TagBindMode.Remove -> note.tags.map { it.name }.filterNot { existing -> tagNames.any { it.equals(existing, ignoreCase = true) } }
                TagBindMode.Replace -> tagNames.distinctTagNames()
            }
            noteUseCases.updateNote(note.id, note.title, note.content, note.type, note.color, nextTags.joinToString("、"))
            affectedIds += note.id
        }
        val resultJson = JSONObject()
            .put("mode", mode.storageValue)
            .put("tags", JSONArray(tagNames))
            .put("affected_note_ids", affectedIds.toJsonArray())
            .toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = affectedIds, confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success("已${mode.actionLabel()} ${affectedIds.size} 条便签的标签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
    }

    private suspend fun deleteTag(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
        pendingPreviewJson: String? = null,
    ): CommandResult {
        val tagId = args.optLong("tag_id", 0L)
        if (tagId <= 0) return validationFailed(ToolName.TagsDelete, rawJson, source, "缺少 tag_id")
        val tags = noteUseCases.listTags().first()
        val tag = tags.firstOrNull { it.id == tagId } ?: return writeFailedLog(
            tool = ToolName.TagsDelete,
            argumentsJson = rawJson,
            source = source,
            riskLevel = RiskLevel.High,
            message = "没有找到标签：$tagId",
            errorCode = CommandErrorCode.NotFound,
        )
        val linkedNotes = allNotes().filter { note -> note.tags.any { it.id == tag.id || it.normalizedName == tag.normalizedName } }
        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.TagsDelete, source = source, affectedNoteCount = linkedNotes.size, affectedTagCount = 1, linkedNoteCount = linkedNotes.size))
        val confirmed = args.optBoolean("confirmed", false) || fromConfirmation
        if (!confirmed) {
            val previewJson = JSONObject()
                .put("summary", "将删除标签 #${tag.name}，并从 ${linkedNotes.size} 条便签中移除")
                .put("tag_id", tag.id)
                .put("tag_name", tag.name)
                .put("linked_note_count", linkedNotes.size)
                .put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray())
                .put("preview", linkedNotes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.TagsDelete, rawJson, source, risk, previewJson, linkedNotes.map { it.id }, listOf(tag.id), "将删除标签 #${tag.name}，并从 ${linkedNotes.size} 条便签中移除，是否确认？")
        }
        if (fromConfirmation && pendingPreviewJson != null) {
            val expectedCount = runCatching { JSONObject(pendingPreviewJson).optInt("linked_note_count", linkedNotes.size) }.getOrDefault(linkedNotes.size)
            if (expectedCount != linkedNotes.size) {
                existingLogId?.let {
                    finishLog(
                        logId = it,
                        status = CommandStatus.Blocked,
                        errorCode = CommandErrorCode.Conflict,
                        errorMessage = "标签关联便签数量已变化，请重新发起命令",
                        confirmationStatus = ConfirmationStatus.Confirmed,
                    )
                }
                return CommandResult.failure(
                    message = "标签关联便签数量已变化，请重新发起命令",
                    riskLevel = RiskLevel.High,
                    commandLogId = existingLogId,
                    errorCode = CommandErrorCode.Conflict,
                )
            }
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.TagsDelete, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "delete_tag", linkedNotes)
        val deleted = noteUseCases.deleteTag(tag.id)
        val status = if (deleted) CommandStatus.Success else CommandStatus.Failed
        val resultJson = JSONObject()
            .put("tag_id", tag.id)
            .put("tag_name", tag.name)
            .put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray())
            .toString()
        finishLog(
            logId = logId,
            status = status,
            resultJson = resultJson,
            affectedNoteIds = linkedNotes.map { it.id },
            affectedTagIds = listOf(tag.id),
            errorCode = if (deleted) null else CommandErrorCode.StorageError,
            errorMessage = if (deleted) null else "删除标签失败",
            confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired,
        )
        return if (deleted) {
            CommandResult.success("已删除标签 #${tag.name}", risk, logId, affectedNoteIds = linkedNotes.map { it.id }, affectedTagIds = listOf(tag.id), resultJson = resultJson)
        } else {
            CommandResult.failure("删除标签失败", risk, CommandErrorCode.StorageError, logId)
        }
    }

    private suspend fun unsupported(tool: ToolName, rawJson: String, source: CommandSource): CommandResult =
        writeFailedLog(tool, rawJson, source, RiskLevel.High, "暂不支持这个工具：${tool.storageValue}", CommandErrorCode.UnsupportedTool)

    private suspend fun validationFailed(tool: ToolName, rawJson: String, source: CommandSource, message: String): CommandResult =
        writeFailedLog(tool, rawJson, source, RiskLevel.Medium, message, CommandErrorCode.ValidationError)

    private suspend fun notFound(tool: ToolName, rawJson: String, source: CommandSource, noteId: Long): CommandResult =
        writeFailedLog(tool, rawJson, source, RiskLevel.Medium, "没有找到便签：$noteId", CommandErrorCode.NotFound)

    private suspend fun insertInitialLog(tool: ToolName, rawJson: String, source: CommandSource, risk: RiskLevel): Long {
        val now = timeProvider.nowMillis()
        return commandTraceRepository.insertCommandLog(
            AssistantCommandLog(
                source = source,
                toolName = tool,
                argumentsJson = rawJson,
                riskLevel = risk,
                confirmationStatus = ConfirmationStatus.NotRequired,
                status = CommandStatus.Blocked,
                createdAt = now,
            ),
        )
    }

    private suspend fun finishLog(
        logId: Long,
        status: CommandStatus,
        resultJson: String? = null,
        affectedNoteIds: List<Long> = emptyList(),
        affectedTagIds: List<Long> = emptyList(),
        errorCode: CommandErrorCode? = null,
        errorMessage: String? = null,
        confirmationStatus: ConfirmationStatus = ConfirmationStatus.NotRequired,
    ) {
        val existing = commandTraceRepository.getCommandLog(logId) ?: return
        commandTraceRepository.updateCommandLog(
            existing.copy(
                status = status,
                confirmationStatus = confirmationStatus,
                resultJson = resultJson,
                affectedNoteIdsJson = affectedNoteIds.takeIf { it.isNotEmpty() }?.toJsonArrayString(),
                affectedTagIdsJson = affectedTagIds.takeIf { it.isNotEmpty() }?.toJsonArrayString(),
                errorCode = errorCode,
                errorMessage = errorMessage,
                completedAt = timeProvider.nowMillis(),
            ),
        )
    }

    private suspend fun writeFailedLog(
        tool: ToolName,
        argumentsJson: String,
        source: CommandSource,
        riskLevel: RiskLevel,
        message: String,
        errorCode: CommandErrorCode,
    ): CommandResult {
        val logId = insertInitialLog(tool, argumentsJson, source, riskLevel)
        finishLog(logId, CommandStatus.Failed, errorCode = errorCode, errorMessage = message)
        return CommandResult.failure(message = message, riskLevel = riskLevel, errorCode = errorCode, commandLogId = logId)
    }

    private suspend fun createPendingConfirmation(
        tool: ToolName,
        rawJson: String,
        source: CommandSource,
        risk: RiskLevel,
        previewJson: String,
        noteIds: List<Long>,
        tagIds: List<Long>,
        summary: String,
    ): CommandResult {
        val logId = insertInitialLog(tool, rawJson, source, risk)
        val now = timeProvider.nowMillis()
        val confirmationId = UUID.randomUUID().toString()
        commandTraceRepository.insertPendingConfirmation(
            PendingConfirmation(
                confirmationId = confirmationId,
                commandLogId = logId,
                toolName = tool,
                argumentsJson = rawJson,
                riskLevel = RiskLevel.High,
                previewJson = previewJson,
                createdAt = now,
                expiresAt = now + CONFIRMATION_TTL_MILLIS,
                source = source,
                status = ConfirmationStatus.Pending,
            ),
        )
        finishLog(
            logId = logId,
            status = CommandStatus.RequiresConfirmation,
            resultJson = previewJson,
            affectedNoteIds = noteIds,
            affectedTagIds = tagIds,
            errorCode = CommandErrorCode.RequiresConfirmation,
            errorMessage = summary,
            confirmationStatus = ConfirmationStatus.Pending,
        )
        return CommandResult.requiresConfirmation(
            message = summary,
            commandLogId = logId,
            confirmationId = confirmationId,
            affectedNoteIds = noteIds,
            affectedTagIds = tagIds,
            resultJson = previewJson,
        )
    }

    private suspend fun insertRevisionSnapshots(commandLogId: Long, source: CommandSource, reason: String, notes: List<Note>) {
        if (notes.isEmpty()) return
        val now = timeProvider.nowMillis()
        commandTraceRepository.runInTraceTransaction {
            notes.forEach { note ->
                insertRevision(note.toRevision(source = source, reason = reason, commandLogId = commandLogId, createdAt = now))
            }
        }
    }

    private fun Note.toRevision(source: CommandSource, reason: String, commandLogId: Long, createdAt: Long): NoteRevision = NoteRevision(
        noteId = id,
        titleSnapshot = title,
        contentSnapshot = content,
        tagsSnapshotJson = JSONArray(tags.map { it.name }).toString(),
        typeSnapshot = type,
        isDoneSnapshot = isDone,
        pinnedSnapshot = pinned,
        archivedSnapshot = archived,
        deletedSnapshot = deleted,
        colorSnapshot = color,
        createdAt = createdAt,
        source = source,
        reason = reason,
        commandLogId = commandLogId,
    )

    private fun parseArguments(argumentsJson: String): Result<JSONObject> = runCatching {
        if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
    }

    private suspend fun allNotes(): List<Note> = buildList {
        addAll(noteUseCases.listNotes().first())
        addAll(noteUseCases.listArchivedNotes().first())
        addAll(noteUseCases.listDeletedNotes().first())
    }.distinctBy { it.id }

    private fun JSONObject.tagsText(): String {
        val explicitTagText = optString("tagText", optString("tag_text", "")).trim()
        if (explicitTagText.isNotBlank()) return explicitTagText
        return tagNames().joinToString("、")
    }

    private fun JSONObject.tagNames(): List<String> {
        val fromText = optString("tagText", optString("tag_text", "")).trim()
        if (fromText.isNotBlank()) return fromText.split(Regex("[\\s,，、#]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctTagNames()
        val array = optJSONArray("tags") ?: return emptyList()
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.distinctTagNames()
    }

    private fun JSONObject.noteIds(): List<Long> {
        val array = optJSONArray("note_ids")
        if (array != null) {
            return buildList {
                for (index in 0 until array.length()) {
                    val id = array.optLong(index, 0L)
                    if (id > 0) add(id)
                }
            }.distinct()
        }
        val id = optLong("note_id", 0L)
        return if (id > 0) listOf(id) else emptyList()
    }

    private fun String.toNoteTypeOrNull(): NoteType? = when (trim().lowercase()) {
        "normal", "普通" -> NoteType.Normal
        "todo", "待办" -> NoteType.Todo
        else -> null
    }

    private fun NoteType.storageValue(): String = when (this) {
        NoteType.Normal -> "normal"
        NoteType.Todo -> "todo"
    }

    private fun List<Note>.toCommandResultJson(): String = JSONObject()
        .put("count", size)
        .put("results", JSONArray().also { array ->
            forEach { note ->
                array.put(
                    JSONObject()
                        .put("note_id", note.id)
                        .put("title", note.title)
                        .put("snippet", note.content.take(80))
                        .put("tags", JSONArray(note.tags.map { it.name }))
                        .put("updated_at", note.updatedAt),
                )
            }
        })
        .toString()

    private fun List<Note>.toPreviewJsonArray(): JSONArray = JSONArray().also { array ->
        take(8).forEach { note ->
            array.put(
                JSONObject()
                    .put("note_id", note.id)
                    .put("title", note.title)
                    .put("updated_at", note.updatedAt),
            )
        }
    }

    private fun NoteRevision.tagsTextFromSnapshot(): String = runCatching {
        val array = JSONArray(tagsSnapshotJson)
        buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.joinToString("、")
    }.getOrDefault("")

    private fun List<Long>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

    private fun List<Long>.toJsonArrayString(): String = toJsonArray().toString()

    private fun List<Tag>.toTagText(): String = joinToString("、") { it.name }

    private fun List<String>.distinctTagNames(): List<String> = map { it.trim() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }

    private fun TagBindMode.actionLabel(): String = when (this) {
        TagBindMode.Add -> "添加"
        TagBindMode.Remove -> "移除"
        TagBindMode.Replace -> "替换"
    }

    companion object {
        private const val CONFIRMATION_TTL_MILLIS = 10 * 60 * 1000L
    }
}
