package com.er1cmo.noteassistant.core.common.audio

import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class MicrophoneOwner {
    None,
    WakeWordKws,
    AssistantCapture,
}

data class MicrophoneOwnershipState(
    val owner: MicrophoneOwner = MicrophoneOwner.None,
    val reason: String = "idle",
    val acquiredAtMs: Long? = null,
    val generation: Long = 0L,
)

data class MicrophoneLease internal constructor(
    val owner: MicrophoneOwner,
    internal val generation: Long,
)

@Singleton
class MicrophoneOwnershipCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val generation = AtomicLong(0L)
    private val mutableState = MutableStateFlow(MicrophoneOwnershipState())

    val state: StateFlow<MicrophoneOwnershipState> = mutableState.asStateFlow()

    suspend fun acquire(
        owner: MicrophoneOwner,
        reason: String,
        timeoutMs: Long = DEFAULT_ACQUIRE_TIMEOUT_MS,
    ): MicrophoneLease? {
        require(owner != MicrophoneOwner.None) { "MicrophoneOwner.None cannot acquire the microphone" }

        return withTimeoutOrNull(timeoutMs) {
            while (true) {
                val lease = mutex.withLock {
                    val current = mutableState.value
                    if (current.owner == MicrophoneOwner.None) {
                        val nextGeneration = generation.incrementAndGet()
                        mutableState.value = MicrophoneOwnershipState(
                            owner = owner,
                            reason = reason,
                            acquiredAtMs = System.currentTimeMillis(),
                            generation = nextGeneration,
                        )
                        MicrophoneLease(owner = owner, generation = nextGeneration)
                    } else {
                        null
                    }
                }
                if (lease != null) return@withTimeoutOrNull lease
                mutableState.first { it.owner == MicrophoneOwner.None }
            }
            @Suppress("UNREACHABLE_CODE")
            null
        }
    }

    suspend fun release(lease: MicrophoneLease, reason: String = "released") {
        mutex.withLock {
            val current = mutableState.value
            if (current.owner == lease.owner && current.generation == lease.generation) {
                mutableState.value = MicrophoneOwnershipState(
                    owner = MicrophoneOwner.None,
                    reason = reason,
                    generation = current.generation,
                )
            }
        }
    }

    suspend fun forceRelease(owner: MicrophoneOwner, reason: String) {
        mutex.withLock {
            val current = mutableState.value
            if (current.owner == owner) {
                mutableState.value = MicrophoneOwnershipState(
                    owner = MicrophoneOwner.None,
                    reason = reason,
                    generation = current.generation,
                )
            }
        }
    }

    suspend fun awaitOwner(
        owner: MicrophoneOwner,
        timeoutMs: Long = DEFAULT_ACQUIRE_TIMEOUT_MS,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        state.first { it.owner == owner }
        true
    } ?: false

    private companion object {
        const val DEFAULT_ACQUIRE_TIMEOUT_MS = 2_500L
    }
}
