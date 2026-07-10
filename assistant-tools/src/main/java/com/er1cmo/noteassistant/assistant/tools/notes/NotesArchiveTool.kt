package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.mcpbase.McpRiskLevel
import com.er1cmo.noteassistant.assistant.mcpbase.McpTool
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolContext
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolDescriptor
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolResult
import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import com.er1cmo.noteassistant.assistant.tools.common.toCommandSource
import com.er1cmo.noteassistant.assistant.tools.common.toMcpToolResult
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.model.Note
import javax.inject.Inject
import org.json.JSONArray
import org.json.JSONObject

class NotesArchiveTool @Inject constructor(
    private val commandService: NoteCommandService,
    private val resolver: NoteReferenceResolver,
) : McpTool {
    override val name: String = "notes.archive"
    override val description: String =
        "归档或取消归档便签。用户按标签描述目标（例如“标签是客户那一条”“客户标签下所有便签”）时，直接传 tag_name/target_tag；不要先按标签列出后再把标签词当标题关键词。标签只匹配真实标签，不匹配标题中的同名文字。"
    override val riskLevel: McpRiskLevel = McpRiskLevel.Medium
    override val descriptor: McpToolDescriptor = McpToolDescriptor(
        name = name,
        description = description,
        inputSchemaJson = """
            {
              "type": "object",
              "properties": {
                "note_ref": { "type": "string", "description": "用户可见标题或唯一关键词。按标签定位时可省略。" },
                "note_title": { "type": "string" },
                "target_title": { "type": "string" },
                "exact_title": { "type": "string" },
                "query": { "type": "string", "description": "可保留原始用户话术；工具会识别‘标签是客户’或‘客户标签下’。" },
                "title": { "type": "string", "description": "目标标题兼容字段" },
                "tag_name": { "type": "string", "description": "用于定位目标便签的真实标签名，例如 客户。不是标题关键词。" },
                "target_tag": { "type": "string", "description": "tag_name 的兼容字段" },
                "tag": { "type": "string", "description": "tag_name 的兼容字段" },
                "tag_names": { "type": "array", "items": { "type": "string" }, "description": "多个标签同时存在时才匹配" },
                "note_id": { "type": "integer", "description": "内部 ID，仅当来自当前工具结果时使用" },
                "note_ids": { "type": "array", "items": { "type": "integer" } },
                "id_is_internal": { "type": "boolean" },
                "allow_multiple": { "type": "boolean", "description": "只有用户明确说全部/所有时设为 true" },
                "archived": { "type": "boolean", "description": "true 归档；false 取消归档" }
              },
              "additionalProperties": false
            }
        """.trimIndent(),
        riskLevel = McpRiskLevel.Medium,
        mutates = true,
        confirmation = McpToolDescriptor.CONFIRMATION_MAY_BE_REQUIRED,
        examples = listOf(
            "归档验收归档记录：{\"note_ref\":\"验收归档记录\",\"archived\":true}",
            "把标签是客户的那一条归档：{\"tag_name\":\"客户\",\"archived\":true}",
            "把客户标签下所有便签归档：{\"tag_name\":\"客户\",\"allow_multiple\":true,\"archived\":true}",
            "恢复客户标签下唯一的已归档便签：{\"tag_name\":\"客户\",\"archived\":false}",
        ),
    )

    override suspend fun call(argumentsJson: String): McpToolResult = call(argumentsJson, McpToolContext())

    override suspend fun call(argumentsJson: String, context: McpToolContext): McpToolResult {
        val args = runCatching { JSONObject(argumentsJson.trim().ifBlank { "{}" }) }.getOrElse { error ->
            return McpToolResult.invalidJson(
                toolName = name,
                argumentsJson = argumentsJson,
                message = "notes.archive 参数不是有效 JSON：${error.message ?: "解析失败"}",
            )
        }
        val archived = if (args.has("archived")) args.optBoolean("archived", true) else true
        val scope = archiveResolveScope(archived)
        val tagTarget = args.toArchiveTagTarget()
        val prepared = if (tagTarget != null) {
            prepareTagResolvedArguments(
                args = args,
                originalArgumentsJson = argumentsJson,
                tagTarget = tagTarget,
                scope = scope,
            )
        } else {
            prepareResolvedNoteArguments(
                toolName = name,
                argumentsJson = argumentsJson,
                context = context,
                risk = riskLevel,
                resolver = resolver,
                scope = scope,
                supportsMultiple = true,
                referenceFields = ARCHIVE_NOTE_REFERENCE_FIELDS,
                exactTitleFields = ARCHIVE_EXACT_TITLE_FIELDS,
            )
        }

        return when (prepared) {
            is PreparedNoteArguments.Failed -> prepared.result
            is PreparedNoteArguments.Ready -> {
                val normalizedArguments = JSONObject(prepared.argumentsJson)
                    .put("archived", archived)
                    .toString()
                val result = commandService.execute(
                    toolName = name,
                    argumentsJson = normalizedArguments,
                    source = context.toCommandSource(),
                ).toMcpToolResult(
                    toolName = name,
                    argumentsJson = normalizedArguments,
                ).withResolvedNoteTargets(prepared.notes)

                if (tagTarget == null) {
                    result
                } else {
                    result.withArchiveTagContext(
                        target = tagTarget,
                        notes = prepared.notes,
                        archived = archived,
                    )
                }
            }
        }
    }

    private suspend fun prepareTagResolvedArguments(
        args: JSONObject,
        originalArgumentsJson: String,
        tagTarget: ArchiveTagTarget,
        scope: NoteResolveScope,
    ): PreparedNoteArguments {
        val maxMatches = args.optInt("max_matches", 20).coerceIn(1, 20)
        val resolution = if (tagTarget.noteReference.isBlank()) {
            val pool = resolver.loadPool(scope)
            val taggedNotes = pool
                .filter { note -> note.matchesAllArchiveTags(tagTarget.tagNames) }
                .sortedWith(compareByDescending<Note> { it.updatedAt }.thenByDescending { it.id })
            NoteResolveResult(
                requestedText = tagTarget.sourceText,
                normalizedText = tagTarget.tagNames.joinToString("、"),
                strategy = "tag_exact",
                totalMatches = taggedNotes.size,
                matches = taggedNotes.take(maxMatches),
                resultIsLimited = taggedNotes.size > maxMatches,
                scores = taggedNotes.associate { it.id to 1100 },
                matchedFields = taggedNotes.associate { it.id to listOf("tag_exact") },
                poolSize = pool.size,
            )
        } else {
            resolver.resolve(
                NoteResolveRequest(
                    query = tagTarget.noteReference,
                    exactTitle = args.archiveExactTitle(),
                    scope = scope,
                    limit = maxMatches,
                    tags = tagTarget.tagNames,
                ),
            )
        }

        if (resolution.totalMatches == 0 || resolution.matches.isEmpty()) {
            val tagText = tagTarget.tagNames.joinToString("、") { "#$it" }
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = if (tagTarget.noteReference.isBlank()) {
                        "没有找到带有标签 $tagText 且处于当前归档范围的便签。已停止执行。"
                    } else {
                        "标签 $tagText 下没有找到与“${tagTarget.noteReference}”匹配的便签。已停止执行。"
                    },
                    toolName = name,
                    argumentsJson = originalArgumentsJson,
                    errorCode = "tagged_note_not_found",
                    risk = riskLevel,
                ).copy(resultJson = resolution.toJson(kind = "archive_tag_resolution_no_match").toString()),
            )
        }
        if (resolution.resultIsLimited) {
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = "标签目标超过 ${resolution.matches.size} 条，请缩小条件后重试。",
                    toolName = name,
                    argumentsJson = originalArgumentsJson,
                    errorCode = "too_many_note_matches",
                    risk = riskLevel,
                ).copy(resultJson = resolution.toJson(kind = "archive_tag_resolution_limited").toString()),
            )
        }

        val allowMultiple = args.optBoolean("allow_multiple", false) || tagTarget.multipleIntent
        if (resolution.totalMatches > 1 && !allowMultiple) {
            val tagText = tagTarget.tagNames.joinToString("、") { "#$it" }
            return PreparedNoteArguments.Failed(
                McpToolResult.failed(
                    message = "标签 $tagText 下有 ${resolution.totalMatches} 条候选便签。请说出具体标题；只有明确说“全部/所有”时才会批量归档。",
                    toolName = name,
                    argumentsJson = originalArgumentsJson,
                    errorCode = "ambiguous_tagged_note_reference",
                    risk = riskLevel,
                ).copy(resultJson = resolution.toJson(kind = "archive_tag_resolution_ambiguous").toString()),
            )
        }

        val selected = if (allowMultiple) resolution.matches else listOf(resolution.matches.first())
        val requestedIds = args.archiveRequestedIds()
        val rewritten = JSONObject(args.toString())
        ARCHIVE_NOTE_REFERENCE_FIELDS.forEach { field -> rewritten.remove(field) }
        ARCHIVE_TAG_FIELDS.forEach { field -> rewritten.remove(field) }
        rewritten.remove("note_id")
        rewritten.remove("note_ids")
        rewritten.remove("allow_multiple")
        rewritten.remove("max_matches")
        rewritten.remove("force_note_id")
        rewritten.remove("id_is_internal")
        if (selected.size == 1) {
            rewritten.put("note_id", selected.first().id)
        } else {
            rewritten.put("note_ids", JSONArray(selected.map { it.id }))
        }
        rewritten.put("resolved_tag_names", JSONArray(tagTarget.tagNames))
        rewritten.put("resolved_note_ref", tagTarget.noteReference.ifBlank { JSONObject.NULL })
        rewritten.put("resolved_by", resolution.strategy)
        rewritten.put("resolved_target_titles", JSONArray(selected.map { it.title.ifBlank { "未命名便签" } }))
        if (requestedIds.isNotEmpty() && requestedIds.toSet() != selected.map { it.id }.toSet()) {
            rewritten.put("discarded_conflicting_note_ids", JSONArray(requestedIds))
        }
        return PreparedNoteArguments.Ready(
            argumentsJson = rewritten.toString(),
            notes = selected,
        )
    }
}

internal data class ArchiveTagTarget(
    val tagNames: List<String>,
    val noteReference: String,
    val multipleIntent: Boolean,
    val sourceText: String,
)

internal fun archiveResolveScope(archived: Boolean): NoteResolveScope =
    if (archived) NoteResolveScope.Active else NoteResolveScope.Archived

internal fun JSONObject.toArchiveTagTarget(): ArchiveTagTarget? {
    val explicitTags = buildList {
        ARCHIVE_SINGLE_TAG_FIELDS.forEach { field ->
            optString(field, "").normalizeArchiveTagName().takeIf { it.isNotBlank() }?.let(::add)
        }
        ARCHIVE_ARRAY_TAG_FIELDS.forEach { field ->
            optJSONArray(field)?.let { array ->
                for (index in 0 until array.length()) {
                    array.optString(index, "").normalizeArchiveTagName().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }
    }.distinctBy { it.lowercase() }

    val sourceValues = ARCHIVE_NOTE_REFERENCE_FIELDS
        .map { field -> field to optString(field, "").cleanVoiceReference() }
        .filter { (_, value) -> value.isNotBlank() }
    val inferredTags = sourceValues
        .asSequence()
        .flatMap { (_, value) -> value.extractArchiveTagNames().asSequence() }
        .distinctBy { it.lowercase() }
        .toList()
    val tagNames = (explicitTags.ifEmpty { inferredTags }).distinctBy { it.lowercase() }
    if (tagNames.isEmpty()) return null

    val noteSpecificReference = sourceValues
        .asSequence()
        .filter { (field, _) -> field != "query" }
        .map { (_, value) -> value.toArchiveNoteReference(tagNames, stripActionWords = false) }
        .firstOrNull { candidate -> candidate.isMeaningfulArchiveReference(tagNames) }
        .orEmpty()
    val queryReference = sourceValues
        .firstOrNull { (field, _) -> field == "query" }
        ?.second
        ?.toArchiveNoteReference(tagNames, stripActionWords = true)
        ?.takeIf { candidate -> candidate.isMeaningfulArchiveReference(tagNames) }
        .orEmpty()
    val sourceText = sourceValues.firstOrNull { (field, _) -> field == "query" }?.second
        ?: sourceValues.firstOrNull()?.second
        ?: tagNames.joinToString("、")

    return ArchiveTagTarget(
        tagNames = tagNames,
        noteReference = noteSpecificReference.ifBlank { queryReference },
        multipleIntent = optBoolean("allow_multiple", false) || sourceValues.any { (_, value) -> value.hasArchiveMultipleIntent() },
        sourceText = sourceText,
    )
}

internal fun Note.matchesAllArchiveTags(tagNames: List<String>): Boolean = tagNames.all { wanted ->
    val normalizedWanted = wanted.normalizeArchiveTagName().lowercase()
    tags.any { tag ->
        tag.name.equals(wanted, ignoreCase = true) || tag.normalizedName.lowercase() == normalizedWanted
    }
}

internal fun String.extractArchiveTagNames(): List<String> = buildList {
    ARCHIVE_TAG_AFTER_LABEL.findAll(this@extractArchiveTagNames).forEach { match ->
        match.groupValues.getOrNull(1)?.normalizeArchiveTagName()?.takeIf { it.isNotBlank() }?.let(::add)
    }
    ARCHIVE_TAG_BEFORE_LABEL.findAll(this@extractArchiveTagNames).forEach { match ->
        match.groupValues.getOrNull(1)?.normalizeArchiveTagName()?.takeIf { it.isNotBlank() }?.let(::add)
    }
}.distinctBy { it.lowercase() }

internal fun String.toArchiveNoteReference(tagNames: List<String>, stripActionWords: Boolean): String {
    var value = cleanVoiceReference()
    tagNames.forEach { tagName ->
        val escaped = Regex.escape(tagName)
        value = value
            .replace(Regex("(?:标签|tag)\\s*(?:是|为|叫|名为|名称是|名称为|=|:|：)\\s*#?$escaped", RegexOption.IGNORE_CASE), "")
            .replace(Regex("#?$escaped\\s*(?:这个|的)?标签(?:下|里|中)?", RegexOption.IGNORE_CASE), "")
    }
    ARCHIVE_GENERIC_REFERENCE_NOISE.forEach { noise -> value = value.replace(noise, "") }
    if (stripActionWords) {
        ARCHIVE_ACTION_NOISE.forEach { noise -> value = value.replace(noise, "") }
    }
    return value
        .trim()
        .trim('"', '\'', '“', '”', '‘', '’')
        .replace(Regex("[\\s,，、#。！？!?:：;；/\\-_.《》<>（）()\\[\\]{}]+"), "")
        .trim()
}

private fun String.isMeaningfulArchiveReference(tagNames: List<String>): Boolean {
    val normalized = lowercase()
    if (normalized.isBlank()) return false
    if (normalized in ARCHIVE_GENERIC_REFERENCES) return false
    return tagNames.none { normalized == it.normalizeArchiveTagName().lowercase() }
}

private fun JSONObject.archiveExactTitle(): String = ARCHIVE_EXACT_TITLE_FIELDS
    .asSequence()
    .map { field -> optString(field, "").cleanVoiceReference() }
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun JSONObject.archiveRequestedIds(): List<Long> = buildList {
    optJSONArray("note_ids")?.let { array ->
        for (index in 0 until array.length()) {
            array.optLong(index, 0L).takeIf { it > 0L }?.let(::add)
        }
    }
    optLong("note_id", 0L).takeIf { it > 0L }?.let(::add)
}.distinct()

private fun String.normalizeArchiveTagName(): String = trim()
    .trimStart('#')
    .trim()
    .trimEnd('的')
    .trim()

private fun String.hasArchiveMultipleIntent(): Boolean = ARCHIVE_MULTIPLE_WORDS.any(::contains)

private fun McpToolResult.withArchiveTagContext(
    target: ArchiveTagTarget,
    notes: List<Note>,
    archived: Boolean,
): McpToolResult {
    val tagText = target.tagNames.joinToString("、") { "#$it" }
    val noteText = notes.take(3).joinToString("、") { "《${it.title.ifBlank { "未命名便签" }}》" }
        .let { summary -> if (notes.size > 3) "$summary 等 ${notes.size} 条" else summary }
    val action = if (archived) "归档" else "取消归档"
    val contextualMessage = when (statusEnum) {
        McpToolStatus.Success -> "已按标签 $tagText 精确定位并$action：$noteText"
        McpToolStatus.RequiresConfirmation -> "$message；按标签 $tagText 精确定位：$noteText"
        else -> "$message；标签定位条件：$tagText"
    }
    val contextualResult = runCatching {
        val root = resultJson?.takeIf { it.isNotBlank() }?.let(::JSONObject) ?: JSONObject()
        root.put("archive_target_mode", "tag_exact")
            .put("archive_target_tags", JSONArray(target.tagNames))
            .put("archive_target_titles", JSONArray(notes.map { it.title.ifBlank { "未命名便签" } }))
            .toString()
    }.getOrElse { resultJson }
    return copy(message = contextualMessage, resultJson = contextualResult)
}

private val ARCHIVE_NOTE_REFERENCE_FIELDS = listOf(
    "note_ref",
    "note_title",
    "target_title",
    "exact_title",
    "query",
    "title",
)
private val ARCHIVE_EXACT_TITLE_FIELDS = listOf("exact_title", "note_title", "target_title")
private val ARCHIVE_SINGLE_TAG_FIELDS = listOf("tag_name", "target_tag", "tag")
private val ARCHIVE_ARRAY_TAG_FIELDS = listOf("tag_names", "target_tags", "tags")
private val ARCHIVE_TAG_FIELDS = ARCHIVE_SINGLE_TAG_FIELDS + ARCHIVE_ARRAY_TAG_FIELDS
private val ARCHIVE_MULTIPLE_WORDS = listOf("全部", "所有", "这些", "一批", "每一条", "都归档")
private val ARCHIVE_GENERIC_REFERENCES = setOf("那", "这", "其中", "唯一", "一", "一个", "一条")
private val ARCHIVE_GENERIC_REFERENCE_NOISE = listOf(
    "那一条",
    "这一条",
    "其中一条",
    "唯一一条",
    "那条",
    "这条",
    "这个",
    "那个",
    "的便签",
    "的记录",
    "的笔记",
    "便签",
    "记录",
    "笔记",
    "一下",
)
private val ARCHIVE_ACTION_NOISE = listOf(
    "恢复到普通便签列表",
    "恢复到普通列表",
    "从已归档中恢复",
    "从归档中恢复",
    "从归档恢复",
    "移出归档",
    "取消归档",
    "归档起来",
    "归档",
    "全部",
    "所有",
    "这些",
    "帮我",
    "麻烦",
    "请",
    "把",
    "将",
    "给",
)
private val ARCHIVE_TAG_AFTER_LABEL = Regex(
    "(?:标签|tag)\\s*(?:是|为|叫|名为|名称是|名称为|=|:|：)\\s*#?([\\p{L}\\p{N}_-]{1,24}?)(?=那一条|这一条|那条|这条|的便签|便签|记录|笔记|归档|取消|恢复|全部|所有|这些|[，。！？、\\s]|$)",
    RegexOption.IGNORE_CASE,
)
private val ARCHIVE_TAG_BEFORE_LABEL = Regex(
    "(?:把|将|给|请|帮我)?\\s*#?([\\p{L}\\p{N}_-]{1,24}?)\\s*(?:这个|的)?标签(?=下|里|中|的|那一条|这一条|便签|记录|笔记|[，。！？、\\s]|$)",
    RegexOption.IGNORE_CASE,
)
