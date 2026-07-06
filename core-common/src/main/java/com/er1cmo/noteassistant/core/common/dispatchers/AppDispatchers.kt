package com.er1cmo.noteassistant.core.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

data class AppDispatchers(
    val io: CoroutineDispatcher,
    val default: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)
