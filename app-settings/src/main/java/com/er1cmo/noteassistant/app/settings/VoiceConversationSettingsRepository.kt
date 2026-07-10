package com.er1cmo.noteassistant.app.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.voiceConversationDataStore by preferencesDataStore(name = "voice_conversation_settings")

data class VoiceConversationSettingsSnapshot(
    val defaultMode: String = MODE_STREAMING,
    val streamingIdleTimeoutMs: Long = DEFAULT_STREAMING_IDLE_TIMEOUT_MS,
    val streamingBargeInEnabled: Boolean = false,
    val mixedGestureShortcutEnabled: Boolean = false,
) {
    companion object {
        const val MODE_HOLD_TO_TALK = "hold_to_talk"
        const val MODE_STREAMING = "streaming_conversation"
        const val DEFAULT_STREAMING_IDLE_TIMEOUT_MS = 15_000L
    }
}

@Singleton
class VoiceConversationSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val defaultMode = stringPreferencesKey("voice_interaction_mode")
        val streamingIdleTimeoutMs = longPreferencesKey("streaming_idle_timeout_ms")
        val streamingBargeInEnabled = booleanPreferencesKey("streaming_barge_in_enabled")
        val mixedGestureShortcutEnabled = booleanPreferencesKey("mixed_gesture_shortcut_enabled")
    }

    val settings: Flow<VoiceConversationSettingsSnapshot> = context.voiceConversationDataStore.data.map { prefs ->
        VoiceConversationSettingsSnapshot(
            defaultMode = prefs[Keys.defaultMode] ?: VoiceConversationSettingsSnapshot.MODE_STREAMING,
            streamingIdleTimeoutMs = (prefs[Keys.streamingIdleTimeoutMs]
                ?: VoiceConversationSettingsSnapshot.DEFAULT_STREAMING_IDLE_TIMEOUT_MS)
                .coerceIn(MIN_IDLE_TIMEOUT_MS, MAX_IDLE_TIMEOUT_MS),
            streamingBargeInEnabled = prefs[Keys.streamingBargeInEnabled] ?: false,
            mixedGestureShortcutEnabled = prefs[Keys.mixedGestureShortcutEnabled] ?: false,
        )
    }

    suspend fun current(): VoiceConversationSettingsSnapshot = settings.first()

    suspend fun setDefaultMode(mode: String) {
        require(mode == VoiceConversationSettingsSnapshot.MODE_HOLD_TO_TALK ||
            mode == VoiceConversationSettingsSnapshot.MODE_STREAMING) {
            "Unsupported voice interaction mode: $mode"
        }
        context.voiceConversationDataStore.edit { it[Keys.defaultMode] = mode }
    }

    suspend fun setStreamingIdleTimeoutMs(value: Long) {
        context.voiceConversationDataStore.edit {
            it[Keys.streamingIdleTimeoutMs] = value.coerceIn(MIN_IDLE_TIMEOUT_MS, MAX_IDLE_TIMEOUT_MS)
        }
    }

    suspend fun setStreamingBargeInEnabled(enabled: Boolean) {
        context.voiceConversationDataStore.edit { it[Keys.streamingBargeInEnabled] = enabled }
    }

    suspend fun setMixedGestureShortcutEnabled(enabled: Boolean) {
        context.voiceConversationDataStore.edit { it[Keys.mixedGestureShortcutEnabled] = enabled }
    }

    private companion object {
        const val MIN_IDLE_TIMEOUT_MS = 5_000L
        const val MAX_IDLE_TIMEOUT_MS = 60_000L
    }
}
