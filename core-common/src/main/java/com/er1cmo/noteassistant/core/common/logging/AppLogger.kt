package com.er1cmo.noteassistant.core.common.logging

interface AppLogger {
    fun d(tag: String, message: String)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
