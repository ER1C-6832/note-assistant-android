package com.er1cmo.noteassistant.assistant.wakeword

import com.er1cmo.noteassistant.app.settings.WakeWordSettingsSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WakeWordConfigTest {
    @Test
    fun presetSettingsMapToValidatedPhrase() {
        val config = WakeWordConfig.fromSettings(
            WakeWordSettingsSnapshot(
                enabled = true,
                presetId = WakeWordPreset.XiaozhiClassmate.name,
                sensitivity = WakeWordSensitivity.Conservative.name,
                cooldownMs = 2_500L,
            ),
        )

        assertEquals("小智同学", config.phrase.displayText)
        assertEquals(WakeWordPhraseType.Preset, config.phrase.type)
        assertEquals(2_500L, config.cooldownMs)
        assertEquals(WakeWordSensitivity.Conservative.keywordsThreshold, config.keywordsThreshold)
        assertTrue(config.phrase.grammar.contains("@小智同学"))
    }

    @Test
    fun customPhraseArchitectureConsumesStoredGrammar() {
        val config = WakeWordConfig.fromSettings(
            WakeWordSettingsSnapshot(
                enabled = true,
                phraseType = WakeWordPhraseType.Custom.storageValue,
                customText = "小泓同学",
                customGrammar = "x iǎo h óng t óng x ué @小泓同学",
            ),
        )

        assertEquals(WakeWordPhraseType.Custom, config.phrase.type)
        assertEquals("小泓同学", config.phrase.displayText)
        assertTrue(config.phrase.grammar.endsWith("@小泓同学"))
    }
}
