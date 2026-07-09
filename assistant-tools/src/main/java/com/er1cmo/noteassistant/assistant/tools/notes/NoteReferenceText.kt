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
        .replace("删掉", "")
        .replace("删除", "")
        .replace("查找", "")
        .replace("搜索", "")
        .replace("帮我", "")
        .replace("请", "")
        .replace("小智", "")
        .cleanVoiceReference()
    return listOf(original, cleaned)
        .map { it.cleanVoiceReference() }
        .filter { it.isNotBlank() }
        .distinctBy { it.lowercase() }
}

internal fun String.visibleTitleNormalize(): String = cleanVoiceReference()
    .lowercase()
    .replace(Regex("\\s+"), "")
