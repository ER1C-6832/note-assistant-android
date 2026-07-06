package com.er1cmo.noteassistant.core.common.result

data class AppError(
    val code: String,
    val message: String,
    val cause: Throwable? = null,
)
