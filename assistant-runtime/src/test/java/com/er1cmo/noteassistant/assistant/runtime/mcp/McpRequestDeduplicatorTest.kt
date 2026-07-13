package com.er1cmo.noteassistant.assistant.runtime.mcp

import com.er1cmo.noteassistant.assistant.mcpbase.McpToolStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class McpRequestDeduplicatorTest {
    @Test
    fun sameRequestIdComputesOnlyOnce() {
        val deduplicator = McpRequestDeduplicator(maxEntries = 4)
        var executions = 0
        fun response(): McpProtocolResponse {
            executions += 1
            return McpProtocolResponse(
                requestIdJson = "1",
                method = "tools/call",
                toolName = "notes.create",
                status = McpToolStatus.Success,
                blocked = false,
                message = "ok-$executions",
                responseJson = "{\"jsonrpc\":\"2.0\",\"id\":1}",
            )
        }

        val first = deduplicator.getOrCompute("1", ::response)
        val second = deduplicator.getOrCompute("1", ::response)

        assertEquals(1, executions)
        assertEquals(first, second)
    }

    @Test
    fun cacheRemainsBounded() {
        val deduplicator = McpRequestDeduplicator(maxEntries = 2)
        repeat(5) { index ->
            deduplicator.getOrCompute(index.toString()) {
                McpProtocolResponse(
                    requestIdJson = index.toString(),
                    method = "tools/call",
                    toolName = null,
                    status = McpToolStatus.Success,
                    blocked = false,
                    message = "ok",
                    responseJson = "{}",
                )
            }
        }
        assertEquals(2, deduplicator.size())
    }
}
