package com.er1cmo.noteassistant.assistant.wakeword

import android.app.Service
import android.content.Intent
import android.os.IBinder

class WakeWordForegroundService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null
    // Phase 5 从 xiaozhi-android 迁移真实 ForegroundService + sherpa-onnx KWS。
}
