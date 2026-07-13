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
    private val customPhraseTester: WakeWordCustomPhraseTester,
) : WakeWordAudioGate {
    suspend fun restoreIfEnabled() {
        val settings = settingsRepository.current()
        if (!settings.enabled) return
        val current = coordinator.state.value.serviceState
        if (current in setOf(
                WakeWordServiceState.Starting,
                WakeWordServiceState.Initializing,
                WakeWordServiceState.Listening,
                WakeWordServiceState.Recovering,
            )
        ) {
            return
        }
        val config = WakeWordConfig.fromSettings(settings)
        coordinator.onServiceStarting(config.phrase.displayText)
        ContextCompat.startForegroundService(
            context,
            WakeWordForegroundService.startIntent(context),
        )
    }

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

    suspend fun verifyCustomPhrase(
        candidate: WakeWordPronunciationCandidate,
    ): WakeWordCustomCheckResult {
        val settings = settingsRepository.current()
        val resumeAfter = shouldTemporarilyPause(settings.enabled)
        if (resumeAfter && !pauseForAssistant("检查自定义唤醒词可用性")) {
            return WakeWordCustomCheckResult(false, "正式 KWS 未及时释放麦克风，检查已取消。")
        }
        return try {
            customPhraseTester.verifyStream(
                candidate = candidate,
                sensitivity = WakeWordSensitivity.fromName(settings.sensitivity),
            )
        } finally {
            if (resumeAfter) resumeAfterAssistant("自定义唤醒词检查完成")
        }
    }

    suspend fun testCustomPhrase(
        candidate: WakeWordPronunciationCandidate,
    ): WakeWordCustomTestResult {
        val settings = settingsRepository.current()
        val resumeAfter = shouldTemporarilyPause(settings.enabled)
        if (resumeAfter && !pauseForAssistant("自定义唤醒词本机测试")) {
            return WakeWordCustomTestResult(false, message = "正式 KWS 未及时释放麦克风，本机测试已取消。")
        }
        return try {
            customPhraseTester.listenForPhrase(
                candidate = candidate,
                sensitivity = WakeWordSensitivity.fromName(settings.sensitivity),
            )
        } finally {
            if (resumeAfter) resumeAfterAssistant("自定义唤醒词本机测试完成")
        }
    }

    suspend fun saveCustomPhrase(candidate: WakeWordPronunciationCandidate) {
        require(candidate.displayText.length in 2..6) { "自定义唤醒词必须为 2～6 个汉字" }
        settingsRepository.saveCustomPhrase(candidate.displayText, candidate.grammar)
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

    private fun shouldTemporarilyPause(enabled: Boolean): Boolean {
        if (!enabled) return false
        return coordinator.state.value.serviceState in setOf(
            WakeWordServiceState.Starting,
            WakeWordServiceState.Initializing,
            WakeWordServiceState.Listening,
        )
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
