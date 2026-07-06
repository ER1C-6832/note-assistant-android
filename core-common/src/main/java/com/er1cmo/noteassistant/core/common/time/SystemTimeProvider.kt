package com.er1cmo.noteassistant.core.common.time

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemTimeProvider @Inject constructor() : TimeProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
