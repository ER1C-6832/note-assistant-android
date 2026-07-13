package com.er1cmo.noteassistant.assistant.runtime.mcp

/**
 * Small in-process JSON-RPC response cache.
 *
 * The lock intentionally covers computation as well as lookup so two transport
 * callbacks carrying the same request id cannot execute a destructive tool twice.
 */
internal class McpRequestDeduplicator(
    private val maxEntries: Int = 64,
) {
    private val lock = Any()
    private val responses = LinkedHashMap<String, McpProtocolResponse>(maxEntries, 0.75f, true)

    fun getOrCompute(
        requestIdJson: String?,
        compute: () -> McpProtocolResponse,
    ): McpProtocolResponse {
        val key = requestIdJson?.trim()?.takeIf { it.isNotBlank() } ?: return compute()
        return synchronized(lock) {
            responses[key]?.let { return@synchronized it }
            compute().also { response ->
                responses[key] = response
                while (responses.size > maxEntries) {
                    val oldest = responses.entries.iterator()
                    if (oldest.hasNext()) {
                        oldest.next()
                        oldest.remove()
                    }
                }
            }
        }
    }

    fun clear() = synchronized(lock) { responses.clear() }

    fun size(): Int = synchronized(lock) { responses.size }
}
