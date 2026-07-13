package com.er1cmo.noteassistant

import android.app.Application
import com.er1cmo.noteassistant.stability.AssistantSystemAudioCoordinator
import com.er1cmo.noteassistant.wakeword.WakeWordAssistantBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NoteAssistantApp : Application() {
    @Inject lateinit var wakeWordAssistantBridge: WakeWordAssistantBridge
    @Inject lateinit var assistantSystemAudioCoordinator: AssistantSystemAudioCoordinator

    override fun onCreate() {
        super.onCreate()
        wakeWordAssistantBridge.start()
        assistantSystemAudioCoordinator.start()
    }
}
