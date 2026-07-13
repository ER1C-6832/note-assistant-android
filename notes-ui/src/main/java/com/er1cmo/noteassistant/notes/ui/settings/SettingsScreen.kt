package com.er1cmo.noteassistant.notes.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordCoordinator
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordPreset
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordSensitivity
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceController
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val wakeWordState by viewModel.wakeWordState.collectAsState()
    val context = LocalContext.current

    fun hasPermission(permission: String): Boolean = ContextCompat.checkSelfPermission(
        context,
        permission,
    ) == PackageManager.PERMISSION_GRANTED

    val wakeWordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        val microphoneGranted = hasPermission(Manifest.permission.RECORD_AUDIO)
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        viewModel.setWakeWordEnabled(microphoneGranted && notificationGranted)
    }

    val setWakeWordEnabled: (Boolean) -> Unit = { enabled ->
        if (!enabled) {
            viewModel.setWakeWordEnabled(false)
        } else {
            val missing = buildList {
                if (!hasPermission(Manifest.permission.RECORD_AUDIO)) {
                    add(Manifest.permission.RECORD_AUDIO)
                }
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !hasPermission(Manifest.permission.POST_NOTIFICATIONS)
                ) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            if (missing.isEmpty()) {
                viewModel.setWakeWordEnabled(true)
            } else {
                wakeWordPermissionLauncher.launch(missing.toTypedArray())
            }
        }
    }

    Surface(
        color = colorFromHex(state.homeBackgroundColor),
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "设置",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF202632),
                )
                TextButton(onClick = onBackClick) { Text("完成") }
            }

            AppearanceSettingsBox(
                homeBackgroundColor = state.homeBackgroundColor,
                tagDrawerBackgroundColor = state.tagDrawerBackgroundColor,
                onHomeBackgroundChange = viewModel::setHomeBackgroundColor,
                onTagDrawerBackgroundChange = viewModel::setTagDrawerBackgroundColor,
            )

            WakeWordSettingsBox(
                state = wakeWordState,
                onEnabledChange = setWakeWordEnabled,
                onPresetChange = viewModel::setWakeWordPreset,
                onSensitivityChange = viewModel::setWakeWordSensitivity,
                onCooldownChange = viewModel::setWakeWordCooldown,
            )

            VoiceConversationSettingsBox(
                state = state.assistantState,
                onModeChange = viewModel::setVoiceInteractionMode,
                onBargeInEnabledChange = viewModel::setStreamingBargeInEnabled,
            )

            TextConversationSettingsBox(
                enabled = state.assistantTextPanelEnabled,
                onEnabledChange = viewModel::setAssistantTextPanelEnabled,
            )

            DeveloperOptionsBox(logs = state.recentLogs)

            Spacer(Modifier.height(24.dp))
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val commandTraceRepository: CommandTraceRepository,
    private val assistantController: AssistantController,
    private val wakeWordServiceController: WakeWordServiceController,
    private val wakeWordCoordinator: WakeWordCoordinator,
) : ViewModel() {
    val wakeWordState = wakeWordCoordinator.state

    private data class AppearanceState(
        val homeBackgroundColor: String,
        val tagDrawerBackgroundColor: String,
        val assistantTextPanelEnabled: Boolean,
    )

    private val appearanceState = combine(
        settingsRepository.homeBackgroundColor,
        settingsRepository.tagDrawerBackgroundColor,
        settingsRepository.assistantTextPanelEnabled,
    ) { home, drawer, textPanelEnabled ->
        AppearanceState(
            homeBackgroundColor = home,
            tagDrawerBackgroundColor = drawer,
            assistantTextPanelEnabled = textPanelEnabled,
        )
    }

    val state = combine(
        appearanceState,
        assistantController.state,
        commandTraceRepository.observeRecentCommandLogs(20),
    ) { appearance, assistantState, logs ->
        SettingsUiState(
            homeBackgroundColor = appearance.homeBackgroundColor,
            tagDrawerBackgroundColor = appearance.tagDrawerBackgroundColor,
            assistantTextPanelEnabled = appearance.assistantTextPanelEnabled,
            assistantState = assistantState,
            recentLogs = logs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setHomeBackgroundColor(hex: String) {
        viewModelScope.launch { settingsRepository.setHomeBackgroundColor(hex) }
    }

    fun setTagDrawerBackgroundColor(hex: String) {
        viewModelScope.launch { settingsRepository.setTagDrawerBackgroundColor(hex) }
    }

    fun setAssistantTextPanelEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setAssistantTextPanelEnabled(enabled) }
    }

    fun setVoiceInteractionMode(mode: VoiceInteractionMode) {
        viewModelScope.launch { assistantController.setVoiceInteractionMode(mode) }
    }

    fun setStreamingBargeInEnabled(enabled: Boolean) {
        viewModelScope.launch { assistantController.setStreamingBargeInEnabled(enabled) }
    }

    fun setWakeWordEnabled(enabled: Boolean) {
        viewModelScope.launch { wakeWordServiceController.setEnabled(enabled) }
    }

    fun setWakeWordPreset(preset: WakeWordPreset) {
        viewModelScope.launch { wakeWordServiceController.setPreset(preset) }
    }

    fun setWakeWordSensitivity(sensitivity: WakeWordSensitivity) {
        viewModelScope.launch { wakeWordServiceController.setSensitivity(sensitivity) }
    }

    fun setWakeWordCooldown(cooldownMs: Long) {
        viewModelScope.launch { wakeWordServiceController.setCooldownMs(cooldownMs) }
    }
}

data class SettingsUiState(
    val homeBackgroundColor: String = SettingsRepository.DEFAULT_HOME_BACKGROUND,
    val tagDrawerBackgroundColor: String = SettingsRepository.DEFAULT_TAG_DRAWER_BACKGROUND,
    val assistantTextPanelEnabled: Boolean = false,
    val assistantState: AssistantState = AssistantState.disabled(),
    val recentLogs: List<AssistantCommandLog> = emptyList(),
)

@Composable
private fun AppearanceSettingsBox(
    homeBackgroundColor: String,
    tagDrawerBackgroundColor: String,
    onHomeBackgroundChange: (String) -> Unit,
    onTagDrawerBackgroundChange: (String) -> Unit,
) {
    SettingsCard {
        Text(
            text = "外观",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF202632),
        )
        ColorChoiceRow(
            label = "主页颜色",
            selectedHex = homeBackgroundColor,
            options = HOME_BACKGROUND_OPTIONS,
            onSelect = onHomeBackgroundChange,
        )
        ColorChoiceRow(
            label = "侧栏颜色",
            selectedHex = tagDrawerBackgroundColor,
            options = DRAWER_BACKGROUND_OPTIONS,
            onSelect = onTagDrawerBackgroundChange,
        )
    }
}

@Composable
private fun TextConversationSettingsBox(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "显示文字对话",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF202632),
            )
            Switch(checked = enabled, onCheckedChange = onEnabledChange)
        }
    }
}

@Composable
private fun DeveloperOptionsBox(logs: List<AssistantCommandLog>) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    SettingsCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "开发者选项",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF202632),
            )
            Text(
                text = if (expanded) "收起" else "展开",
                color = Color(0xFF5268D8),
                style = MaterialTheme.typography.labelLarge,
            )
        }

        if (expanded) {
            Text(
                text = "最近命令日志",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF545D6D),
            )
            if (logs.isEmpty()) {
                Text(
                    text = "暂无记录",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF7A8494),
                )
            } else {
                logs.take(20).forEach { log ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = Color(0xFFF5F7FC),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            Text(
                                text = log.toolName.storageValue,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF2D3542),
                            )
                            Text(
                                text = "${log.status.storageValue} · ${log.source.storageValue} · #${log.id}",
                                style = MaterialTheme.typography.labelSmall,
                                color = Color(0xFF717B8B),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorChoiceRow(
    label: String,
    selectedHex: String,
    options: List<ColorOption>,
    onSelect: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF545D6D),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            options.forEach { option ->
                val selected = option.hex.equals(selectedHex, ignoreCase = true)
                Surface(
                    modifier = Modifier
                        .size(42.dp)
                        .clickable { onSelect(option.hex) },
                    shape = CircleShape,
                    color = colorFromHex(option.hex),
                    border = BorderStroke(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) Color(0xFF5268D8) else Color(0xFFD4D9E2),
                    ),
                    shadowElevation = if (selected) 3.dp else 0.dp,
                ) {}
            }
        }
    }
}

@Composable
internal fun SettingsCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(22.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content,
    )
}

private data class ColorOption(val name: String, val hex: String)

private val HOME_BACKGROUND_OPTIONS = listOf(
    ColorOption("白", "#FFFFFF"),
    ColorOption("暖白", "#FFF8F1"),
    ColorOption("雾蓝", "#F2F6FF"),
    ColorOption("浅绿", "#F2F8F2"),
    ColorOption("深色", "#252A34"),
)

private val DRAWER_BACKGROUND_OPTIONS = listOf(
    ColorOption("暖黄", "#FFF3D1"),
    ColorOption("浅紫", "#F2E9FF"),
    ColorOption("浅蓝", "#EAF3FF"),
    ColorOption("浅绿", "#EAF7EE"),
    ColorOption("灰白", "#F4F6FA"),
)

private fun colorFromHex(hex: String): Color = runCatching {
    Color(AndroidColor.parseColor(hex))
}.getOrDefault(Color.White)
