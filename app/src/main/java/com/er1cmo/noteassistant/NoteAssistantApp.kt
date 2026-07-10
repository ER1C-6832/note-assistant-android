package com.er1cmo.noteassistant

import android.app.Application
import com.er1cmo.noteassistant.wakeword.WakeWordAssistantBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NoteAssistantApp : Application() {
    @Inject lateinit var wakeWordAssistantBridge: WakeWordAssistantBridge

    override fun onCreate() {
        super.onCreate()
        wakeWordAssistantBridge.start()
    }
}
