package com.er1cmo.noteassistant.assistant.tools.notes

internal fun String.cleanVoiceReference(): String = trim()
    .trim('"')
    .trim('\'')
    .trim('“')
    .trim('”')
    .trim('‘')
    .trim('’')
    .trim()

internal fun String.toAssistantSearchTerms(): List<String> {
    val original = cleanVoiceReference()
    if (original.isBlank()) return emptyList()
    val reference = original.toAssistantReferenceText()
    return buildList {
        if (reference.isNotBlank()) add(reference)
        val compactOriginal = original.cleanVoiceReference()
        if (compactOriginal.isNotBlank() && compactOriginal != reference && !original.looksLikeMutationOrUiCommand()) {
            add(compactOriginal)
        }
    }.distinctBy { it.lowercase() }
}

internal fun String.toAssistantReferenceText(): String {
    var value = cleanVoiceReference()
    if (value.isBlank()) return ""

    // Remove long action phrases first. This turns phrases such as
    // “把手柄便签标记完成” into “手柄” instead of searching the full command text.
    val phraseNoise = listOf(
        "标记为已完成", "标记成已完成", "标记为完成", "标记成完成", "标记已完成", "标记完成",
        "设为已完成", "设置为已完成", "设成已完成", "设置成已完成", "设为完成", "设置为完成",
        "变成已完成", "改成已完成", "改为已完成", "转为已完成", "转成已完成",
        "变成完成", "改成完成", "改为完成", "转为完成", "转成完成",
        "打勾完成", "勾选完成", "勾选为完成", "勾选为已完成",
        "变成待办", "改成待办", "改为待办", "转为待办", "转成待办", "转换为待办", "转换成待办",
        "变回普通便签", "改回普通便签", "转回普通便签", "转换为普通便签", "转换成普通便签",
        "取消完成", "取消已完成", "取消标记完成", "取消标记为完成",
        "追加一句", "补充一句", "补一句", "加一句", "追加内容", "补充内容",
        "打开看看", "打开一下", "打开便签", "显示出来", "显示一下",
        "相关的便签", "相关便签", "相关的记录", "相关记录",
        "全部便签有哪些", "全部用便签有哪些", "所有便签有哪些", "全部笔记有哪些", "所有笔记有哪些",
    )
    phraseNoise.forEach { value = value.replace(it, "") }

    val singleNoise = listOf(
        "小智", "帮我", "麻烦", "请", "把", "给", "将", "让", "一下", "一个", "一条", "这条", "那条",
        "刚才那条", "刚刚那条", "刚才", "刚刚", "当前", "这个", "那个", "的",
        "相关", "有关", "关于", "便签", "记录", "笔记", "事项",
        "删除", "删掉", "移除", "查找", "搜索", "找", "显示", "打开", "列出", "看看",
        "有哪些", "有什么", "都有哪些", "都有啥", "列表", "全部", "所有",
    )
    singleNoise.forEach { value = value.replace(it, "") }

    return value.cleanVoiceReference()
        .removeSingleUseFiller()
        .stripReferencePunctuation()
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
    .stripReferencePunctuation()

internal fun String.visibleTitleNormalize(): String = cleanVoiceReference()
    .lowercase()
    .stripReferencePunctuation()

private fun String.looksLikeMutationOrUiCommand(): Boolean {
    val value = cleanVoiceReference()
    return listOf(
        "标记", "完成", "已完成", "待办", "普通", "删除", "删掉", "追加", "补充", "打开", "显示", "搜索", "查找", "归档", "恢复", "置顶",
    ).any { value.contains(it) }
}

private fun String.removeSingleUseFiller(): String {
    val value = cleanVoiceReference()
    return if (value == "用") "" else value
}

private fun String.stripReferencePunctuation(): String = lowercase()
    .replace(Regex("[\\s,，、#。！？!?:：;；/\\-_.《》<>\uff08\uff09()\\[\\]{}]+"), "")
    .cleanVoiceReference()
