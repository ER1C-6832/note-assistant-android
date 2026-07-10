package com.er1cmo.noteassistant.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.wakeWordSettingsDataStore by preferencesDataStore(name = "wakeword_settings")

data class WakeWordSettingsSnapshot(
    val enabled: Boolean = false,
    val phraseType: String = "preset",
    val presetId: String = "Xiaozhi",
    val customText: String = "",
    val customGrammar: String = "",
    val sensitivity: String = "Standard",
    val cooldownMs: Long = 1_500L,
    val totalHitCount: Int = 0,
    val falseTriggerCount: Int = 0,
    val cooldownIgnoredCount: Int = 0,
)

@Singleton
class WakeWordSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val enabled = booleanPreferencesKey("wakeword_enabled")
        val phraseType = stringPreferencesKey("wakeword_phrase_type")
        val presetId = stringPreferencesKey("wakeword_preset_id")
        val customText = stringPreferencesKey("wakeword_custom_text")
        val customGrammar = stringPreferencesKey("wakeword_custom_grammar")
        val sensitivity = stringPreferencesKey("wakeword_sensitivity")
        val cooldownMs = longPreferencesKey("wakeword_cooldown_ms")
        val totalHitCount = intPreferencesKey("wakeword_total_hit_count")
        val falseTriggerCount = intPreferencesKey("wakeword_false_trigger_count")
        val cooldownIgnoredCount = intPreferencesKey("wakeword_cooldown_ignored_count")
    }

    val settings: Flow<WakeWordSettingsSnapshot> = context.wakeWordSettingsDataStore.data.map { prefs ->
        WakeWordSettingsSnapshot(
            enabled = prefs[Keys.enabled] ?: false,
            phraseType = prefs[Keys.phraseType] ?: "preset",
            presetId = prefs[Keys.presetId] ?: "Xiaozhi",
            customText = prefs[Keys.customText].orEmpty(),
            customGrammar = prefs[Keys.customGrammar].orEmpty(),
            sensitivity = prefs[Keys.sensitivity] ?: "Standard",
            cooldownMs = prefs[Keys.cooldownMs] ?: 1_500L,
            totalHitCount = prefs[Keys.totalHitCount] ?: 0,
            falseTriggerCount = prefs[Keys.falseTriggerCount] ?: 0,
            cooldownIgnoredCount = prefs[Keys.cooldownIgnoredCount] ?: 0,
        )
    }

    suspend fun current(): WakeWordSettingsSnapshot = settings.first()

    suspend fun setEnabled(enabled: Boolean) {
        context.wakeWordSettingsDataStore.edit { it[Keys.enabled] = enabled }
    }

    suspend fun setPreset(presetId: String) {
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.phraseType] = "preset"
            prefs[Keys.presetId] = presetId
        }
    }

    suspend fun saveCustomPhrase(text: String, grammar: String) {
        require(text.isNotBlank() && grammar.isNotBlank()) { "Custom wake-word text and grammar are required" }
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.phraseType] = "custom"
            prefs[Keys.customText] = text.trim()
            prefs[Keys.customGrammar] = grammar.trim()
        }
    }

    suspend fun setSensitivity(value: String) {
        context.wakeWordSettingsDataStore.edit { it[Keys.sensitivity] = value }
    }

    suspend fun setCooldownMs(value: Long) {
        context.wakeWordSettingsDataStore.edit { it[Keys.cooldownMs] = value.coerceIn(500L, 10_000L) }
    }

    suspend fun incrementHitCount() {
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.totalHitCount] = (prefs[Keys.totalHitCount] ?: 0) + 1
        }
    }

    suspend fun incrementCooldownIgnoredCount() {
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.cooldownIgnoredCount] = (prefs[Keys.cooldownIgnoredCount] ?: 0) + 1
        }
    }

    suspend fun markFalseTrigger() {
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.falseTriggerCount] = (prefs[Keys.falseTriggerCount] ?: 0) + 1
        }
    }

    suspend fun resetStatistics() {
        context.wakeWordSettingsDataStore.edit { prefs ->
            prefs[Keys.totalHitCount] = 0
            prefs[Keys.falseTriggerCount] = 0
            prefs[Keys.cooldownIgnoredCount] = 0
        }
    }
}
