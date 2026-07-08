package com.er1cmo.noteassistant.assistant.runtime.recovery

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private val policy = ReconnectPolicy()

    @Test
    fun normalCloseDoesNotReconnect() {
        val decision = policy.decideClose(
            closeCode = 1000,
            reason = "normal",
            assistantEnabled = true,
            currentAttempt = 0,
        )

        assertFalse(decision.shouldReconnect)
        assertEquals("normal_close_no_reconnect", decision.decisionLabel)
    }

    @Test
    fun abnormalCloseReconnectsWhenEnabled() {
        val decision = policy.decideClose(
            closeCode = 1006,
            reason = "abnormal",
            assistantEnabled = true,
            currentAttempt = 0,
        )

        assertTrue(decision.shouldReconnect)
        assertEquals(1, decision.nextAttempt)
        assertEquals("reconnect_attempt_1", decision.decisionLabel)
    }

    @Test
    fun disabledRuntimeNeverReconnects() {
        val decision = policy.decideClose(
            closeCode = 1006,
            reason = "abnormal",
            assistantEnabled = false,
            currentAttempt = 0,
        )

        assertFalse(decision.shouldReconnect)
        assertEquals("disabled_no_reconnect", decision.decisionLabel)
    }

    @Test
    fun maxAttemptsStopReconnectLoop() {
        val decision = policy.decideClose(
            closeCode = 1006,
            reason = "abnormal",
            assistantEnabled = true,
            currentAttempt = ReconnectPolicy.MAX_RECONNECT_ATTEMPTS,
        )

        assertFalse(decision.shouldReconnect)
        assertEquals("max_attempts_reached", decision.decisionLabel)
    }
}
