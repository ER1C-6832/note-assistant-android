package com.er1cmo.noteassistant

import android.app.Application
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceController
import com.er1cmo.noteassistant.stability.AssistantSystemAudioCoordinator
import com.er1cmo.noteassistant.wakeword.WakeWordAssistantBridge
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class NoteAssistantApp : Application() {
    @Inject lateinit var wakeWordAssistantBridge: WakeWordAssistantBridge
    @Inject lateinit var assistantSystemAudioCoordinator: AssistantSystemAudioCoordinator
    @Inject lateinit var wakeWordServiceController: WakeWordServiceController

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        wakeWordAssistantBridge.start()
        assistantSystemAudioCoordinator.start()
        applicationScope.launch {
            runCatching { wakeWordServiceController.restoreIfEnabled() }
        }
    }
}
