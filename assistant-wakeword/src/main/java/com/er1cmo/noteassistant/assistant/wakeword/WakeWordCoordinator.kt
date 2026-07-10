package com.er1cmo.noteassistant.assistant.wakeword

import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class WakeWordServiceState {
    Disabled,
    Starting,
    Initializing,
    Listening,
    Paused,
    Detected,
    Error,
    Stopped,
}

enum class WakeWordMicrophoneOwner {
    None,
    WakeWordKws,
}

data class WakeWordState(
    val enabled: Boolean = false,
    val serviceState: WakeWordServiceState = WakeWordServiceState.Disabled,
    val microphoneOwner: WakeWordMicrophoneOwner = WakeWordMicrophoneOwner.None,
    val phraseType: WakeWordPhraseType = WakeWordPhraseType.Preset,
    val preset: WakeWordPreset = WakeWordPreset.Xiaozhi,
    val activePhrase: String = WakeWordPreset.Xiaozhi.displayName,
    val sensitivity: WakeWordSensitivity = WakeWordSensitivity.Standard,
    val cooldownMs: Long = WakeWordConfig.DEFAULT_COOLDOWN_MS,
    val lastStatus: String = "本地唤醒词未开启",
    val lastDetectedKeyword: String = "暂无",
    val lastDetectionLatencyMs: Long? = null,
    val hitCount: Int = 0,
    val falseTriggerCount: Int = 0,
    val cooldownIgnoredCount: Int = 0,
    val errorMessage: String? = null,
)

@Singleton
class WakeWordCoordinator @Inject constructor(
    private val settingsRepository: WakeWordSettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mutableState = MutableStateFlow(WakeWordState())
    private val mutableDetections = MutableSharedFlow<WakeWordEvent.Detected>(extraBufferCapacity = 1)
    val state: StateFlow<WakeWordState> = mutableState.asStateFlow()
    val detections: SharedFlow<WakeWordEvent.Detected> = mutableDetections.asSharedFlow()

    init {
        scope.launch {
            settingsRepository.settings.collect { settings ->
                val preset = WakeWordPreset.fromName(settings.presetId)
                val phraseType = WakeWordPhraseType.fromStorage(settings.phraseType)
                mutableState.update { current ->
                    current.copy(
                        enabled = settings.enabled,
                        phraseType = phraseType,
                        preset = preset,
                        activePhrase = if (
                            phraseType == WakeWordPhraseType.Custom && settings.customText.isNotBlank()
                        ) settings.customText else preset.displayName,
                        sensitivity = WakeWordSensitivity.fromName(settings.sensitivity),
                        cooldownMs = settings.cooldownMs,
                        hitCount = settings.totalHitCount,
                        falseTriggerCount = settings.falseTriggerCount,
                        cooldownIgnoredCount = settings.cooldownIgnoredCount,
                        serviceState = if (!settings.enabled && current.serviceState != WakeWordServiceState.Error) {
                            WakeWordServiceState.Disabled
                        } else {
                            current.serviceState
                        },
                    )
                }
            }
        }
    }

    fun onServiceStarting(phrase: String) {
        mutableState.update {
            it.copy(
                serviceState = WakeWordServiceState.Starting,
                microphoneOwner = WakeWordMicrophoneOwner.None,
                activePhrase = phrase,
                lastStatus = "唤醒词前台服务启动中：$phrase",
                errorMessage = null,
            )
        }
    }

    fun onServicePaused(reason: String) {
        mutableState.update {
            it.copy(
                serviceState = WakeWordServiceState.Paused,
                microphoneOwner = WakeWordMicrophoneOwner.None,
                lastStatus = reason,
            )
        }
    }

    fun onServiceStopped(reason: String) {
        mutableState.update {
            it.copy(
                serviceState = if (it.enabled) WakeWordServiceState.Stopped else WakeWordServiceState.Disabled,
                microphoneOwner = WakeWordMicrophoneOwner.None,
                lastStatus = reason,
            )
        }
    }

    fun onEvent(event: WakeWordEvent) {
        when (event) {
            is WakeWordEvent.Detected -> {
                mutableState.update {
                    it.copy(
                        serviceState = WakeWordServiceState.Detected,
                        microphoneOwner = WakeWordMicrophoneOwner.None,
                        lastStatus = "已唤醒：${event.rawKeyword}；正在交接流式对话",
                        lastDetectedKeyword = event.rawKeyword,
                        lastDetectionLatencyMs = event.latencyMs,
                        errorMessage = null,
                    )
                }
                mutableDetections.tryEmit(event)
                scope.launch { settingsRepository.incrementHitCount() }
            }

            is WakeWordEvent.Status -> {
                val serviceState = when (event.state) {
                    "initializing" -> WakeWordServiceState.Initializing
                    "listening", "audio_open" -> WakeWordServiceState.Listening
                    "cooldown" -> WakeWordServiceState.Listening
                    "audio_released" -> mutableState.value.serviceState
                    "stopped" -> WakeWordServiceState.Stopped
                    else -> mutableState.value.serviceState
                }
                val owner = when (event.state) {
                    "listening", "audio_open", "cooldown" -> WakeWordMicrophoneOwner.WakeWordKws
                    "audio_released", "stopped" -> WakeWordMicrophoneOwner.None
                    else -> mutableState.value.microphoneOwner
                }
                mutableState.update {
                    it.copy(
                        serviceState = serviceState,
                        microphoneOwner = owner,
                        activePhrase = event.keyword.ifBlank { it.activePhrase },
                        lastStatus = event.message,
                        lastDetectionLatencyMs = event.latencyMs ?: it.lastDetectionLatencyMs,
                        errorMessage = null,
                    )
                }
                if (event.state == "cooldown") {
                    scope.launch { settingsRepository.incrementCooldownIgnoredCount() }
                }
            }

            is WakeWordEvent.Error -> {
                mutableState.update {
                    it.copy(
                        serviceState = WakeWordServiceState.Error,
                        microphoneOwner = WakeWordMicrophoneOwner.None,
                        lastStatus = event.message,
                        errorMessage = event.message,
                    )
                }
            }
        }
    }
}
