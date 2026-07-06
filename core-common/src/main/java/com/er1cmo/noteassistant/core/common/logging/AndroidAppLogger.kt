package com.er1cmo.noteassistant.core.common.logging

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AndroidAppLogger @Inject constructor() : AppLogger {
    override fun d(tag: String, message: String) {
        Log.d(tag, message)
    }

    override fun e(tag: String, message: String, throwable: Throwable?) {
        Log.e(tag, message, throwable)
    }
}
