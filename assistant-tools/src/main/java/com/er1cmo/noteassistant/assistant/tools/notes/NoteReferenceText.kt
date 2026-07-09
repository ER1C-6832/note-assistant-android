package com.er1cmo.noteassistant.assistant.tools.notes

internal fun String.cleanVoiceReference(): String = trim()
    .trim('"')
    .trim('\'')
    .trim()

internal fun String.toAssistantSearchTerms(): List<String> {
    val original = cleanVoiceReference()
    if (original.isBlank()) return emptyList()
    val cleaned = original
        .replace("相关的便签", "")
        .replace("相关便签", "")
        .replace("相关的记录", "")
        .replace("相关记录", "")
        .replace("相关", "")
        .replace("有关", "")
        .replace("关于", "")
        .replace("便签", "")
        .replace("记录", "")
        .replace("笔记", "")
        .replace("有哪些", "")
        .replace("有什么", "")
        .replace("都有哪些", "")
        .replace("都有啥", "")
        .replace("列表", "")
        .replace("全部", "")
        .replace("所有", "")
        .replace("删掉", "")
        .replace("删除", "")
        .replace("查找", "")
        .replace("搜索", "")
        .replace("显示", "")
        .replace("打开", "")
        .replace("帮我", "")
        .replace("请", "")
        .replace("小智", "")
        .cleanVoiceReference()
        .removeSingleUseFiller()
    return listOf(original, cleaned)
        .map { it.cleanVoiceReference() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

internal fun String.isBroadNoteListRequest(): Boolean {
    val original = cleanVoiceReference()
    if (original.isBlank()) return false
    val hasSubject = listOf("便签", "记录", "笔记").any { original.contains(it) }
    val hasListIntent = listOf("全部", "所有", "有哪些", "有什么", "都有哪些", "都有啥", "列表").any { original.contains(it) }
    if (!hasSubject || !hasListIntent) return false
    return original.toBroadListRemainder().isBlank()
}

internal fun String.toBroadListRemainder(): String = cleanVoiceReference()
    .replace("全部", "")
    .replace("所有", "")
    .replace("有哪些", "")
    .replace("有什么", "")
    .replace("都有哪些", "")
    .replace("都有啥", "")
    .replace("列表", "")
    .replace("便签", "")
    .replace("记录", "")
    .replace("笔记", "")
    .replace("帮我", "")
    .replace("请", "")
    .replace("小智", "")
    .cleanVoiceReference()
    .removeSingleUseFiller()

private fun String.removeSingleUseFiller(): String {
    val value = cleanVoiceReference()
    return if (value == "用") "" else value
}

internal fun String.visibleTitleNormalize(): String = cleanVoiceReference()
    .lowercase()
    .replace(Regex("\\s+"), "")
