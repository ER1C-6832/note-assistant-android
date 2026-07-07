package com.er1cmo.noteassistant.assistant.runtime.protocol

class XiaozhiMessageBuilder {
    fun hello(): String = """
        {"type":"hello","version":1,"features":{"mcp":true},"transport":"websocket","audio_params":{"format":"opus","sample_rate":16000,"channels":1,"frame_duration":20}}
    """.trimIndent()

    fun listenDetect(sessionId: String, text: String): String =
        "{\"session_id\":\"${sessionId.escapeJson()}\",\"type\":\"listen\",\"state\":\"detect\",\"text\":\"${text.escapeJson()}\"}"

    fun startListening(sessionId: String, mode: String = "manual"): String =
        "{\"session_id\":\"${sessionId.escapeJson()}\",\"type\":\"listen\",\"state\":\"start\",\"mode\":\"${mode.escapeJson()}\"}"

    fun stopListening(sessionId: String): String =
        "{\"session_id\":\"${sessionId.escapeJson()}\",\"type\":\"listen\",\"state\":\"stop\"}"

    fun abort(sessionId: String, reason: String = "user_interruption"): String =
        "{\"session_id\":\"${sessionId.escapeJson()}\",\"type\":\"abort\",\"reason\":\"${reason.escapeJson()}\"}"

    fun mcp(sessionId: String, payloadJson: String): String =
        "{\"session_id\":\"${sessionId.escapeJson()}\",\"type\":\"mcp\",\"payload\":$payloadJson}"

    private fun String.escapeJson(): String = buildString {
        for (char in this@escapeJson) {
            when (char) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(char)
            }
        }
    }
}
