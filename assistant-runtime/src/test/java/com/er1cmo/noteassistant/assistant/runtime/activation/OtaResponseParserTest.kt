package com.er1cmo.noteassistant.assistant.runtime.activation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OtaResponseParserTest {
    @Test
    fun parsesWebSocketConfigAndRedactsToken() {
        val response = parseOtaResponse(
            """
            {
              "websocket": {
                "url": "wss://example.test/xiaozhi",
                "token": "secret-token"
              }
            }
            """.trimIndent(),
        )

        assertEquals("wss://example.test/xiaozhi", response.websocketUrl)
        assertEquals("secret-token", response.websocketToken)
        assertNull(response.activation)
        assertTrue(response.redactedJson.contains("***"))
    }

    @Test
    fun parsesActivationChallenge() {
        val response = parseOtaResponse(
            """
            {
              "activation": {
                "code": "123456",
                "challenge": "abc",
                "message": "请输入验证码"
              }
            }
            """.trimIndent(),
        )

        assertEquals("123456", response.activation?.code)
        assertEquals("abc", response.activation?.challenge)
        assertEquals("请输入验证码", response.activation?.message)
    }
}
