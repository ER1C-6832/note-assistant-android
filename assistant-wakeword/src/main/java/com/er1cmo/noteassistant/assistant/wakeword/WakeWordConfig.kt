package com.er1cmo.noteassistant.assistant.wakeword

import com.er1cmo.noteassistant.app.settings.WakeWordSettingsSnapshot

enum class WakeWordPhraseType(val storageValue: String) {
    Preset("preset"),
    Custom("custom");

    companion object {
        fun fromStorage(value: String?): WakeWordPhraseType =
            values().firstOrNull { it.storageValue == value } ?: Preset
    }
}

data class WakeWordPhrase(
    val id: String,
    val type: WakeWordPhraseType,
    val displayText: String,
    val grammar: String,
) {
    init {
        require(id.isNotBlank()) { "Wake-word phrase id cannot be blank" }
        require(displayText.isNotBlank()) { "Wake-word display text cannot be blank" }
        require(grammar.isNotBlank()) { "Wake-word grammar cannot be blank" }
    }
}

enum class WakeWordPreset(
    val displayName: String,
    val description: String,
    val grammar: String,
) {
    Xiaozhi(
        displayName = "小智",
        description = "默认短唤醒词，命中快；嘈杂环境建议使用更保守灵敏度。",
        grammar = "x iǎo zh ì @小智/" +
            "x iǎo zh ī @小智/" +
            "x iǎo zh í @小智/" +
            "x iǎo zh ǐ @小智/" +
            "x iǎo z ì @小智/" +
            "x iǎo z ī @小智",
    ),
    XiaozhiRepeated(
        displayName = "小智小智",
        description = "重复唤醒词，抗误触更好。",
        grammar = "x iǎo zh ì x iǎo zh ì @小智小智/" +
            "x iǎo zh ì x iǎo zh ī @小智小智/" +
            "x iǎo zh ī x iǎo zh ī @小智小智/" +
            "x iǎo z ì x iǎo z ì @小智小智",
    ),
    XiaozhiClassmate(
        displayName = "小智同学",
        description = "完整称呼，误触最低。",
        grammar = "x iǎo zh ì t óng x ué @小智同学/" +
            "x iǎo zh ī t óng x ué @小智同学/" +
            "x iǎo z ì t óng x ué @小智同学",
    );

    fun asPhrase(): WakeWordPhrase = WakeWordPhrase(
        id = name,
        type = WakeWordPhraseType.Preset,
        displayText = displayName,
        grammar = grammar,
    )

    companion object {
        fun fromName(value: String?): WakeWordPreset =
            values().firstOrNull { it.name == value } ?: Xiaozhi
    }
}

enum class WakeWordSensitivity(
    val label: String,
    val description: String,
    val keywordsScore: Float,
    val keywordsThreshold: Float,
    val numTrailingBlanks: Int,
) {
    Conservative(
        label = "保守",
        description = "降低误触发，适合电视外放或嘈杂环境。",
        keywordsScore = 1.9f,
        keywordsThreshold = 0.32f,
        numTrailingBlanks = 1,
    ),
    Standard(
        label = "标准",
        description = "默认推荐，兼顾唤醒率和误触发。",
        keywordsScore = 1.6f,
        keywordsThreshold = 0.25f,
        numTrailingBlanks = 1,
    ),
    Sensitive(
        label = "灵敏",
        description = "更容易唤醒，安静环境可用。",
        keywordsScore = 1.2f,
        keywordsThreshold = 0.18f,
        numTrailingBlanks = 1,
    );

    companion object {
        fun fromName(value: String?): WakeWordSensitivity =
            values().firstOrNull { it.name == value } ?: Standard
    }
}

data class WakeWordConfig(
    val phrase: WakeWordPhrase = WakeWordPreset.Xiaozhi.asPhrase(),
    val sampleRate: Int = 16_000,
    val frameMs: Int = 100,
    val cooldownMs: Long = DEFAULT_COOLDOWN_MS,
    val callbackDelayAfterHitMs: Long = 300L,
    val keywordsScore: Float = WakeWordSensitivity.Standard.keywordsScore,
    val keywordsThreshold: Float = WakeWordSensitivity.Standard.keywordsThreshold,
    val numTrailingBlanks: Int = WakeWordSensitivity.Standard.numTrailingBlanks,
    val sensitivityLabel: String = WakeWordSensitivity.Standard.label,
) {
    val samplesPerFrame: Int = sampleRate * frameMs / 1_000

    companion object {
        const val DEFAULT_COOLDOWN_MS = 1_500L
        const val MODEL_DIR = "sherpa-onnx-kws-zipformer-zh-en-3M-2025-12-20"
        const val KEYWORDS_FILE = "$MODEL_DIR/keywords_xiaozhi.txt"

        fun fromSettings(settings: WakeWordSettingsSnapshot): WakeWordConfig {
            val phraseType = WakeWordPhraseType.fromStorage(settings.phraseType)
            val preset = WakeWordPreset.fromName(settings.presetId)
            val phrase = if (phraseType == WakeWordPhraseType.Custom) {
                require(settings.customText.isNotBlank()) { "自定义唤醒词文本不能为空" }
                require(settings.customGrammar.isNotBlank()) { "自定义唤醒词 grammar 不能为空" }
                WakeWordPhrase(
                    id = "custom:${settings.customText}",
                    type = WakeWordPhraseType.Custom,
                    displayText = settings.customText.trim(),
                    grammar = settings.customGrammar.trim(),
                )
            } else {
                preset.asPhrase()
            }
            val sensitivity = WakeWordSensitivity.fromName(settings.sensitivity)
            return WakeWordConfig(
                phrase = phrase,
                cooldownMs = settings.cooldownMs.coerceIn(500L, 10_000L),
                keywordsScore = sensitivity.keywordsScore,
                keywordsThreshold = sensitivity.keywordsThreshold,
                numTrailingBlanks = sensitivity.numTrailingBlanks,
                sensitivityLabel = sensitivity.label,
            )
        }
    }
}
