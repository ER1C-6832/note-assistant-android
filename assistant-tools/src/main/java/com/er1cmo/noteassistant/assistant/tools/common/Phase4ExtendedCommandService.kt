package com.er1cmo.noteassistant.assistant.tools.common

import com.er1cmo.noteassistant.core.common.time.TimeProvider
import com.er1cmo.noteassistant.notes.domain.command.CommandErrorCode
import com.er1cmo.noteassistant.notes.domain.command.CommandResult
import com.er1cmo.noteassistant.notes.domain.command.CommandRiskInput
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.CommandStatus
import com.er1cmo.noteassistant.notes.domain.command.ConfirmationStatus
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.command.NoteRiskPolicy
import com.er1cmo.noteassistant.notes.domain.command.RiskLevel
import com.er1cmo.noteassistant.notes.domain.command.ToolName
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteRevision
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.PendingConfirmation
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject

@Singleton
class Phase4ExtendedCommandService @Inject constructor(
    private val noteCommandService: NoteCommandService,
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
        val args = parseArguments(argumentsJson).getOrElse { error ->
            return writeFailedLog(tool, argumentsJson, source, RiskLevel.High, "参数不是有效 JSON：${error.message ?: "解析失败"}", CommandErrorCode.InvalidJson)
        }
        return runCatching {
            when (tool) {
                ToolName.NotesRestore -> restoreNotes(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.NotesClearDone -> clearDone(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.TagsCreate -> createTag(args, argumentsJson, source)
                ToolName.TagsRename -> renameTag(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                else -> noteCommandService.execute(toolName, argumentsJson, source)
            }
        }.getOrElse { error ->
            writeFailedLog(tool, argumentsJson, source, RiskLevel.High, "命令执行失败：${error.message ?: "未知错误"}", CommandErrorCode.StorageError)
        }
    }

    suspend fun confirmPendingCommand(confirmationId: String): CommandResult {
        commandTraceRepository.markExpiredPendingConfirmations(timeProvider.nowMillis())
        val pending = commandTraceRepository.getPendingConfirmation(confirmationId)
            ?: return CommandResult.failure("没有找到待确认操作", RiskLevel.High, CommandErrorCode.NotFound)
        if (!isExtendedTool(pending.toolName)) return noteCommandService.confirmPendingCommand(confirmationId)
        if (pending.status != ConfirmationStatus.Pending) {
            return CommandResult.failure(
                message = "待确认操作当前状态为 ${pending.status.storageValue}，不能再次确认",
                riskLevel = RiskLevel.High,
                errorCode = when (pending.status) {
                    ConfirmationStatus.Expired -> CommandErrorCode.ConfirmationExpired
                    ConfirmationStatus.Rejected -> CommandErrorCode.ConfirmationRejected
                    else -> CommandErrorCode.Conflict
                },
                commandLogId = pending.commandLogId,
            )
        }
        val now = timeProvider.nowMillis()
        if (pending.expiresAt <= now) {
            commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Expired))
            finishLog(pending.commandLogId, CommandStatus.Blocked, errorCode = CommandErrorCode.ConfirmationExpired, errorMessage = "确认已过期，请重新发起命令", confirmationStatus = ConfirmationStatus.Expired)
            return CommandResult.failure("确认已过期，请重新发起命令", RiskLevel.High, CommandErrorCode.ConfirmationExpired, pending.commandLogId)
        }
        val args = parseArguments(pending.argumentsJson).getOrElse {
            finishLog(pending.commandLogId, CommandStatus.Failed, errorCode = CommandErrorCode.InvalidJson, errorMessage = "待确认参数不是有效 JSON", confirmationStatus = ConfirmationStatus.Confirmed)
            commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Confirmed))
            return CommandResult.failure("待确认参数不是有效 JSON", RiskLevel.High, CommandErrorCode.InvalidJson, pending.commandLogId)
        }
        commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Confirmed))
        return when (pending.toolName) {
            ToolName.NotesRestore -> restoreNotes(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            ToolName.NotesClearDone -> clearDone(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            ToolName.TagsRename -> renameTag(args, pending.argumentsJson, pending.source, pending.commandLogId, fromConfirmation = true)
            else -> noteCommandService.confirmPendingCommand(confirmationId)
        }
    }

    suspend fun rejectPendingCommand(confirmationId: String): CommandResult = noteCommandService.rejectPendingCommand(confirmationId)

    suspend fun listPendingConfirmations(limit: Int = 5): List<PendingConfirmation> {
        commandTraceRepository.markExpiredPendingConfirmations(timeProvider.nowMillis())
        return commandTraceRepository.listPendingConfirmations(limit.coerceIn(1, 50))
    }

    private fun isExtendedTool(tool: ToolName): Boolean = tool == ToolName.NotesRestore || tool == ToolName.NotesClearDone || tool == ToolName.TagsRename

    private suspend fun restoreNotes(args: JSONObject, rawJson: String, source: CommandSource, existingLogId: Long?, fromConfirmation: Boolean): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesRestore, rawJson, source, "缺少 note_id 或 note_ids")
        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { it.deleted }
        if (notes.isEmpty()) return validationFailed(ToolName.NotesRestore, rawJson, source, "没有可从最近删除恢复的便签")
        val riskInput = CommandRiskInput(ToolName.NotesRestore, source, affectedNoteCount = notes.size)
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将恢复 ${notes.size} 条最近删除便签")
                .put("affected_note_ids", notes.map { it.id }.toJsonArray())
                .put("preview", notes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesRestore, rawJson, source, risk, previewJson, notes.map { it.id }, emptyList(), "将恢复 ${notes.size} 条最近删除便签，是否确认？")
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesRestore, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "restore_deleted", notes)
        val restored = mutableListOf<Long>()
        notes.forEach { note -> if (noteUseCases.restoreDeletedNote(note.id)) restored += note.id }
        val resultJson = JSONObject().put("restored", true).put("affected_note_ids", restored.toJsonArray()).toString()
        finishLog(logId, CommandStatus.Success, resultJson, restored, confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return CommandResult.success("已恢复 ${restored.size} 条便签", risk, logId, affectedNoteIds = restored, resultJson = resultJson)
    }

    private suspend fun clearDone(args: JSONObject, rawJson: String, source: CommandSource, existingLogId: Long?, fromConfirmation: Boolean): CommandResult {
        val action = args.optString("action", "archive").trim().lowercase()
        if (action != "archive" && action != "delete") return validationFailed(ToolName.NotesClearDone, rawJson, source, "action 只能是 archive 或 delete")
        val tagFilters = args.tagNames()
        val completed = noteUseCases.listNotes().first().filter { note ->
            note.type == NoteType.Todo && note.isDone && !note.deleted && !note.archived &&
                (tagFilters.isEmpty() || tagFilters.all { wanted -> note.tags.any { it.name.equals(wanted, ignoreCase = true) } })
        }
        if (completed.isEmpty()) return validationFailed(ToolName.NotesClearDone, rawJson, source, "没有匹配的已完成待办")
        val risk = riskPolicy.classify(CommandRiskInput(ToolName.NotesClearDone, source, affectedNoteCount = completed.size))
        if (!fromConfirmation) {
            val previewJson = JSONObject()
                .put("summary", "将${if (action == "archive") "归档" else "删除"} ${completed.size} 条已完成待办")
                .put("action", action)
                .put("affected_note_ids", completed.map { it.id }.toJsonArray())
                .put("preview", completed.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.NotesClearDone, rawJson, source, risk, previewJson, completed.map { it.id }, emptyList(), "将${if (action == "archive") "归档" else "删除"} ${completed.size} 条已完成待办，是否确认？")
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.NotesClearDone, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "clear_done_$action", completed)
        val affected = mutableListOf<Long>()
        completed.forEach { note ->
            val ok = if (action == "archive") {
                noteUseCases.setNoteArchived(note.id, true)
                true
            } else {
                noteUseCases.softDeleteNote(note.id)
            }
            if (ok) affected += note.id
        }
        val resultJson = JSONObject().put("action", action).put("affected_note_ids", affected.toJsonArray()).toString()
        finishLog(logId, CommandStatus.Success, resultJson, affected, confirmationStatus = ConfirmationStatus.Confirmed)
        return CommandResult.success("已${if (action == "archive") "归档" else "删除"} ${affected.size} 条已完成待办", risk, logId, affectedNoteIds = affected, resultJson = resultJson)
    }

    private suspend fun createTag(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val name = args.optString("name", args.optString("tag_name", "")).trim().trimStart('#')
        if (name.isBlank()) return validationFailed(ToolName.TagsCreate, rawJson, source, "缺少 name")
        val risk = riskPolicy.classify(CommandRiskInput(ToolName.TagsCreate, source, affectedTagCount = 1))
        val logId = insertInitialLog(ToolName.TagsCreate, rawJson, source, risk)
        val existed = noteUseCases.listTags().first().firstOrNull { it.name.equals(name, ignoreCase = true) || it.normalizedName == name.lowercase() }
        if (existed != null) {
            val resultJson = existed.toJsonObject(0).put("already_exists", true).toString()
            finishLog(logId, CommandStatus.Success, resultJson, affectedTagIds = listOf(existed.id))
            return CommandResult.success("标签已存在：#${existed.name}", risk, logId, affectedTagIds = listOf(existed.id), resultJson = resultJson)
        }
        val created = noteUseCases.createTag(name)
        val tag = noteUseCases.listTags().first().firstOrNull { it.name.equals(name, ignoreCase = true) || it.normalizedName == name.lowercase() }
        if (!created && tag == null) {
            finishLog(logId, CommandStatus.Failed, errorCode = CommandErrorCode.StorageError, errorMessage = "创建标签失败")
            return CommandResult.failure("创建标签失败", risk, CommandErrorCode.StorageError, logId)
        }
        val affectedTagIds = tag?.let { listOf(it.id) } ?: emptyList()
        val resultJson = JSONObject().put("tag_id", tag?.id ?: JSONObject.NULL).put("tag_name", name).toString()
        finishLog(logId, CommandStatus.Success, resultJson, affectedTagIds = affectedTagIds)
        return CommandResult.success("已创建标签：#$name", risk, logId, affectedTagIds = affectedTagIds, resultJson = resultJson)
    }

    private suspend fun renameTag(args: JSONObject, rawJson: String, source: CommandSource, existingLogId: Long?, fromConfirmation: Boolean): CommandResult {
        val tagId = args.optLong("tag_id", 0L)
        val name = args.optString("name", args.optString("tag_name", "")).trim().trimStart('#')
        if (tagId <= 0) return validationFailed(ToolName.TagsRename, rawJson, source, "缺少 tag_id")
        if (name.isBlank()) return validationFailed(ToolName.TagsRename, rawJson, source, "缺少 name")
        val tags = noteUseCases.listTags().first()
        val tag = tags.firstOrNull { it.id == tagId } ?: return writeFailedLog(ToolName.TagsRename, rawJson, source, RiskLevel.High, "没有找到标签：$tagId", CommandErrorCode.NotFound)
        val linkedNotes = allNotes().filter { note -> note.tags.any { it.id == tag.id || it.normalizedName == tag.normalizedName } }
        val riskInput = CommandRiskInput(ToolName.TagsRename, source, affectedNoteCount = linkedNotes.size, affectedTagCount = 1, linkedNoteCount = linkedNotes.size)
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将重命名标签 #${tag.name} 为 #$name，并影响 ${linkedNotes.size} 条便签")
                .put("tag_id", tag.id)
                .put("old_name", tag.name)
                .put("new_name", name)
                .put("linked_note_count", linkedNotes.size)
                .put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray())
                .put("preview", linkedNotes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(ToolName.TagsRename, rawJson, source, risk, previewJson, linkedNotes.map { it.id }, listOf(tag.id), "将重命名标签 #${tag.name} 为 #$name，是否确认？")
        }
        val logId = existingLogId ?: insertInitialLog(ToolName.TagsRename, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "rename_tag", linkedNotes)
        val ok = noteUseCases.renameTag(tag.id, name)
        val status = if (ok) CommandStatus.Success else CommandStatus.Failed
        val resultJson = JSONObject().put("tag_id", tag.id).put("old_name", tag.name).put("new_name", name).put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray()).toString()
        finishLog(logId, status, resultJson, linkedNotes.map { it.id }, listOf(tag.id), errorCode = if (ok) null else CommandErrorCode.StorageError, errorMessage = if (ok) null else "重命名标签失败", confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired)
        return if (ok) CommandResult.success("已重命名标签 #${tag.name} 为 #$name", risk, logId, affectedNoteIds = linkedNotes.map { it.id }, affectedTagIds = listOf(tag.id), resultJson = resultJson) else CommandResult.failure("重命名标签失败", risk, CommandErrorCode.StorageError, logId)
    }

    private suspend fun allNotes(): List<Note> = buildList {
        addAll(noteUseCases.listNotes().first())
        addAll(noteUseCases.listArchivedNotes().first())
        addAll(noteUseCases.listDeletedNotes().first())
    }.distinctBy { it.id }

    private fun parseArguments(argumentsJson: String): Result<JSONObject> = runCatching { if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson) }

    private suspend fun insertInitialLog(tool: ToolName, rawJson: String, source: CommandSource, risk: RiskLevel): Long = commandTraceRepository.insertCommandLog(
        AssistantCommandLog(source = source, toolName = tool, argumentsJson = rawJson, riskLevel = risk, confirmationStatus = ConfirmationStatus.NotRequired, status = CommandStatus.Blocked, createdAt = timeProvider.nowMillis()),
    )

    private suspend fun finishLog(logId: Long, status: CommandStatus, resultJson: String? = null, affectedNoteIds: List<Long> = emptyList(), affectedTagIds: List<Long> = emptyList(), errorCode: CommandErrorCode? = null, errorMessage: String? = null, confirmationStatus: ConfirmationStatus = ConfirmationStatus.NotRequired) {
        val existing = commandTraceRepository.getCommandLog(logId) ?: return
        commandTraceRepository.updateCommandLog(existing.copy(status = status, confirmationStatus = confirmationStatus, resultJson = resultJson, affectedNoteIdsJson = affectedNoteIds.takeIf { it.isNotEmpty() }?.toJsonArrayString(), affectedTagIdsJson = affectedTagIds.takeIf { it.isNotEmpty() }?.toJsonArrayString(), errorCode = errorCode, errorMessage = errorMessage, completedAt = timeProvider.nowMillis()))
    }

    private suspend fun writeFailedLog(tool: ToolName, rawJson: String, source: CommandSource, risk: RiskLevel, message: String, errorCode: CommandErrorCode): CommandResult {
        val logId = insertInitialLog(tool, rawJson, source, risk)
        finishLog(logId, CommandStatus.Failed, errorCode = errorCode, errorMessage = message)
        return CommandResult.failure(message, risk, errorCode, logId)
    }

    private suspend fun validationFailed(tool: ToolName, rawJson: String, source: CommandSource, message: String): CommandResult = writeFailedLog(tool, rawJson, source, RiskLevel.Medium, message, CommandErrorCode.ValidationError)

    private suspend fun createPendingConfirmation(tool: ToolName, rawJson: String, source: CommandSource, risk: RiskLevel, previewJson: String, noteIds: List<Long>, tagIds: List<Long>, summary: String): CommandResult {
        val logId = insertInitialLog(tool, rawJson, source, risk)
        val now = timeProvider.nowMillis()
        val confirmationId = UUID.randomUUID().toString()
        commandTraceRepository.insertPendingConfirmation(PendingConfirmation(confirmationId = confirmationId, commandLogId = logId, toolName = tool, argumentsJson = rawJson, riskLevel = RiskLevel.High, previewJson = previewJson, createdAt = now, expiresAt = now + CONFIRMATION_TTL_MILLIS, source = source, status = ConfirmationStatus.Pending))
        finishLog(logId, CommandStatus.RequiresConfirmation, previewJson, noteIds, tagIds, CommandErrorCode.RequiresConfirmation, summary, ConfirmationStatus.Pending)
        return CommandResult.requiresConfirmation(summary, logId, confirmationId, noteIds, tagIds, previewJson)
    }

    private suspend fun insertRevisionSnapshots(commandLogId: Long, source: CommandSource, reason: String, notes: List<Note>) {
        if (notes.isEmpty()) return
        val now = timeProvider.nowMillis()
        commandTraceRepository.runInTraceTransaction {
            notes.forEach { note -> insertRevision(note.toRevision(source, reason, commandLogId, now)) }
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

    private fun JSONObject.noteIds(): List<Long> {
        val array = optJSONArray("note_ids")
        if (array != null) return buildList { for (index in 0 until array.length()) array.optLong(index, 0L).takeIf { it > 0 }?.let { add(it) } }.distinct()
        val id = optLong("note_id", 0L)
        return if (id > 0) listOf(id) else emptyList()
    }

    private fun JSONObject.tagNames(): List<String> {
        val fromText = optString("tagText", optString("tag_text", "")).trim()
        if (fromText.isNotBlank()) return fromText.split(Regex("[\\s,，、#]+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase() }
        val array = optJSONArray("tags") ?: return emptyList()
        return buildList { for (index in 0 until array.length()) array.optString(index).trim().takeIf { it.isNotBlank() }?.let { add(it) } }.distinctBy { it.lowercase() }
    }

    private fun List<Note>.toPreviewJsonArray(): JSONArray = JSONArray().also { array ->
        take(8).forEach { note -> array.put(JSONObject().put("note_id", note.id).put("title", note.title).put("updated_at", note.updatedAt)) }
    }

    private fun Tag.toJsonObject(linkedNoteCount: Int): JSONObject = JSONObject().put("tag_id", id).put("name", name).put("color", color ?: JSONObject.NULL).put("linked_note_count", linkedNoteCount)

    private fun List<Long>.toJsonArray(): JSONArray = JSONArray().also { array -> forEach { array.put(it) } }
    private fun List<Long>.toJsonArrayString(): String = toJsonArray().toString()

    companion object {
        private const val CONFIRMATION_TTL_MILLIS = 10 * 60 * 1000L
    }
}
