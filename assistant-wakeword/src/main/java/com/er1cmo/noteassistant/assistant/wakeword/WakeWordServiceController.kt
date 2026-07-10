package com.er1cmo.noteassistant.assistant.wakeword

import android.content.Context
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import com.er1cmo.noteassistant.core.common.audio.WakeWordAudioGate
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class WakeWordServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: WakeWordSettingsRepository,
    private val coordinator: WakeWordCoordinator,
) : WakeWordAudioGate {
    suspend fun setEnabled(enabled: Boolean) {
        settingsRepository.setEnabled(enabled)
        if (enabled) {
            val config = WakeWordConfig.fromSettings(settingsRepository.current())
            coordinator.onServiceStarting(config.phrase.displayText)
            ContextCompat.startForegroundService(
                context,
                WakeWordForegroundService.startIntent(context),
            )
        } else {
            context.startService(WakeWordForegroundService.stopIntent(context, "设置页关闭本地唤醒词"))
        }
    }

    suspend fun setPreset(preset: WakeWordPreset) {
        settingsRepository.setPreset(preset.name)
        updateIfEnabled()
    }

    suspend fun setSensitivity(sensitivity: WakeWordSensitivity) {
        settingsRepository.setSensitivity(sensitivity.name)
        updateIfEnabled()
    }

    suspend fun setCooldownMs(value: Long) {
        settingsRepository.setCooldownMs(value)
        updateIfEnabled()
    }

    fun pause(reason: String = "用户暂停本地唤醒词监听") {
        context.startService(WakeWordForegroundService.pauseIntent(context, reason))
    }

    suspend fun resume() {
        if (!settingsRepository.current().enabled) return
        ContextCompat.startForegroundService(
            context,
            WakeWordForegroundService.resumeIntent(context),
        )
    }

    override suspend fun pauseForAssistant(reason: String): Boolean {
        val settings = settingsRepository.current()
        if (!settings.enabled) return true

        context.startService(WakeWordForegroundService.pauseIntent(context, reason))
        return withTimeoutOrNull(PAUSE_TIMEOUT_MS) {
            coordinator.state.first { state ->
                state.microphoneOwner == WakeWordMicrophoneOwner.None &&
                    state.serviceState in setOf(
                        WakeWordServiceState.Paused,
                        WakeWordServiceState.Detected,
                        WakeWordServiceState.Stopped,
                        WakeWordServiceState.Disabled,
                        WakeWordServiceState.Error,
                    )
            }
            true
        } ?: false
    }

    override suspend fun resumeAfterAssistant(reason: String) {
        if (!settingsRepository.current().enabled) return
        ContextCompat.startForegroundService(
            context,
            WakeWordForegroundService.resumeIntent(context).putExtra(
                WakeWordForegroundService.EXTRA_REASON,
                reason,
            ),
        )
    }

    fun showAssistantStatus(status: String) {
        context.startService(WakeWordForegroundService.assistantStatusIntent(context, status))
    }

    suspend fun markFalseTrigger() {
        settingsRepository.markFalseTrigger()
    }

    suspend fun resetStatistics() {
        settingsRepository.resetStatistics()
    }

    private suspend fun updateIfEnabled() {
        if (!settingsRepository.current().enabled) return
        ContextCompat.startForegroundService(
            context,
            WakeWordForegroundService.updateIntent(context),
        )
    }

    private companion object {
        const val PAUSE_TIMEOUT_MS = 3_000L
    }
}
