package com.er1cmo.noteassistant.assistant.runtime.recovery

import javax.inject.Inject

class ReconnectPolicy @Inject constructor() {
    fun decideClose(
        closeCode: Int,
        reason: String,
        assistantEnabled: Boolean,
        currentAttempt: Int,
    ): ReconnectDecision {
        if (!assistantEnabled) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = 0,
                decisionLabel = "disabled_no_reconnect",
                userMessage = "助手已关闭，不重连。",
            )
        }
        if (closeCode == NORMAL_CLOSE_CODE) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = 0,
                decisionLabel = "normal_close_no_reconnect",
                userMessage = "连接已正常关闭：$reason",
            )
        }
        val nextAttempt = currentAttempt + 1
        if (nextAttempt > MAX_RECONNECT_ATTEMPTS) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = currentAttempt,
                decisionLabel = "max_attempts_reached",
                userMessage = "连接异常关闭，已达到最大重连次数。",
            )
        }
        return ReconnectDecision(
            shouldReconnect = true,
            nextAttempt = nextAttempt,
            decisionLabel = "reconnect_attempt_$nextAttempt",
            userMessage = "连接异常关闭，准备第 $nextAttempt 次重连。",
        )
    }

    fun decideFailure(
        assistantEnabled: Boolean,
        currentAttempt: Int,
    ): ReconnectDecision {
        if (!assistantEnabled) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = 0,
                decisionLabel = "disabled_failure_no_reconnect",
                userMessage = "助手已关闭，连接失败后不重连。",
            )
        }
        val nextAttempt = currentAttempt + 1
        if (nextAttempt > MAX_RECONNECT_ATTEMPTS) {
            return ReconnectDecision(
                shouldReconnect = false,
                nextAttempt = currentAttempt,
                decisionLabel = "failure_max_attempts_reached",
                userMessage = "连接失败，已达到最大重连次数。",
            )
        }
        return ReconnectDecision(
            shouldReconnect = true,
            nextAttempt = nextAttempt,
            decisionLabel = "failure_reconnect_attempt_$nextAttempt",
            userMessage = "连接失败，准备第 $nextAttempt 次重连。",
        )
    }

    companion object {
        const val NORMAL_CLOSE_CODE = 1000
        const val MAX_RECONNECT_ATTEMPTS = 3
    }
}

data class ReconnectDecision(
    val shouldReconnect: Boolean,
    val nextAttempt: Int,
    val decisionLabel: String,
    val userMessage: String,
)
