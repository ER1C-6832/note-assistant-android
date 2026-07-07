package com.er1cmo.noteassistant.notes.domain.command

import com.er1cmo.noteassistant.core.common.time.TimeProvider
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation
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
                ToolName.NotesToggleDone -> toggleDone(arguments, argumentsJson, source)
                ToolName.NotesPin -> setPinned(arguments, argumentsJson, source)
                ToolName.NotesArchive -> setArchived(arguments, argumentsJson, source)
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
        noteUseCases.updateNote(note.id, note.title, nextContent, note.type, note.color, note.tags.joinToString("、") { it.name })
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
        noteUseCases.updateNote(note.id, title, note.content, note.type, note.color, note.tags.joinToString("、") { it.name })
        val resultJson = JSONObject().put("note_id", note.id).put("title", title).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = listOf(note.id))
        return CommandResult.success("已更新标题", risk, logId, affectedNoteIds = listOf(note.id), resultJson = resultJson)
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

    private suspend fun setPinned(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesPin, rawJson, source, "缺少 note_id 或 note_ids")
        val pinned = args.optBoolean("pinned", true)
        val riskInput = CommandRiskInput(toolName = ToolName.NotesPin, source = source, affectedNoteCount = noteIds.size)
        val risk = riskPolicy.classify(riskInput)
        if (riskPolicy.requiresConfirmation(riskInput)) return createPendingConfirmation(ToolName.NotesPin, rawJson, source, risk, noteIds, emptyList(), "将批量修改 ${noteIds.size} 条便签的置顶状态")
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        val logId = insertInitialLog(ToolName.NotesPin, rawJson, source, risk)
        if (notes.size > 1) insertRevisionSnapshots(logId, source, "pin_batch", notes)
        notes.forEach { noteUseCases.setNotePinned(it.id, pinned) }
        val affectedIds = notes.map { it.id }
        val resultJson = JSONObject().put("pinned", pinned).put("affected_note_ids", affectedIds.toJsonArray()).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = affectedIds)
        return CommandResult.success(if (pinned) "已置顶 ${affectedIds.size} 条便签" else "已取消置顶 ${affectedIds.size} 条便签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
    }

    private suspend fun setArchived(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesArchive, rawJson, source, "缺少 note_id 或 note_ids")
        val archived = args.optBoolean("archived", true)
        val riskInput = CommandRiskInput(toolName = ToolName.NotesArchive, source = source, affectedNoteCount = noteIds.size)
        val risk = riskPolicy.classify(riskInput)
        if (riskPolicy.requiresConfirmation(riskInput)) return createPendingConfirmation(ToolName.NotesArchive, rawJson, source, risk, noteIds, emptyList(), "将批量修改 ${noteIds.size} 条便签的归档状态")
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { !it.deleted }
        val logId = insertInitialLog(ToolName.NotesArchive, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, if (archived) "archive" else "unarchive", notes)
        notes.forEach { noteUseCases.setNoteArchived(it.id, archived) }
        val affectedIds = notes.map { it.id }
        val resultJson = JSONObject().put("archived", archived).put("affected_note_ids", affectedIds.toJsonArray()).toString()
        finishLog(logId = logId, status = CommandStatus.Success, resultJson = resultJson, affectedNoteIds = affectedIds)
        return CommandResult.success(if (archived) "已归档 ${affectedIds.size} 条便签" else "已取消归档 ${affectedIds.size} 条便签", risk, logId, affectedNoteIds = affectedIds, resultJson = resultJson)
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
        noteIds: List<Long>,
        tagIds: List<Long>,
        summary: String,
    ): CommandResult {
        val logId = insertInitialLog(tool, rawJson, source, risk)
        val now = timeProvider.nowMillis()
        val confirmationId = UUID.randomUUID().toString()
        val previewJson = JSONObject()
            .put("summary", summary)
            .put("affected_note_ids", noteIds.toJsonArray())
            .put("affected_tag_ids", tagIds.toJsonArray())
            .toString()
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

    private fun JSONObject.tagsText(): String {
        val explicitTagText = optString("tagText", optString("tag_text", "")).trim()
        if (explicitTagText.isNotBlank()) return explicitTagText
        val array = optJSONArray("tags") ?: return ""
        return buildList {
            for (index in 0 until array.length()) {
                val value = array.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }.joinToString("、")
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

    private fun List<Long>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }

    private fun List<Long>.toJsonArrayString(): String = toJsonArray().toString()

    companion object {
        private const val CONFIRMATION_TTL_MILLIS = 10 * 60 * 1000L
    }
}
