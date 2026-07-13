package com.er1cmo.noteassistant.assistant.wakeword

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Restores the persisted wake-word service after a normal device reboot. */
class WakeWordBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                val repository = WakeWordSettingsRepository(context.applicationContext)
                if (repository.current().enabled) {
                    runCatching {
                        ContextCompat.startForegroundService(
                            context.applicationContext,
                            WakeWordForegroundService.startIntent(context.applicationContext),
                        )
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
