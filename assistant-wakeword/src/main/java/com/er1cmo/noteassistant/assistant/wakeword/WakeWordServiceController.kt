package com.er1cmo.noteassistant.assistant.wakeword

import android.content.Context
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WakeWordServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsRepository: WakeWordSettingsRepository,
    private val coordinator: WakeWordCoordinator,
) {
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
}
