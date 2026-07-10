package com.er1cmo.noteassistant.assistant.wakeword

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import net.sourceforge.pinyin4j.PinyinHelper
import net.sourceforge.pinyin4j.format.HanyuPinyinCaseType
import net.sourceforge.pinyin4j.format.HanyuPinyinOutputFormat
import net.sourceforge.pinyin4j.format.HanyuPinyinToneType
import net.sourceforge.pinyin4j.format.HanyuPinyinVCharType

data class WakeWordPronunciationCandidate(
    val id: String,
    val displayText: String,
    val syllables: List<String>,
    val tokenSequence: List<String>,
    val grammar: String,
) {
    val pronunciationLabel: String
        get() = syllables.joinToString(" ")

    fun asPhrase(): WakeWordPhrase = WakeWordPhrase(
        id = "custom:$id",
        type = WakeWordPhraseType.Custom,
        displayText = displayText,
        grammar = grammar,
    )
}

sealed interface WakeWordCompileResult {
    data class Success(
        val normalizedText: String,
        val candidates: List<WakeWordPronunciationCandidate>,
        val candidateLimitReached: Boolean,
    ) : WakeWordCompileResult

    data class Failure(val message: String) : WakeWordCompileResult
}

@Singleton
class WakeWordGrammarCompiler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val pinyinFormat = HanyuPinyinOutputFormat().apply {
        caseType = HanyuPinyinCaseType.LOWERCASE
        toneType = HanyuPinyinToneType.WITH_TONE_MARK
        vCharType = HanyuPinyinVCharType.WITH_U_UNICODE
    }

    private val modelTokens: Set<String> by lazy {
        context.assets.open("${WakeWordConfig.MODEL_DIR}/tokens.txt")
            .bufferedReader(Charsets.UTF_8)
            .useLines { lines ->
                lines.mapNotNull { line ->
                    line.trim()
                        .takeIf { it.isNotBlank() }
                        ?.split(Regex("\\s+"), limit = 2)
                        ?.firstOrNull()
                }.toSet()
            }
    }

    fun compile(rawText: String): WakeWordCompileResult {
        val text = rawText.trim()
        validateText(text)?.let { return WakeWordCompileResult.Failure(it) }

        val optionsByCharacter = text.map { character ->
            runCatching {
                PinyinHelper.toHanyuPinyinStringArray(character, pinyinFormat)
                    ?.map(::normalizeSyllable)
                    ?.filter { it.isNotBlank() }
                    ?.distinct()
                    .orEmpty()
            }.getOrDefault(emptyList())
        }
        if (optionsByCharacter.any { it.isEmpty() }) {
            return WakeWordCompileResult.Failure("部分汉字无法生成拼音，请更换较常用的汉字。")
        }

        var combinations: List<List<String>> = listOf(emptyList())
        var truncated = false
        optionsByCharacter.forEach { options ->
            val expanded = combinations.flatMap { prefix -> options.map { prefix + it } }
            if (expanded.size > MAX_CANDIDATES) truncated = true
            combinations = expanded.take(MAX_CANDIDATES)
        }

        val unsupportedDetails = linkedSetOf<String>()
        val candidates = combinations.mapNotNull { syllables ->
            val tokenSequence = syllables.flatMap { syllable ->
                pinyinSyllableToTokens(syllable) ?: return@mapNotNull null
            }
            val unsupported = tokenSequence.filterNot(modelTokens::contains).distinct()
            if (unsupported.isNotEmpty()) {
                unsupportedDetails += unsupported
                return@mapNotNull null
            }
            val grammar = "${tokenSequence.joinToString(" ")} @$text"
            WakeWordPronunciationCandidate(
                id = tokenSequence.joinToString("-").lowercase(Locale.ROOT),
                displayText = text,
                syllables = syllables,
                tokenSequence = tokenSequence,
                grammar = grammar,
            )
        }.distinctBy { it.grammar }

        if (candidates.isEmpty()) {
            val detail = unsupportedDetails.takeIf { it.isNotEmpty() }
                ?.joinToString(prefix = "（不支持 token：", postfix = "）")
                .orEmpty()
            return WakeWordCompileResult.Failure(
                "生成的拼音不在当前 sherpa tokens.txt 中$detail，原唤醒词不会被覆盖。",
            )
        }
        return WakeWordCompileResult.Success(
            normalizedText = text,
            candidates = candidates,
            candidateLimitReached = truncated,
        )
    }

    private fun validateText(text: String): String? {
        if (text.isBlank()) return "请输入 2～6 个常用汉字。"
        if (!HAN_ONLY.matches(text)) {
            return "仅支持 2～6 个汉字；暂不支持数字、标点、空格或中英文混合。"
        }
        return null
    }

    private fun normalizeSyllable(value: String): String = value
        .trim()
        .lowercase(Locale.ROOT)
        .replace("u:", "ü")
        .replace('v', 'ü')
        .filter { character -> character.isLetter() }

    private fun pinyinSyllableToTokens(syllable: String): List<String>? {
        val normalized = normalizeSyllable(syllable)
        if (normalized.isBlank()) return null
        val initial = INITIALS.firstOrNull { candidate ->
            normalized.startsWith(candidate) && normalized.length > candidate.length
        }
        val final = if (initial == null) normalized else normalized.removePrefix(initial)
        if (final.isBlank()) return null
        return if (initial == null) listOf(final) else listOf(initial, final)
    }

    private companion object {
        const val MAX_CANDIDATES = 16
        val HAN_ONLY = Regex("^[\\u3400-\\u4DBF\\u4E00-\\u9FFF]{2,6}$")
        val INITIALS = listOf(
            "zh", "ch", "sh",
            "b", "p", "m", "f", "d", "t", "n", "l", "g", "k", "h",
            "j", "q", "x", "r", "z", "c", "s", "y", "w",
        )
    }
}
