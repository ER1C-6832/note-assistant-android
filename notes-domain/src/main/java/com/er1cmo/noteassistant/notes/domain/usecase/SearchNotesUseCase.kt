package com.er1cmo.noteassistant.notes.domain.usecase

import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteSearchField
import com.er1cmo.noteassistant.notes.domain.model.NoteSearchResult
import javax.inject.Inject

class SearchNotesUseCase @Inject constructor() {
    operator fun invoke(notes: List<Note>, query: String, limit: Int = DEFAULT_LIMIT): List<NoteSearchResult> {
        val normalizedQuery = query.searchNormalize()
        val baseResults = if (normalizedQuery.isBlank()) {
            notes.sortedWith(defaultComparator()).map { note ->
                NoteSearchResult(note = note, score = 0, matchedFields = emptySet())
            }
        } else {
            notes.mapNotNull { note -> note.matchSearch(normalizedQuery) }
                .sortedWith(
                    compareByDescending<NoteSearchResult> { it.score }
                        .thenByDescending { it.note.pinned }
                        .thenByDescending { it.note.updatedAt }
                        .thenByDescending { it.note.id },
                )
        }
        return if (limit > 0) baseResults.take(limit) else baseResults
    }

    fun filterAndSort(notes: List<Note>, query: String): List<Note> = invoke(notes = notes, query = query, limit = 0).map { it.note }

    private fun Note.matchSearch(query: String): NoteSearchResult? {
        val titleTokens = title.searchTokens()
        val contentTokens = content.searchTokens()
        val tagTokens = tags.flatMap { it.name.searchTokens() }
        val fields = mutableSetOf<NoteSearchField>()
        var score = 0

        when {
            titleTokens.any { it == query } -> {
                score = maxOf(score, 1000)
                fields += NoteSearchField.Title
            }
            titleTokens.any { it.contains(query) } -> {
                score = maxOf(score, 900)
                fields += NoteSearchField.Title
            }
        }
        when {
            tagTokens.any { it == query } -> {
                score = maxOf(score, 800)
                fields += NoteSearchField.Tag
            }
            tagTokens.any { it.contains(query) } -> {
                score = maxOf(score, 700)
                fields += NoteSearchField.Tag
            }
        }
        if (contentTokens.any { it.contains(query) }) {
            score = maxOf(score, 600)
            fields += NoteSearchField.Content
        }
        return if (score > 0) NoteSearchResult(note = this, score = score, matchedFields = fields) else null
    }

    private fun defaultComparator(): Comparator<Note> = compareByDescending<Note> { it.pinned }
        .thenByDescending { it.updatedAt }
        .thenByDescending { it.id }

    private fun String.searchTokens(): Set<String> {
        val normalized = searchNormalize()
        if (normalized.isBlank()) return emptySet()
        val pinyin = toPinyinText()
        val initials = toPinyinInitials()
        return setOf(normalized, pinyin, initials).filter { it.isNotBlank() }.toSet()
    }

    private fun String.searchNormalize(): String = trim().lowercase().replace(Regex("\\s+"), "")

    private fun String.toPinyinText(): String = buildString {
        for (char in this@toPinyinText) {
            append(PINYIN_TABLE[char] ?: char.lowercaseChar().toString())
        }
    }.searchNormalize()

    private fun String.toPinyinInitials(): String = buildString {
        for (char in this@toPinyinInitials) {
            val mapped = PINYIN_TABLE[char]
            append(mapped?.firstOrNull() ?: char.lowercaseChar())
        }
    }.searchNormalize()

    companion object {
        const val DEFAULT_LIMIT = 50

        private val PINYIN_TABLE = mapOf(
            '阿' to "a", '爱' to "ai", '安' to "an", '按' to "an", '昂' to "ang",
            '八' to "ba", '把' to "ba", '白' to "bai", '百' to "bai", '办' to "ban", '班' to "ban", '半' to "ban", '帮' to "bang", '包' to "bao", '报' to "bao", '备' to "bei", '本' to "ben", '便' to "bian", '标' to "biao", '别' to "bie", '部' to "bu",
            '才' to "cai", '菜' to "cai", '测' to "ce", '查' to "cha", '常' to "chang", '成' to "cheng", '重' to "chong", '出' to "chu", '传' to "chuan", '创' to "chuang", '次' to "ci", '从' to "cong", '存' to "cun",
            '打' to "da", '单' to "dan", '到' to "dao", '的' to "de", '等' to "deng", '地' to "di", '点' to "dian", '电' to "dian", '调' to "diao", '定' to "ding", '东' to "dong", '动' to "dong", '都' to "dou", '读' to "du", '对' to "dui", '多' to "duo",
            '发' to "fa", '方' to "fang", '分' to "fen", '风' to "feng", '服' to "fu", '复' to "fu",
            '改' to "gai", '该' to "gai", '干' to "gan", '刚' to "gang", '高' to "gao", '个' to "ge", '跟' to "gen", '更' to "geng", '工' to "gong", '公' to "gong", '功' to "gong", '关' to "guan", '归' to "gui", '过' to "guo",
            '还' to "hai", '行' to "hang", '好' to "hao", '和' to "he", '黑' to "hei", '后' to "hou", '户' to "hu", '回' to "hui", '会' to "hui",
            '机' to "ji", '记' to "ji", '件' to "jian", '检' to "jian", '建' to "jian", '将' to "jiang", '接' to "jie", '今' to "jin", '进' to "jin", '经' to "jing", '客' to "ke", '可' to "ke", '空' to "kong", '快' to "kuai",
            '拉' to "la", '来' to "lai", '览' to "lan", '老' to "lao", '了' to "le", '类' to "lei", '联' to "lian", '链' to "lian", '列' to "lie", '灵' to "ling", '流' to "liu", '录' to "lu",
            '码' to "ma", '买' to "mai", '名' to "ming", '明' to "ming", '命' to "ming", '幕' to "mu",
            '哪' to "na", '内' to "nei", '能' to "neng", '你' to "ni",
            '屏' to "ping", '评' to "ping", '普' to "pu",
            '签' to "qian", '前' to "qian", '强' to "qiang", '清' to "qing", '确' to "que",
            '人' to "ren", '日' to "ri", '软' to "ruan",
            '删' to "shan", '上' to "shang", '少' to "shao", '设' to "she", '升' to "sheng", '声' to "sheng", '时' to "shi", '首' to "shou", '手' to "shou", '数' to "shu", '搜' to "sou", '索' to "suo",
            '他' to "ta", '它' to "ta", '太' to "tai", '天' to "tian", '条' to "tiao", '同' to "tong", '通' to "tong", '图' to "tu",
            '完' to "wan", '王' to "wang", '文' to "wen", '问' to "wen", '我' to "wo",
            '系' to "xi", '下' to "xia", '小' to "xiao", '新' to "xin", '信' to "xin", '修' to "xiu", '需' to "xu", '选' to "xuan",
            '验' to "yan", '样' to "yang", '要' to "yao", '也' to "ye", '页' to "ye", '已' to "yi", '移' to "yi", '用' to "yong", '右' to "you", '语' to "yu", '源' to "yuan",
            '再' to "zai", '在' to "zai", '早' to "zao", '增' to "zeng", '摘' to "zhai", '占' to "zhan", '找' to "zhao", '置' to "zhi", '中' to "zhong", '总' to "zong", '走' to "zou", '左' to "zuo",
        )
    }
}
