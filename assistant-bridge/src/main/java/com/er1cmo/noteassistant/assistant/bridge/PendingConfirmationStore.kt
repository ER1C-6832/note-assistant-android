package com.er1cmo.noteassistant.assistant.bridge

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PendingConfirmationStore @Inject constructor() {
    private val pending = linkedMapOf<String, String>()

    fun put(id: String, payloadJson: String) {
        pending[id] = payloadJson
    }

    fun consume(id: String): String? = pending.remove(id)
}
