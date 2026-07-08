package com.er1cmo.noteassistant.assistant.runtime.protocol

import org.junit.Assert.assertTrue
import org.junit.Test

class XiaozhiMessageBuilderTest {
    private val builder = XiaozhiMessageBuilder()

    @Test
    fun helloContainsMcpAndOpusParams() {
        val json = builder.hello()
        assertTrue(json.contains("\"type\":\"hello\""))
        assertTrue(json.contains("\"mcp\":true"))
        assertTrue(json.contains("\"format\":\"opus\""))
        assertTrue(json.contains("\"sample_rate\":16000"))
        assertTrue(json.contains("\"frame_duration\":20"))
    }

    @Test
    fun listenDetectEscapesUserText() {
        val json = builder.listenDetect("s1", "hi \"xiaozhi\"")
        assertTrue(json.contains("\"type\":\"listen\""))
        assertTrue(json.contains("\"state\":\"detect\""))
        assertTrue(json.contains("hi \\\"xiaozhi\\\""))
    }
}
