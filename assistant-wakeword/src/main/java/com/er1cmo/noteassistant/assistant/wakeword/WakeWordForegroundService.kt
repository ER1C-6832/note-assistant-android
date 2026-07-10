package com.er1cmo.noteassistant.assistant.wakeword

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

@AndroidEntryPoint
class WakeWordForegroundService : Service() {
    @Inject lateinit var settingsRepository: WakeWordSettingsRepository
    @Inject lateinit var coordinator: WakeWordCoordinator

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var engine: SherpaWakeWordEngine? = null
    private var currentConfig = WakeWordConfig()
    private var currentMode = ServiceMode.Stopped

    override fun onCreate() {
        super.onCreate()
        ensureNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_RESTORE
        val reason = intent?.getStringExtra(EXTRA_REASON).orEmpty()
        return when (action) {
            ACTION_STOP -> {
                val stopReason = reason.ifBlank { "本地唤醒词服务已关闭" }
                serviceScope.launch {
                    stopEngineAndAwait()
                    currentMode = ServiceMode.Stopped
                    coordinator.onServiceStopped(stopReason)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                START_NOT_STICKY
            }

            ACTION_PAUSE -> {
                val pauseReason = reason.ifBlank { "本地唤醒词监听已暂停" }
                startAsForeground(buildNotification("唤醒词已暂停", currentConfig, true))
                serviceScope.launch {
                    stopEngineAndAwait()
                    currentMode = ServiceMode.Paused
                    coordinator.onServicePaused(pauseReason)
                    updateNotification("唤醒词已暂停", currentConfig, true)
                }
                START_STICKY
            }

            ACTION_START,
            ACTION_RESUME,
            ACTION_UPDATE,
            ACTION_RESTORE -> {
                startAsForeground(buildNotification("正在准备本地唤醒词", currentConfig, true))
                serviceScope.launch { loadPersistedConfigAndStart(action) }
                START_STICKY
            }

            else -> START_STICKY
        }
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        serviceScope.cancel()
        coordinator.onServiceStopped("本地唤醒词服务已销毁")
        super.onDestroy()
    }

    private suspend fun loadPersistedConfigAndStart(action: String) {
        val settings = settingsRepository.current()
        if (!settings.enabled) {
            stopEngineAndAwait()
            currentMode = ServiceMode.Stopped
            coordinator.onServiceStopped("本地唤醒词未开启，服务停止")
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        if (!hasRecordAudioPermission()) {
            publishError("本地唤醒词启动失败：缺少麦克风权限", "permission_missing")
            stopSelf()
            return
        }

        val config = runCatching { WakeWordConfig.fromSettings(settings) }
            .getOrElse { error ->
                publishError("唤醒词配置无效：${error.message}", "config_error")
                stopSelf()
                return
            }
        currentConfig = config
        coordinator.onServiceStarting(config.phrase.displayText)
        updateNotification(
            if (action == ACTION_UPDATE) "正在更新唤醒词配置" else "正在启动唤醒词监听",
            config,
            true,
        )
        restartEngine(config)
    }

    private suspend fun restartEngine(config: WakeWordConfig) {
        stopEngineAndAwait()
        engine = SherpaWakeWordEngine(applicationContext, config, ::handleWakeWordEvent).also { it.start() }
        currentMode = ServiceMode.Listening
    }

    private suspend fun stopEngineAndAwait() {
        engine?.stopAndAwait()
        engine = null
    }

    private fun handleWakeWordEvent(event: WakeWordEvent) {
        coordinator.onEvent(event)
        when (event) {
            is WakeWordEvent.Detected -> {
                currentMode = ServiceMode.Detected
                updateNotification("已唤醒：${event.rawKeyword}", currentConfig, true)
            }
            is WakeWordEvent.Status -> {
                val title = when (event.state) {
                    "initializing" -> "正在初始化唤醒词"
                    "audio_open", "listening" -> "正在监听唤醒词"
                    "cooldown" -> "唤醒词冷却中"
                    "audio_released" -> when (currentMode) {
                        ServiceMode.Detected -> "已唤醒，等待恢复监听"
                        ServiceMode.Paused -> "唤醒词已暂停"
                        else -> "唤醒词麦克风已释放"
                    }
                    else -> "本地唤醒词服务"
                }
                updateNotification(title, currentConfig, currentMode != ServiceMode.Stopped)
            }
            is WakeWordEvent.Error -> {
                currentMode = ServiceMode.Error
                updateNotification("唤醒词服务异常", currentConfig, true)
            }
        }
    }

    private fun publishError(message: String, state: String) {
        currentMode = ServiceMode.Error
        coordinator.onEvent(WakeWordEvent.Error(message, state))
        updateNotification("唤醒词服务异常", currentConfig, true)
    }

    private fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    private fun startAsForeground(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun updateNotification(title: String, config: WakeWordConfig, ongoing: Boolean) {
        getSystemService(NotificationManager::class.java).notify(
            NOTIFICATION_ID,
            buildNotification(title, config, ongoing),
        )
    }

    private fun buildNotification(
        title: String,
        config: WakeWordConfig,
        ongoing: Boolean,
    ): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = launchIntent?.let {
            PendingIntent.getActivity(
                this,
                0,
                it,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this, "通知栏停止本地唤醒词"),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_wakeword_notification)
            .setContentTitle(title)
            .setContentText("唤醒词：${config.phrase.displayText} · 灵敏度：${config.sensitivityLabel}")
            .setSubText("小泓便签 · Phase5-01")
            .setContentIntent(contentIntent)
            .setOngoing(ongoing)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setDefaults(0)
            .setCategory(Notification.CATEGORY_SERVICE)
            .addAction(0, "停止", stopPendingIntent)
            .build()
    }

    private fun ensureNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "本地唤醒词",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "本地 sherpa-onnx 唤醒词监听与麦克风前台服务提示"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private enum class ServiceMode {
        Listening,
        Paused,
        Detected,
        Error,
        Stopped,
    }

    companion object {
        const val ACTION_START = "com.er1cmo.noteassistant.wakeword.START"
        const val ACTION_UPDATE = "com.er1cmo.noteassistant.wakeword.UPDATE"
        const val ACTION_PAUSE = "com.er1cmo.noteassistant.wakeword.PAUSE"
        const val ACTION_RESUME = "com.er1cmo.noteassistant.wakeword.RESUME"
        const val ACTION_STOP = "com.er1cmo.noteassistant.wakeword.STOP"
        private const val ACTION_RESTORE = "com.er1cmo.noteassistant.wakeword.RESTORE"
        const val EXTRA_REASON = "reason"

        private const val CHANNEL_ID = "note_assistant_wakeword_v1"
        private const val NOTIFICATION_ID = 50101

        fun startIntent(context: Context): Intent = baseIntent(context, ACTION_START)
        fun updateIntent(context: Context): Intent = baseIntent(context, ACTION_UPDATE)
        fun resumeIntent(context: Context): Intent = baseIntent(context, ACTION_RESUME)
        fun pauseIntent(context: Context, reason: String): Intent =
            baseIntent(context, ACTION_PAUSE).putExtra(EXTRA_REASON, reason)
        fun stopIntent(context: Context, reason: String): Intent =
            baseIntent(context, ACTION_STOP).putExtra(EXTRA_REASON, reason)

        private fun baseIntent(context: Context, action: String): Intent =
            Intent(context, WakeWordForegroundService::class.java).setAction(action)
    }
}
