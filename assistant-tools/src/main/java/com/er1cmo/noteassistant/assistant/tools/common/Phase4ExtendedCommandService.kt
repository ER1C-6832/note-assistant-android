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
            return writeFailedLog(
                tool = tool,
                rawJson = argumentsJson,
                source = source,
                risk = RiskLevel.High,
                message = "参数不是有效 JSON：${error.message ?: "解析失败"}",
                errorCode = CommandErrorCode.InvalidJson,
            )
        }
        return runCatching {
            when (tool) {
                ToolName.NotesRestore -> restoreNotes(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.NotesClearDone -> clearDone(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                ToolName.TagsCreate -> createTag(args, argumentsJson, source)
                ToolName.TagsRename -> renameTag(args, argumentsJson, source, existingLogId = null, fromConfirmation = false)
                else -> noteCommandService.execute(toolName = toolName, argumentsJson = argumentsJson, source = source)
            }
        }.getOrElse { error ->
            writeFailedLog(
                tool = tool,
                rawJson = argumentsJson,
                source = source,
                risk = RiskLevel.High,
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

        if (!isExtendedTool(pending.toolName)) {
            return noteCommandService.confirmPendingCommand(confirmationId)
        }

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
                errorCode = CommandErrorCode.ConfirmationExpired,
                commandLogId = pending.commandLogId,
            )
        }

        val args = parseArguments(pending.argumentsJson).getOrElse {
            commandTraceRepository.updatePendingConfirmation(pending.copy(status = ConfirmationStatus.Confirmed))
            finishLog(
                logId = pending.commandLogId,
                status = CommandStatus.Failed,
                errorCode = CommandErrorCode.InvalidJson,
                errorMessage = "待确认参数不是有效 JSON",
                confirmationStatus = ConfirmationStatus.Confirmed,
            )
            return CommandResult.failure(
                message = "待确认参数不是有效 JSON",
                riskLevel = RiskLevel.High,
                errorCode = CommandErrorCode.InvalidJson,
                commandLogId = pending.commandLogId,
            )
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

    private fun isExtendedTool(tool: ToolName): Boolean = when (tool) {
        ToolName.NotesRestore,
        ToolName.NotesClearDone,
        ToolName.TagsRename,
        -> true
        else -> false
    }

    private suspend fun restoreNotes(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
    ): CommandResult {
        val noteIds = args.noteIds()
        if (noteIds.isEmpty()) return validationFailed(ToolName.NotesRestore, rawJson, source, "缺少 note_id 或 note_ids")

        val notes = noteIds.mapNotNull { noteUseCases.getNote(it) }.filter { it.deleted }
        if (notes.isEmpty()) return validationFailed(ToolName.NotesRestore, rawJson, source, "没有可从最近删除恢复的便签")

        val riskInput = CommandRiskInput(toolName = ToolName.NotesRestore, source = source, affectedNoteCount = notes.size)
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将恢复 ${notes.size} 条最近删除便签")
                .put("affected_note_ids", notes.map { it.id }.toJsonArray())
                .put("preview", notes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(
                tool = ToolName.NotesRestore,
                rawJson = rawJson,
                source = source,
                risk = risk,
                previewJson = previewJson,
                noteIds = notes.map { it.id },
                tagIds = emptyList(),
                summary = "将恢复 ${notes.size} 条最近删除便签，是否确认？",
            )
        }

        val logId = existingLogId ?: insertInitialLog(ToolName.NotesRestore, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "restore_deleted", notes)
        val restoredIds = notes.mapNotNull { note -> if (noteUseCases.restoreDeletedNote(note.id)) note.id else null }
        val resultJson = JSONObject()
            .put("restored", true)
            .put("affected_note_ids", restoredIds.toJsonArray())
            .toString()
        finishLog(
            logId = logId,
            status = CommandStatus.Success,
            resultJson = resultJson,
            affectedNoteIds = restoredIds,
            confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired,
        )
        return CommandResult.success(
            message = "已恢复 ${restoredIds.size} 条便签",
            riskLevel = risk,
            commandLogId = logId,
            affectedNoteIds = restoredIds,
            resultJson = resultJson,
        )
    }

    private suspend fun clearDone(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
    ): CommandResult {
        val action = args.optString("action", "archive").trim().lowercase()
        if (action != "archive" && action != "delete") {
            return validationFailed(ToolName.NotesClearDone, rawJson, source, "action 只能是 archive 或 delete")
        }

        val tagFilters = args.tagNames()
        val completedNotes = noteUseCases.listNotes().first().filter { note ->
            note.type == NoteType.Todo &&
                note.isDone &&
                !note.deleted &&
                !note.archived &&
                (tagFilters.isEmpty() || tagFilters.all { wanted -> note.tags.any { it.name.equals(wanted, ignoreCase = true) } })
        }
        if (completedNotes.isEmpty()) return validationFailed(ToolName.NotesClearDone, rawJson, source, "没有匹配的已完成待办")

        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.NotesClearDone, source = source, affectedNoteCount = completedNotes.size))
        if (!fromConfirmation) {
            val previewJson = JSONObject()
                .put("summary", "将${if (action == "archive") "归档" else "删除"} ${completedNotes.size} 条已完成待办")
                .put("action", action)
                .put("affected_note_ids", completedNotes.map { it.id }.toJsonArray())
                .put("preview", completedNotes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(
                tool = ToolName.NotesClearDone,
                rawJson = rawJson,
                source = source,
                risk = risk,
                previewJson = previewJson,
                noteIds = completedNotes.map { it.id },
                tagIds = emptyList(),
                summary = "将${if (action == "archive") "归档" else "删除"} ${completedNotes.size} 条已完成待办，是否确认？",
            )
        }

        val logId = existingLogId ?: insertInitialLog(ToolName.NotesClearDone, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "clear_done_$action", completedNotes)
        val affectedIds = completedNotes.mapNotNull { note ->
            val ok = if (action == "archive") {
                noteUseCases.setNoteArchived(note.id, true)
            } else {
                noteUseCases.softDeleteNote(note.id)
            }
            if (ok) note.id else null
        }
        val resultJson = JSONObject()
            .put("action", action)
            .put("affected_note_ids", affectedIds.toJsonArray())
            .toString()
        finishLog(
            logId = logId,
            status = CommandStatus.Success,
            resultJson = resultJson,
            affectedNoteIds = affectedIds,
            confirmationStatus = ConfirmationStatus.Confirmed,
        )
        return CommandResult.success(
            message = "已${if (action == "archive") "归档" else "删除"} ${affectedIds.size} 条已完成待办",
            riskLevel = risk,
            commandLogId = logId,
            affectedNoteIds = affectedIds,
            resultJson = resultJson,
        )
    }

    private suspend fun createTag(args: JSONObject, rawJson: String, source: CommandSource): CommandResult {
        val name = args.optString("name", args.optString("tag_name", "")).trim().trimStart('#')
        if (name.isBlank()) return validationFailed(ToolName.TagsCreate, rawJson, source, "缺少 name")

        val risk = riskPolicy.classify(CommandRiskInput(toolName = ToolName.TagsCreate, source = source, affectedTagCount = 1))
        val logId = insertInitialLog(ToolName.TagsCreate, rawJson, source, risk)
        val existingTag = noteUseCases.listTags().first().firstOrNull { it.name.equals(name, ignoreCase = true) || it.normalizedName == name.lowercase() }
        if (existingTag != null) {
            val resultJson = existingTag.toJsonObject(linkedNoteCount = 0)
                .put("already_exists", true)
                .toString()
            finishLog(logId, CommandStatus.Success, resultJson = resultJson, affectedTagIds = listOf(existingTag.id))
            return CommandResult.success(
                message = "标签已存在：#${existingTag.name}",
                riskLevel = risk,
                commandLogId = logId,
                affectedTagIds = listOf(existingTag.id),
                resultJson = resultJson,
            )
        }

        val created = noteUseCases.createTag(name)
        val tag = noteUseCases.listTags().first().firstOrNull { it.name.equals(name, ignoreCase = true) || it.normalizedName == name.lowercase() }
        if (!created && tag == null) {
            finishLog(logId, CommandStatus.Failed, errorCode = CommandErrorCode.StorageError, errorMessage = "创建标签失败")
            return CommandResult.failure("创建标签失败", risk, CommandErrorCode.StorageError, logId)
        }

        val affectedTagIds = tag?.let { listOf(it.id) } ?: emptyList()
        val resultJson = JSONObject()
            .put("tag_id", tag?.id ?: JSONObject.NULL)
            .put("tag_name", name)
            .toString()
        finishLog(logId, CommandStatus.Success, resultJson = resultJson, affectedTagIds = affectedTagIds)
        return CommandResult.success(
            message = "已创建标签：#$name",
            riskLevel = risk,
            commandLogId = logId,
            affectedTagIds = affectedTagIds,
            resultJson = resultJson,
        )
    }

    private suspend fun renameTag(
        args: JSONObject,
        rawJson: String,
        source: CommandSource,
        existingLogId: Long?,
        fromConfirmation: Boolean,
    ): CommandResult {
        val tagId = args.optLong("tag_id", 0L)
        val newName = args.optString("name", args.optString("tag_name", "")).trim().trimStart('#')
        if (tagId <= 0L) return validationFailed(ToolName.TagsRename, rawJson, source, "缺少 tag_id")
        if (newName.isBlank()) return validationFailed(ToolName.TagsRename, rawJson, source, "缺少 name")

        val tag = noteUseCases.listTags().first().firstOrNull { it.id == tagId }
            ?: return writeFailedLog(ToolName.TagsRename, rawJson, source, RiskLevel.High, "没有找到标签：$tagId", CommandErrorCode.NotFound)
        val linkedNotes = allNotes().filter { note -> note.tags.any { it.id == tag.id || it.normalizedName == tag.normalizedName } }
        val riskInput = CommandRiskInput(
            toolName = ToolName.TagsRename,
            source = source,
            affectedNoteCount = linkedNotes.size,
            affectedTagCount = 1,
            linkedNoteCount = linkedNotes.size,
        )
        val risk = riskPolicy.classify(riskInput)
        if (!fromConfirmation && riskPolicy.requiresConfirmation(riskInput)) {
            val previewJson = JSONObject()
                .put("summary", "将重命名标签 #${tag.name} 为 #$newName，并影响 ${linkedNotes.size} 条便签")
                .put("tag_id", tag.id)
                .put("old_name", tag.name)
                .put("new_name", newName)
                .put("linked_note_count", linkedNotes.size)
                .put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray())
                .put("preview", linkedNotes.toPreviewJsonArray())
                .toString()
            return createPendingConfirmation(
                tool = ToolName.TagsRename,
                rawJson = rawJson,
                source = source,
                risk = risk,
                previewJson = previewJson,
                noteIds = linkedNotes.map { it.id },
                tagIds = listOf(tag.id),
                summary = "将重命名标签 #${tag.name} 为 #$newName，是否确认？",
            )
        }

        val logId = existingLogId ?: insertInitialLog(ToolName.TagsRename, rawJson, source, risk)
        insertRevisionSnapshots(logId, source, "rename_tag", linkedNotes)
        val ok = noteUseCases.renameTag(tag.id, newName)
        val resultJson = JSONObject()
            .put("tag_id", tag.id)
            .put("old_name", tag.name)
            .put("new_name", newName)
            .put("affected_note_ids", linkedNotes.map { it.id }.toJsonArray())
            .toString()
        finishLog(
            logId = logId,
            status = if (ok) CommandStatus.Success else CommandStatus.Failed,
            resultJson = resultJson,
            affectedNoteIds = linkedNotes.map { it.id },
            affectedTagIds = listOf(tag.id),
            errorCode = if (ok) null else CommandErrorCode.StorageError,
            errorMessage = if (ok) null else "重命名标签失败",
            confirmationStatus = if (fromConfirmation) ConfirmationStatus.Confirmed else ConfirmationStatus.NotRequired,
        )
        return if (ok) {
            CommandResult.success(
                message = "已重命名标签 #${tag.name} 为 #$newName",
                riskLevel = risk,
                commandLogId = logId,
                affectedNoteIds = linkedNotes.map { it.id },
                affectedTagIds = listOf(tag.id),
                resultJson = resultJson,
            )
        } else {
            CommandResult.failure("重命名标签失败", risk, CommandErrorCode.StorageError, logId)
        }
    }

    private suspend fun allNotes(): List<Note> = buildList {
        addAll(noteUseCases.listNotes().first())
        addAll(noteUseCases.listArchivedNotes().first())
        addAll(noteUseCases.listDeletedNotes().first())
    }.distinctBy { it.id }

    private fun parseArguments(argumentsJson: String): Result<JSONObject> = runCatching {
        if (argumentsJson.isBlank()) JSONObject() else JSONObject(argumentsJson)
    }

    private suspend fun insertInitialLog(tool: ToolName, rawJson: String, source: CommandSource, risk: RiskLevel): Long {
        return commandTraceRepository.insertCommandLog(
            AssistantCommandLog(
                source = source,
                toolName = tool,
                argumentsJson = rawJson,
                riskLevel = risk,
                confirmationStatus = ConfirmationStatus.NotRequired,
                status = CommandStatus.Blocked,
                createdAt = timeProvider.nowMillis(),
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
        rawJson: String,
        source: CommandSource,
        risk: RiskLevel,
        message: String,
        errorCode: CommandErrorCode,
    ): CommandResult {
        val logId = insertInitialLog(tool, rawJson, source, risk)
        finishLog(logId, CommandStatus.Failed, errorCode = errorCode, errorMessage = message)
        return CommandResult.failure(message, risk, errorCode, logId)
    }

    private suspend fun validationFailed(tool: ToolName, rawJson: String, source: CommandSource, message: String): CommandResult {
        return writeFailedLog(tool, rawJson, source, RiskLevel.Medium, message, CommandErrorCode.ValidationError)
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
            notes.forEach { note -> insertRevision(note.toRevision(source, reason, commandLogId, now)) }
        }
    }

    private fun Note.toRevision(source: CommandSource, reason: String, commandLogId: Long, createdAt: Long): NoteRevision {
        return NoteRevision(
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
    }

    private fun JSONObject.noteIds(): List<Long> {
        val array = optJSONArray("note_ids")
        if (array != null) {
            val ids = mutableListOf<Long>()
            for (index in 0 until array.length()) {
                val id = array.optLong(index, 0L)
                if (id > 0L) ids += id
            }
            return ids.distinct()
        }
        val id = optLong("note_id", 0L)
        return if (id > 0L) listOf(id) else emptyList()
    }

    private fun JSONObject.tagNames(): List<String> {
        val fromText = optString("tagText", optString("tag_text", "")).trim()
        if (fromText.isNotBlank()) {
            return fromText.split(Regex("[\\s,，、#]+"))
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .distinctBy { it.lowercase() }
        }
        val array = optJSONArray("tags") ?: return emptyList()
        val names = mutableListOf<String>()
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) names += value
        }
        return names.distinctBy { it.lowercase() }
    }

    private fun List<Note>.toPreviewJsonArray(): JSONArray {
        val array = JSONArray()
        take(8).forEach { note ->
            array.put(
                JSONObject()
                    .put("note_id", note.id)
                    .put("title", note.title)
                    .put("snippet", note.content.take(80))
                    .put("updated_at", note.updatedAt),
            )
        }
        return array
    }

    private fun Tag.toJsonObject(linkedNoteCount: Int): JSONObject {
        return JSONObject()
            .put("tag_id", id)
            .put("name", name)
            .put("color", color ?: JSONObject.NULL)
            .put("linked_note_count", linkedNoteCount)
    }

    private fun List<Long>.toJsonArray(): JSONArray {
        val array = JSONArray()
        forEach { array.put(it) }
        return array
    }

    private fun List<Long>.toJsonArrayString(): String = toJsonArray().toString()

    companion object {
        private const val CONFIRMATION_TTL_MILLIS = 10 * 60 * 1000L
    }
}
