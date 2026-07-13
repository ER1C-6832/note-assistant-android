package com.er1cmo.noteassistant.stability

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwner
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Application-scoped system audio guard for Phase5-04.
 *
 * It deliberately avoids READ_PHONE_STATE. Calls and competing media sessions are
 * handled through Android audio focus and audio mode changes. Wired/Bluetooth route
 * removal is handled through ACTION_AUDIO_BECOMING_NOISY and AudioDeviceCallback.
 */
@Singleton
class AssistantSystemAudioCoordinator @Inject constructor(
    @ApplicationContext private val context: Context,
    private val assistantController: AssistantController,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val started = AtomicBoolean(false)
    private val interruptionRunning = AtomicBoolean(false)

    @Volatile private var focusHeld = false
    @Volatile private var recoveryPending = false
    @Volatile private var permissionBlocked = false
    @Volatile private var lastInterruptionAtMs = 0L

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (lastInterruptionAtMs > 0L) recoveryPending = true
            }
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                scheduleInterruption("audio_focus_$change")
            }
        }
    }

    private val focusRequest: AudioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
        )
        .setAcceptsDelayedFocusGain(false)
        .setOnAudioFocusChangeListener(focusListener)
        .build()

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                scheduleInterruption("audio_route_becoming_noisy")
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
            if (removedDevices.any(::isVoiceRouteDevice)) {
                scheduleInterruption("audio_route_removed")
            }
        }

        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            if (lastInterruptionAtMs > 0L && addedDevices.any(::isVoiceRouteDevice)) {
                recoveryPending = true
            }
        }
    }

    fun start() {
        if (!started.compareAndSet(false, true)) return

        ContextCompat.registerReceiver(
            context,
            noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))

        scope.launch {
            assistantController.state.collectLatest { state ->
                val shouldHoldFocus = state.streamingSessionActive ||
                    state.microphoneOwner == MicrophoneOwner.AssistantCapture ||
                    state.audio == AssistantAudioStatus.Playing ||
                    state.phase == AssistantPhase.Listening ||
                    state.phase == AssistantPhase.Thinking ||
                    state.phase == AssistantPhase.Speaking

                if (shouldHoldFocus && !focusHeld) {
                    val granted = requestFocus()
                    if (!granted) {
                        assistantController.handleSystemAudioInterruption(
                            reason = "audio_focus_request_denied",
                            resumeWakeWord = true,
                        )
                    }
                } else if (!shouldHoldFocus && focusHeld) {
                    abandonFocus()
                }
            }
        }

        scope.launch {
            while (isActive) {
                val state = assistantController.state.value
                val voiceActive = state.streamingSessionActive ||
                    state.microphoneOwner == MicrophoneOwner.AssistantCapture ||
                    state.audio == AssistantAudioStatus.Playing ||
                    state.phase == AssistantPhase.Listening ||
                    state.phase == AssistantPhase.Thinking ||
                    state.phase == AssistantPhase.Speaking
                val permissionGranted = hasRecordAudioPermission()

                if (voiceActive && !permissionGranted && !permissionBlocked) {
                    permissionBlocked = true
                    assistantController.handleSystemAudioInterruption(
                        reason = "record_audio_permission_revoked",
                        resumeWakeWord = false,
                    )
                }

                if (permissionBlocked && permissionGranted && !voiceActive) {
                    permissionBlocked = false
                    assistantController.handleSystemAudioRecovered("record_audio_permission_restored")
                }

                if (
                    recoveryPending &&
                    permissionGranted &&
                    !voiceActive &&
                    audioManager.mode == AudioManager.MODE_NORMAL &&
                    System.currentTimeMillis() - lastInterruptionAtMs >= RECOVERY_GUARD_MS
                ) {
                    recoveryPending = false
                    lastInterruptionAtMs = 0L
                    assistantController.handleSystemAudioRecovered("system_audio_recovered")
                }

                delay(HEALTH_POLL_MS)
            }
        }
    }

    private fun scheduleInterruption(reason: String) {
        val state = assistantController.state.value
        val voiceActive = state.streamingSessionActive ||
            state.microphoneOwner == MicrophoneOwner.AssistantCapture ||
            state.audio == AssistantAudioStatus.Playing ||
            state.phase == AssistantPhase.Listening ||
            state.phase == AssistantPhase.Thinking ||
            state.phase == AssistantPhase.Speaking
        if (!voiceActive || !interruptionRunning.compareAndSet(false, true)) return

        recoveryPending = true
        lastInterruptionAtMs = System.currentTimeMillis()
        scope.launch {
            try {
                assistantController.handleSystemAudioInterruption(
                    reason = reason,
                    resumeWakeWord = false,
                )
            } finally {
                interruptionRunning.set(false)
            }
        }
    }

    private fun requestFocus(): Boolean {
        val granted = audioManager.requestAudioFocus(focusRequest) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        focusHeld = granted
        return granted
    }

    private fun abandonFocus() {
        runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        focusHeld = false
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    private fun isVoiceRouteDevice(device: AudioDeviceInfo): Boolean = when (device.type) {
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> true
        else -> false
    }

    private companion object {
        const val HEALTH_POLL_MS = 500L
        const val RECOVERY_GUARD_MS = 1_500L
    }
}
