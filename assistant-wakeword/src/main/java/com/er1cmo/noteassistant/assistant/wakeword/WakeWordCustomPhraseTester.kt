package com.er1cmo.noteassistant.assistant.wakeword

import android.content.Context
import com.er1cmo.noteassistant.core.common.audio.MicrophoneOwnershipCoordinator
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

data class WakeWordCustomCheckResult(
    val success: Boolean,
    val message: String,
)

data class WakeWordCustomTestResult(
    val success: Boolean,
    val detectedKeyword: String? = null,
    val latencyMs: Long? = null,
    val message: String,
)

@Singleton
class WakeWordCustomPhraseTester @Inject constructor(
    @ApplicationContext private val context: Context,
    private val microphoneOwnershipCoordinator: MicrophoneOwnershipCoordinator,
) {
    suspend fun verifyStream(
        candidate: WakeWordPronunciationCandidate,
        sensitivity: WakeWordSensitivity,
    ): WakeWordCustomCheckResult = withContext(Dispatchers.Default) {
        val detector = SherpaWakeWordDetector(context, candidate.toConfig(sensitivity))
        runCatching {
            detector.initialize()
            detector.resetStream()
        }.fold(
            onSuccess = {
                detector.release()
                WakeWordCustomCheckResult(
                    success = true,
                    message = "tokens 校验通过，sherpa 测试 stream 创建成功。",
                )
            },
            onFailure = { error ->
                detector.release()
                WakeWordCustomCheckResult(
                    success = false,
                    message = "测试 stream 创建失败：${error.message ?: error::class.java.simpleName}",
                )
            },
        )
    }

    suspend fun listenForPhrase(
        candidate: WakeWordPronunciationCandidate,
        sensitivity: WakeWordSensitivity,
        timeoutMs: Long = TEST_TIMEOUT_MS,
    ): WakeWordCustomTestResult {
        val completion = CompletableDeferred<WakeWordCustomTestResult>()
        val engine = SherpaWakeWordEngine(
            context = context,
            config = candidate.toConfig(sensitivity).copy(
                cooldownMs = 500L,
                callbackDelayAfterHitMs = 0L,
            ),
            microphoneOwnershipCoordinator = microphoneOwnershipCoordinator,
            onEvent = { event ->
                when (event) {
                    is WakeWordEvent.Detected -> completion.complete(
                        WakeWordCustomTestResult(
                            success = true,
                            detectedKeyword = event.rawKeyword,
                            latencyMs = event.latencyMs,
                            message = "本机测试成功：检测到 ${candidate.displayText}",
                        ),
                    )
                    is WakeWordEvent.Error -> completion.complete(
                        WakeWordCustomTestResult(
                            success = false,
                            message = event.message,
                        ),
                    )
                    is WakeWordEvent.Status -> Unit
                }
            },
        )
        engine.start()
        return try {
            withTimeoutOrNull(timeoutMs) { completion.await() }
                ?: WakeWordCustomTestResult(
                    success = false,
                    message = "${timeoutMs / 1_000} 秒内未检测到唤醒词，请靠近手机并按所选读音重试。",
                )
        } finally {
            engine.stopAndAwait()
        }
    }

    private fun WakeWordPronunciationCandidate.toConfig(
        sensitivity: WakeWordSensitivity,
    ): WakeWordConfig = WakeWordConfig(
        phrase = asPhrase(),
        keywordsScore = sensitivity.keywordsScore,
        keywordsThreshold = sensitivity.keywordsThreshold,
        numTrailingBlanks = sensitivity.numTrailingBlanks,
        sensitivityLabel = sensitivity.label,
    )

    private companion object {
        const val TEST_TIMEOUT_MS = 10_000L
    }
}
