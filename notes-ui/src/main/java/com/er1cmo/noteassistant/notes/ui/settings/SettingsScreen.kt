package com.er1cmo.noteassistant.notes.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
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
import com.er1cmo.noteassistant.notes.domain.command.CommandResult
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import com.er1cmo.noteassistant.notes.ui.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
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
    val microphonePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.startPtt(hasRecordAudioPermission = granted)
    }
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
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
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
    val startPushToTalk: () -> Unit = {
        val alreadyGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED
        if (alreadyGranted) {
            viewModel.startPtt(hasRecordAudioPermission = true)
        } else {
            microphonePermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    Surface(color = colorFromHex(state.homeBackgroundColor), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小泓便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("设置 / Phase2 调试 / Phase3+Phase4 MCP 调试", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                StatusPill(text = "本地优先")
            }

            SectionTitle("便签设置")
            SettingBox("数据库", "本地 SQLite note_assistant.db")
            SettingBox("搜索模式", "手动搜索与命令搜索逐步收口到 SearchNotesUseCase")
            ColorSettingBox(
                label = "主界面背景",
                selectedHex = state.homeBackgroundColor,
                options = homeBackgroundOptions,
                onSelect = viewModel::setHomeBackgroundColor,
            )
            ColorSettingBox(
                label = "Tag 列表背景",
                selectedHex = state.tagDrawerBackgroundColor,
                options = tagDrawerBackgroundOptions,
                onSelect = viewModel::setTagDrawerBackgroundColor,
            )

            SectionTitle("Phase5-01 本地唤醒词")
            WakeWordSettingsBox(
                state = wakeWordState,
                onEnabledChange = setWakeWordEnabled,
                onPresetChange = viewModel::setWakeWordPreset,
                onSensitivityChange = viewModel::setWakeWordSensitivity,
                onCooldownChange = viewModel::setWakeWordCooldown,
                onPause = viewModel::pauseWakeWord,
                onResume = viewModel::resumeWakeWord,
                onMarkFalseTrigger = viewModel::markWakeWordFalseTrigger,
                onResetStats = viewModel::resetWakeWordStats,
            )

            SectionTitle("Phase5-02 语音模式")
            VoiceConversationSettingsBox(
                state = state.assistantState,
                onModeChange = viewModel::setVoiceInteractionMode,
                onBargeInEnabledChange = viewModel::setStreamingBargeInEnabled,
            )

            SectionTitle("Phase3/Phase4 助手运行时")
            Phase3RuntimeBox(
                state = state,
                onEnableClick = viewModel::enableAssistant,
                onDisableClick = viewModel::disableAssistant,
                onPrepareIdentityClick = viewModel::prepareDeviceIdentity,
                onResetIdentityClick = viewModel::resetDeviceIdentity,
                onUseFakeRuntimeClick = viewModel::useFakeRuntime,
                onUseRealRuntimeClick = viewModel::useRealRuntime,
                onFakeActivationClick = viewModel::runFakeActivation,
                onRealActivationClick = viewModel::runRealActivation,
                onConnectClick = viewModel::connectAssistant,
                onReconnectClick = viewModel::reconnectAssistant,
                onDisconnectClick = viewModel::disconnectAssistant,
                onTextInputChange = viewModel::setAssistantTextInput,
                onSendTextClick = viewModel::sendAssistantText,
                onStartPttClick = startPushToTalk,
                onStopPttClick = viewModel::stopPtt,
                onSimulateToolCallClick = viewModel::simulateBlockedNoteTool,
                onPhase4ToolNameChange = viewModel::setPhase4ToolName,
                onPhase4ArgumentsChange = viewModel::setPhase4ArgumentsJson,
                onPhase4SampleClick = viewModel::applyPhase4Sample,
                onListPhase4ToolsClick = viewModel::listPhase4Tools,
                onExecutePhase4ToolClick = viewModel::executePhase4McpTool,
                onSimulateCloseClick = viewModel::simulateConnectionClosed,
                onSimulateFailureClick = viewModel::simulateConnectionFailure,
                onSimulateAudioFailureClick = viewModel::simulateAudioFailure,
            )

            SectionTitle("Phase2 命令与追溯调试")
            ToolSimulatorBox(
                state = state,
                onToolNameChange = viewModel::setToolName,
                onArgumentsChange = viewModel::setArgumentsJson,
                onSampleClick = viewModel::applySample,
                onExecuteClick = viewModel::executeTool,
                onConfirmClick = viewModel::confirmPending,
                onRejectClick = viewModel::rejectPending,
            )
            RevisionDebugBox(
                state = state,
                onNoteIdChange = viewModel::setRevisionNoteId,
                onRevisionIdChange = viewModel::setRevisionToRestoreId,
                onLoadClick = viewModel::loadRevisions,
                onApplyRestoreSampleClick = viewModel::applyRestoreRevisionSample,
            )
            CommandLogBox(logs = state.recentLogs)
            Button(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val noteCommandService: NoteCommandService,
    private val commandTraceRepository: CommandTraceRepository,
    private val assistantController: AssistantController,
    private val wakeWordServiceController: WakeWordServiceController,
    private val wakeWordCoordinator: WakeWordCoordinator,
) : ViewModel() {
    val wakeWordState = wakeWordCoordinator.state
    private val toolName = MutableStateFlow("notes.create")
    private val argumentsJson = MutableStateFlow("{\"title\":\"Phase2 模拟创建\",\"content\":\"从本地工具模拟器创建\",\"type\":\"normal\",\"tags\":[\"Phase2\"]}")
    private val isRunning = MutableStateFlow(false)
    private val resultText = MutableStateFlow("尚未执行命令")
    private val pendingConfirmationId = MutableStateFlow<String?>(null)
    private val revisionNoteId = MutableStateFlow("")
    private val revisionToRestoreId = MutableStateFlow("")
    private val revisionText = MutableStateFlow("输入 note_id 后点击刷新 revision。")
    private val assistantTextInput = MutableStateFlow("你好，请简单介绍一下自己")
    private val phase4ToolName = MutableStateFlow("notes.create")
    private val phase4ArgumentsJson = MutableStateFlow("{\"title\":\"Phase4 MCP 创建\",\"content\":\"从 runtime MCP executor 创建\",\"type\":\"normal\",\"tags\":[\"Phase4\"]}")

    private data class ThemeState(val home: String, val tagDrawer: String)
    private data class SimulatorState(
        val toolName: String,
        val argumentsJson: String,
        val isRunning: Boolean,
        val resultText: String,
        val pendingConfirmationId: String?,
    )
    private data class RevisionState(val noteId: String, val revisionId: String, val text: String)
    private data class AssistantPanelState(
        val assistantState: AssistantState,
        val textInput: String,
        val phase4ToolName: String,
        val phase4ArgumentsJson: String,
    )

    private val themeState = combine(
        settingsRepository.homeBackgroundColor,
        settingsRepository.tagDrawerBackgroundColor,
    ) { home, tagDrawer -> ThemeState(home = home, tagDrawer = tagDrawer) }

    private val simulatorState = combine(toolName, argumentsJson, isRunning, resultText, pendingConfirmationId) { tool, args, running, result, confirmationId ->
        SimulatorState(toolName = tool, argumentsJson = args, isRunning = running, resultText = result, pendingConfirmationId = confirmationId)
    }

    private val revisionState = combine(revisionNoteId, revisionToRestoreId, revisionText) { noteId, revisionId, text ->
        RevisionState(noteId = noteId, revisionId = revisionId, text = text)
    }

    private val assistantPanelState = combine(
        assistantController.state,
        assistantTextInput,
        phase4ToolName,
        phase4ArgumentsJson,
    ) { assistantState, input, tool, arguments ->
        AssistantPanelState(
            assistantState = assistantState,
            textInput = input,
            phase4ToolName = tool,
            phase4ArgumentsJson = arguments,
        )
    }

    val state = combine(
        themeState,
        simulatorState,
        revisionState,
        commandTraceRepository.observeRecentCommandLogs(20),
        assistantPanelState,
    ) { theme, simulator, revision, logs, assistant ->
        SettingsUiState(
            homeBackgroundColor = theme.home,
            tagDrawerBackgroundColor = theme.tagDrawer,
            toolName = simulator.toolName,
            argumentsJson = simulator.argumentsJson,
            isRunning = simulator.isRunning,
            resultText = simulator.resultText,
            pendingConfirmationId = simulator.pendingConfirmationId,
            revisionNoteId = revision.noteId,
            revisionToRestoreId = revision.revisionId,
            revisionText = revision.text,
            assistantState = assistant.assistantState,
            assistantTextInput = assistant.textInput,
            phase4ToolName = assistant.phase4ToolName,
            phase4ArgumentsJson = assistant.phase4ArgumentsJson,
            recentLogs = logs,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setHomeBackgroundColor(hex: String) { viewModelScope.launch { settingsRepository.setHomeBackgroundColor(hex) } }
    fun setTagDrawerBackgroundColor(hex: String) { viewModelScope.launch { settingsRepository.setTagDrawerBackgroundColor(hex) } }
    fun setVoiceInteractionMode(mode: VoiceInteractionMode) { viewModelScope.launch { assistantController.setVoiceInteractionMode(mode) } }
    fun setStreamingBargeInEnabled(enabled: Boolean) { viewModelScope.launch { assistantController.setStreamingBargeInEnabled(enabled) } }
    fun setWakeWordEnabled(enabled: Boolean) { viewModelScope.launch { wakeWordServiceController.setEnabled(enabled) } }
    fun setWakeWordPreset(preset: WakeWordPreset) { viewModelScope.launch { wakeWordServiceController.setPreset(preset) } }
    fun setWakeWordSensitivity(sensitivity: WakeWordSensitivity) { viewModelScope.launch { wakeWordServiceController.setSensitivity(sensitivity) } }
    fun setWakeWordCooldown(cooldownMs: Long) { viewModelScope.launch { wakeWordServiceController.setCooldownMs(cooldownMs) } }
    fun pauseWakeWord() { wakeWordServiceController.pause() }
    fun resumeWakeWord() { viewModelScope.launch { wakeWordServiceController.resume() } }
    fun markWakeWordFalseTrigger() { viewModelScope.launch { wakeWordServiceController.markFalseTrigger() } }
    fun resetWakeWordStats() { viewModelScope.launch { wakeWordServiceController.resetStatistics() } }
    fun setAssistantTextInput(value: String) { assistantTextInput.value = value }
    fun setPhase4ToolName(value: String) { phase4ToolName.value = value }
    fun setPhase4ArgumentsJson(value: String) { phase4ArgumentsJson.value = value }
    fun enableAssistant() { viewModelScope.launch { assistantController.enableAssistant() } }
    fun disableAssistant() { viewModelScope.launch { assistantController.disableAssistant() } }
    fun prepareDeviceIdentity() { viewModelScope.launch { assistantController.ensureDeviceIdentity() } }
    fun resetDeviceIdentity() { viewModelScope.launch { assistantController.resetDeviceIdentity() } }
    fun useFakeRuntime() { viewModelScope.launch { assistantController.useFakeRuntime() } }
    fun useRealRuntime() { viewModelScope.launch { assistantController.useRealRuntime() } }
    fun runFakeActivation() { viewModelScope.launch { assistantController.runFakeActivation() } }
    fun runRealActivation() { viewModelScope.launch { assistantController.runRealActivation() } }
    fun connectAssistant() { viewModelScope.launch { assistantController.connect() } }
    fun reconnectAssistant() { viewModelScope.launch { assistantController.reconnect() } }
    fun disconnectAssistant() { viewModelScope.launch { assistantController.disconnect("settings_close") } }
    fun sendAssistantText() { viewModelScope.launch { assistantController.sendText(assistantTextInput.value) } }
    fun startPtt(hasRecordAudioPermission: Boolean) { viewModelScope.launch { assistantController.startPushToTalk(hasRecordAudioPermission = hasRecordAudioPermission) } }
    fun stopPtt() { viewModelScope.launch { assistantController.stopPushToTalk() } }
    fun simulateBlockedNoteTool() { viewModelScope.launch { assistantController.simulateIncomingToolCall("notes.delete", "{\"note_id\":1}") } }
    fun simulateConnectionClosed() { viewModelScope.launch { assistantController.simulateConnectionClosed(code = 1006, reason = "settings_debug_abnormal_close") } }
    fun simulateConnectionFailure() { viewModelScope.launch { assistantController.simulateConnectionFailure("settings_debug_transport_failure") } }
    fun simulateAudioFailure() { viewModelScope.launch { assistantController.simulateAudioFailure("settings_debug_audio_failure") } }
    fun listPhase4Tools() { viewModelScope.launch { assistantController.simulateIncomingToolsList() } }
    fun executePhase4McpTool() {
        viewModelScope.launch {
            assistantController.simulateIncomingToolCall(
                toolName = phase4ToolName.value.trim(),
                argumentsJson = phase4ArgumentsJson.value,
            )
        }
    }
    fun applyPhase4Sample(sample: ToolSample) {
        phase4ToolName.value = sample.toolName
        phase4ArgumentsJson.value = sample.argumentsJson
    }

    fun setToolName(value: String) { toolName.value = value }
    fun setArgumentsJson(value: String) { argumentsJson.value = value }
    fun setRevisionNoteId(value: String) { revisionNoteId.value = value.filter { it.isDigit() } }
    fun setRevisionToRestoreId(value: String) { revisionToRestoreId.value = value.filter { it.isDigit() } }
    fun applySample(sample: ToolSample) {
        toolName.value = sample.toolName
        argumentsJson.value = sample.argumentsJson
        pendingConfirmationId.value = null
        resultText.value = "已套用样例：${sample.label}"
    }

    fun executeTool() {
        if (isRunning.value) return
        viewModelScope.launch {
            isRunning.value = true
            pendingConfirmationId.value = null
            resultText.value = "执行中……"
            val result = noteCommandService.execute(
                toolName = toolName.value.trim(),
                argumentsJson = argumentsJson.value,
                source = CommandSource.LocalToolSimulator,
            )
            pendingConfirmationId.value = result.confirmationId
            resultText.value = result.toDebugText()
            isRunning.value = false
        }
    }

    fun confirmPending() {
        val confirmationId = pendingConfirmationId.value ?: return
        if (isRunning.value) return
        viewModelScope.launch {
            isRunning.value = true
            resultText.value = "确认执行中……\nconfirmation_id=$confirmationId"
            val result = noteCommandService.confirmPendingCommand(confirmationId)
            pendingConfirmationId.value = result.confirmationId
            resultText.value = result.toDebugText()
            isRunning.value = false
        }
    }

    fun rejectPending() {
        val confirmationId = pendingConfirmationId.value ?: return
        if (isRunning.value) return
        viewModelScope.launch {
            isRunning.value = true
            resultText.value = "拒绝执行中……\nconfirmation_id=$confirmationId"
            val result = noteCommandService.rejectPendingCommand(confirmationId)
            pendingConfirmationId.value = null
            resultText.value = result.toDebugText()
            isRunning.value = false
        }
    }

    fun loadRevisions() {
        val noteId = revisionNoteId.value.toLongOrNull()
        if (noteId == null || noteId <= 0L) {
            revisionText.value = "请输入有效 note_id。"
            return
        }
        viewModelScope.launch {
            val revisions: List<*> = commandTraceRepository.listRevisionsForNote(noteId)
            revisionText.value = revisions.toRevisionDebugText(noteId)
            revisionToRestoreId.value = revisions.firstOrNull()?.debugLong("id")?.toString().orEmpty()
        }
    }

    fun applyRestoreRevisionSample() {
        val noteId = revisionNoteId.value.toLongOrNull()
        val revisionId = revisionToRestoreId.value.toLongOrNull()
        if (noteId == null || revisionId == null || noteId <= 0L || revisionId <= 0L) {
            revisionText.value = "需要有效 note_id 和 revision_id，才能套用 restore_revision 样例。"
            return
        }
        toolName.value = "notes.restore_revision"
        argumentsJson.value = "{\"note_id\":$noteId,\"revision_id\":$revisionId}"
        pendingConfirmationId.value = null
        resultText.value = "已套用 restore_revision 样例。执行后会先返回 requires_confirmation。"
    }

    private fun CommandResult.toDebugText(): String = buildString {
        appendLine("status=${status.storageValue}")
        appendLine("risk=${riskLevel.storageValue}")
        appendLine("confirmation=${confirmationStatus.storageValue}")
        appendLine("message=$message")
        commandLogId?.let { appendLine("command_log_id=$it") }
        confirmationId?.let { appendLine("confirmation_id=$it") }
        if (affectedNoteIds.isNotEmpty()) appendLine("affected_note_ids=$affectedNoteIds")
        if (affectedTagIds.isNotEmpty()) appendLine("affected_tag_ids=$affectedTagIds")
        errorCode?.let { appendLine("error=${it.storageValue}") }
        resultJson?.let { appendLine("result_json=$it") }
    }

    private fun List<*>.toRevisionDebugText(noteId: Long): String = buildString {
        if (isEmpty()) {
            append("note_id=$noteId 暂无 revision。先用 append / update_title / replace_content / delete 等命令产生 revision。")
            return@buildString
        }
        appendLine("note_id=$noteId revision_count=$size")
        take(8).forEach { revision ->
            appendLine(
                "#${revision.debugText("id")} " +
                    "reason=${revision.debugText("reason")} " +
                    "source=${revision.debugValue("source").debugStorageValue()} " +
                    "command_log_id=${revision.debugText("commandLogId")}",
            )
            appendLine("  title=${revision.debugText("titleSnapshot", "未命名便签")}")
            appendLine("  content=${revision.debugText("contentSnapshot", "<empty>").take(48)}")
            appendLine("  tags=${revision.debugText("tagsSnapshotJson")}")
            appendLine(
                "  state type=${revision.debugText("typeSnapshot")} " +
                    "done=${revision.debugText("isDoneSnapshot")} " +
                    "pinned=${revision.debugText("pinnedSnapshot")} " +
                    "archived=${revision.debugText("archivedSnapshot")} " +
                    "deleted=${revision.debugText("deletedSnapshot")}",
            )
        }
    }

    private fun Any?.debugLong(name: String): Long? = debugValue(name)?.toString()?.toLongOrNull()
    private fun Any?.debugText(name: String, fallback: String = "-"): String = debugValue(name)?.toString()?.trim().orEmpty().ifBlank { fallback }
    private fun Any?.debugStorageValue(): String {
        val target = this ?: return "-"
        val value = target.debugValue("storageValue")?.toString()?.trim().orEmpty()
        return value.ifBlank { target.toString() }
    }
    private fun Any?.debugValue(name: String): Any? {
        val target = this ?: return null
        if (name.isBlank()) return null
        val capitalized = name.substring(0, 1).uppercase() + name.substring(1)
        val candidates = buildList {
            if (name.startsWith("is")) add(name)
            add("get$capitalized")
            add(name)
        }
        return runCatching {
            val method = target.javaClass.methods.firstOrNull { method -> method.parameterCount == 0 && candidates.contains(method.name) }
            if (method != null) {
                method.invoke(target)
            } else {
                val field = target.javaClass.declaredFields.firstOrNull { it.name == name } ?: return@runCatching null
                field.isAccessible = true
                field.get(target)
            }
        }.getOrNull()
    }
}

data class SettingsUiState(
    val homeBackgroundColor: String = SettingsRepository.DEFAULT_HOME_BACKGROUND,
    val tagDrawerBackgroundColor: String = SettingsRepository.DEFAULT_TAG_DRAWER_BACKGROUND,
    val toolName: String = "notes.create",
    val argumentsJson: String = "{}",
    val isRunning: Boolean = false,
    val resultText: String = "尚未执行命令",
    val pendingConfirmationId: String? = null,
    val revisionNoteId: String = "",
    val revisionToRestoreId: String = "",
    val revisionText: String = "输入 note_id 后点击刷新 revision。",
    val assistantState: AssistantState = AssistantState.disabled(),
    val assistantTextInput: String = "你好，请简单介绍一下自己",
    val phase4ToolName: String = "notes.create",
    val phase4ArgumentsJson: String = "{}",
    val recentLogs: List<AssistantCommandLog> = emptyList(),
)

private data class ColorOption(val name: String, val hex: String)

data class ToolSample(
    val label: String,
    val toolName: String,
    val argumentsJson: String,
)

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = Color(0xFF222832))
}

@Composable
private fun Phase3RuntimeBox(
    state: SettingsUiState,
    onEnableClick: () -> Unit,
    onDisableClick: () -> Unit,
    onPrepareIdentityClick: () -> Unit,
    onResetIdentityClick: () -> Unit,
    onUseFakeRuntimeClick: () -> Unit,
    onUseRealRuntimeClick: () -> Unit,
    onFakeActivationClick: () -> Unit,
    onRealActivationClick: () -> Unit,
    onConnectClick: () -> Unit,
    onReconnectClick: () -> Unit,
    onDisconnectClick: () -> Unit,
    onTextInputChange: (String) -> Unit,
    onSendTextClick: () -> Unit,
    onStartPttClick: () -> Unit,
    onStopPttClick: () -> Unit,
    onSimulateToolCallClick: () -> Unit,
    onPhase4ToolNameChange: (String) -> Unit,
    onPhase4ArgumentsChange: (String) -> Unit,
    onPhase4SampleClick: (ToolSample) -> Unit,
    onListPhase4ToolsClick: () -> Unit,
    onExecutePhase4ToolClick: () -> Unit,
    onSimulateCloseClick: () -> Unit,
    onSimulateFailureClick: () -> Unit,
    onSimulateAudioFailureClick: () -> Unit,
) {
    val assistant = state.assistantState
    Column(
        modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Phase3/Phase4 助手运行时", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("当前支持 Phase3 Fake/Real runtime；Phase4 MCP tools/call 通过 runtime executor 进入 assistant-tools。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        Text("mode=${assistant.runtimeMode.storageValue} · phase=${assistant.phase.storageValue} · connection=${assistant.connection.storageValue} · activation=${assistant.activation.storageValue} · audio=${assistant.audio.storageValue}", color = Color(0xFF344054), style = MaterialTheme.typography.bodySmall)
        Text("GateB real: handshake=${assistant.gateBRealHandshakeVerified} · text=${assistant.gateBRealTextVerified} · audio_up=${assistant.gateBRealAudioUploadVerified} · audio_play=${assistant.gateBRealAudioPlaybackVerified} · tool_block=${assistant.gateBRealToolCallBlockedVerified}", color = Color(0xFF344054), style = MaterialTheme.typography.bodySmall)
        Text("status=${assistant.statusText}", color = Color(0xFF344054), style = MaterialTheme.typography.bodySmall)
        assistant.errorMessage?.let { Text("error=$it", color = Color(0xFFB42318), style = MaterialTheme.typography.bodySmall) }
        assistant.deviceId?.let { Text("device_id=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.clientId?.let { Text("client_id=${it.take(8)}...", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.websocketUrl?.let { Text("websocket=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.activationCode?.let { Text("activation_code=$it", color = Color(0xFF9A3412), style = MaterialTheme.typography.bodySmall) }
        assistant.sessionId?.let { Text("session=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastAssistantText?.let { Text("last assistant=$it", color = Color(0xFF344054), style = MaterialTheme.typography.bodySmall) }
        if (assistant.audioCapturedFrames > 0 || assistant.pushToTalkStopLatencyMs != null) {
            Text("audio pcm=${assistant.audioCapturedFrames} · opus=${assistant.audioEncodedFrames} · uploaded=${assistant.audioUploadedFrames} · stop=${assistant.pushToTalkStopLatencyMs ?: "-"}ms", color = Color(0xFF344054), style = MaterialTheme.typography.bodySmall)
        }
        assistant.lastAudioSummary?.let { Text("audio=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastProtocolEvent?.let { Text("event=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastReconnectDecision?.let { Text("reconnect=$it · attempt=${assistant.reconnectAttempt} · errors=${assistant.runtimeErrorCount}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastCloseReason?.let { Text("last close=${assistant.lastCloseCode ?: "-"} / $it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastClientJson?.let { Text("last client=${it.take(180)}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
        assistant.lastServerJson?.let { Text("last server=${it.take(180)}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onEnableClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("启用助手") }
            Button(onClick = onDisableClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280))) { Text("关闭助手") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onUseFakeRuntimeClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("使用 Fake") }
            Button(onClick = onUseRealRuntimeClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))) { Text("使用 Real") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onPrepareIdentityClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("准备身份") }
            Button(onClick = onResetIdentityClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280))) { Text("重置身份") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onFakeActivationClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("Fake 激活") }
            Button(onClick = onRealActivationClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF7C3AED))) { Text("真实 OTA") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onConnectClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("连接当前模式") }
            Button(onClick = onReconnectClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("重连") }
            Button(onClick = onDisconnectClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280))) { Text("断开") }
        }
        OutlinedTextField(value = state.assistantTextInput, onValueChange = onTextInputChange, label = { Text("文本对话测试") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
        Button(onClick = onSendTextClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text("发送文本到当前模式") }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onStartPttClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("开始 PTT（请求麦克风）") }
            Button(onClick = onStopPttClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("结束 PTT") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onSimulateCloseClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB45309))) { Text("模拟异常关闭") }
            Button(onClick = onSimulateFailureClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) { Text("模拟连接失败") }
        }
        Button(onClick = onSimulateAudioFailureClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFB42318))) { Text("模拟音频失败") }
        Button(onClick = onSimulateToolCallClick, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))) { Text("旧入口：模拟 notes.delete MCP 调用") }

        Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF8FAFC), RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Phase4 MCP 工具模拟器", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("这里走 runtime MCP 链路：Fake runtime -> router -> McpProtocolClient -> McpToolExecutor -> assistant-tools。下面的 Phase2 模拟器不算 Phase4 MCP 验收。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
            Text("Gate D 样例已补全：读列表、归档/恢复、清理完成、标签、UI 展示和 pending confirmation。", color = Color(0xFF0F766E), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                phase4McpSamples.forEach { sample ->
                    Surface(onClick = { onPhase4SampleClick(sample) }, shape = RoundedCornerShape(14.dp), color = Color.White, border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                        Text(sample.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            OutlinedTextField(value = state.phase4ToolName, onValueChange = onPhase4ToolNameChange, label = { Text("Phase4 MCP tool name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = state.phase4ArgumentsJson, onValueChange = onPhase4ArgumentsChange, label = { Text("Phase4 MCP arguments JSON") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onListPhase4ToolsClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E))) { Text("列出 Phase4 tools/list") }
                Button(onClick = onExecutePhase4ToolClick, shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))) { Text("执行 Phase4 tools/call") }
            }
            Text("执行后看上方 last server / last client / last assistant，以及下方最近命令日志。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun ToolSimulatorBox(
    state: SettingsUiState,
    onToolNameChange: (String) -> Unit,
    onArgumentsChange: (String) -> Unit,
    onSampleClick: (ToolSample) -> Unit,
    onExecuteClick: () -> Unit,
    onConfirmClick: () -> Unit,
    onRejectClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Phase2 本地工具模拟器", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("支持完整 tool name + arguments JSON。高风险命令先返回 requires_confirmation，必须确认后才执行。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            toolSamples.forEach { sample ->
                Surface(onClick = { onSampleClick(sample) }, shape = RoundedCornerShape(14.dp), color = Color(0xFFF6F7FB), border = BorderStroke(1.dp, Color(0xFFE5E7EB))) {
                    Text(sample.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        OutlinedTextField(value = state.toolName, onValueChange = onToolNameChange, label = { Text("tool name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = state.argumentsJson, onValueChange = onArgumentsChange, label = { Text("arguments JSON") }, minLines = 4, maxLines = 8, modifier = Modifier.fillMaxWidth())
        Button(onClick = onExecuteClick, enabled = !state.isRunning, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(if (state.isRunning) "执行中……" else "执行 tools/call") }
        state.pendingConfirmationId?.let { confirmationId ->
            Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFFFF7ED), RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("待确认操作", fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
                Text("confirmation_id=$confirmationId", color = Color(0xFF9A3412), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onConfirmClick, enabled = !state.isRunning, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("确认执行") }
                    Button(onClick = onRejectClick, enabled = !state.isRunning, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)), shape = RoundedCornerShape(14.dp), modifier = Modifier.weight(1f)) { Text("拒绝") }
                }
            }
        }
        Text("执行结果", color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodySmall)
        Text(state.resultText, modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp)).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF344054))
    }
}

@Composable
private fun RevisionDebugBox(
    state: SettingsUiState,
    onNoteIdChange: (String) -> Unit,
    onRevisionIdChange: (String) -> Unit,
    onLoadClick: () -> Unit,
    onApplyRestoreSampleClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Revision 验证入口", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("输入 note_id 查看最近 revision；套用 restore_revision 后仍需走高风险确认。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.revisionNoteId, onValueChange = onNoteIdChange, label = { Text("note_id") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = onLoadClick, shape = RoundedCornerShape(14.dp), enabled = !state.isRunning) { Text("刷新") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = state.revisionToRestoreId, onValueChange = onRevisionIdChange, label = { Text("revision_id") }, singleLine = true, modifier = Modifier.weight(1f))
            Button(onClick = onApplyRestoreSampleClick, shape = RoundedCornerShape(14.dp), enabled = !state.isRunning) { Text("套用恢复") }
        }
        Text(state.revisionText, modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp)).padding(10.dp), style = MaterialTheme.typography.bodySmall, color = Color(0xFF344054))
    }
}

@Composable
private fun CommandLogBox(logs: List<AssistantCommandLog>) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("最近命令日志", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (logs.isEmpty()) {
            Text("暂无命令日志。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        } else {
            logs.forEach { log ->
                Column(modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${log.id} ${log.toolName.storageValue}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFF344054))
                        Text(log.status.storageValue, color = Color(0xFF3D6BFF), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("source=${log.source.storageValue} · risk=${log.riskLevel.storageValue} · confirmation=${log.confirmationStatus.storageValue}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
                    Text("created=${log.createdAt} · completed=${log.completedAt ?: "-"}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
                    log.errorMessage?.let { Text(it, color = Color(0xFFB42318), style = MaterialTheme.typography.bodySmall) }
                    log.affectedNoteIdsJson?.let { Text("notes=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
                    log.affectedTagIdsJson?.let { Text("tags=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
                    log.resultJson?.let { Text("result=${it.take(140)}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun SettingBox(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColorSettingBox(label: String, selectedHex: String, options: List<ColorOption>, onSelect: (String) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp)).padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                Surface(onClick = { onSelect(option.hex) }, shape = RoundedCornerShape(14.dp), color = if (selectedHex == option.hex) Color(0xFFEDE0FF) else Color(0xFFF6F7FB), border = BorderStroke(1.dp, if (selectedHex == option.hex) Color.Transparent else Color(0xFFE5E7EB))) {
                    Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(14.dp).background(colorFromHex(option.hex), CircleShape))
                        Text(option.name, style = MaterialTheme.typography.labelMedium, color = Color(0xFF344054))
                    }
                }
            }
        }
    }
}

private val homeBackgroundOptions = listOf(
    ColorOption("白色", "#FFFFFF"),
    ColorOption("米白", "#FFFDF7"),
    ColorOption("暖杏", "#F8F3EA"),
    ColorOption("浅蓝", "#F3F6FB"),
    ColorOption("深色", "#1F2430"),
    ColorOption("黑色", "#000000"),
)

private val tagDrawerBackgroundOptions = listOf(
    ColorOption("暖黄", "#FFF3D1"),
    ColorOption("米白", "#FFFDF7"),
    ColorOption("浅蓝", "#E7F0FF"),
    ColorOption("浅绿", "#E4F6EC"),
    ColorOption("深色", "#1F2430"),
    ColorOption("黑色", "#000000"),
)

private val phase4McpSamples = listOf(
    ToolSample("创建", "notes.create", "{\"title\":\"Phase4 MCP 创建\",\"content\":\"从 runtime MCP executor 创建\",\"type\":\"normal\",\"tags\":[\"Phase4\"]}"),
    ToolSample("搜索", "notes.search", "{\"query\":\"Phase4\",\"limit\":10}"),
    ToolSample("最近", "notes.list_recent", "{\"limit\":5,\"include_archived\":true}"),
    ToolSample("获取", "notes.get", "{\"note_id\":1}"),
    ToolSample("追加", "notes.append", "{\"note_id\":1,\"content\":\"Phase4 MCP 追加一行\"}"),
    ToolSample("改标题", "notes.update_title", "{\"note_id\":1,\"title\":\"Phase4 GateB 已改标题\"}"),
    ToolSample("完成", "notes.toggle_done", "{\"note_id\":1,\"done\":true}"),
    ToolSample("置顶", "notes.pin", "{\"note_id\":1,\"pinned\":true}"),
    ToolSample("归档", "notes.archive", "{\"note_id\":1,\"archived\":true}"),
    ToolSample("恢复", "notes.restore", "{\"note_id\":1}"),
    ToolSample("清完成", "notes.clear_done", "{\"include_archived\":false}"),
    ToolSample("归档列表", "notes.list_archived", "{\"limit\":10}"),
    ToolSample("删除列表", "notes.list_deleted", "{\"limit\":10}"),
    ToolSample("待办列表", "notes.list_todos", "{\"include_done\":true,\"limit\":10}"),
    ToolSample("完成列表", "notes.list_done", "{\"limit\":10}"),
    ToolSample("打开", "ui.open_note", "{\"note_id\":1}"),
    ToolSample("显示搜索", "ui.show_search", "{\"query\":\"Phase4\"}"),
    ToolSample("显示列表", "ui.show_note_list", "{}"),
    ToolSample("显示标签", "ui.show_tag", "{\"tag_name\":\"Phase4\"}"),
    ToolSample("显示归档", "ui.show_archive", "{}"),
    ToolSample("显示回收站", "ui.show_trash", "{}"),
    ToolSample("建标签", "tags.create", "{\"name\":\"GateD\"}"),
    ToolSample("改标签", "tags.rename", "{\"tag_id\":1,\"name\":\"GateD 已改名\"}"),
    ToolSample("标签列表", "tags.list", "{\"limit\":50}"),
    ToolSample("搜标签", "tags.search", "{\"query\":\"GateD\",\"limit\":10}"),
    ToolSample("加标签", "tags.bind", "{\"note_id\":1,\"tags\":[\"语音\",\"GateB\"],\"mode\":\"add\"}"),
    ToolSample("移标签", "tags.bind", "{\"note_id\":1,\"tags\":[\"语音\"],\"mode\":\"remove\"}"),
    ToolSample("替换标签", "tags.bind", "{\"note_id\":1,\"tags\":[\"替换后\",\"GateC\"],\"mode\":\"replace\"}"),
    ToolSample("删标签", "tags.delete", "{\"tag_id\":1}"),
    ToolSample("覆盖正文", "notes.replace_content", "{\"note_id\":1,\"content\":\"这段正文需要确认后才会覆盖原内容\"}"),
    ToolSample("恢复版本", "notes.restore_revision", "{\"note_id\":1,\"revision_id\":1}"),
    ToolSample("删除", "notes.delete", "{\"note_id\":1}"),
    ToolSample("列确认", "assistant.list_pending_confirmations", "{\"limit\":10}"),
    ToolSample("显确认", "ui.show_confirmation", "{\"confirmation_id\":\"粘贴 requires_confirmation 返回的 id\",\"title\":\"待确认操作\",\"message\":\"请确认是否执行这个高风险操作。\"}"),
    ToolSample("确认", "assistant.confirm", "{\"confirmation_id\":\"粘贴 requires_confirmation 返回的 id\"}"),
    ToolSample("拒绝", "assistant.reject", "{\"confirmation_id\":\"粘贴 requires_confirmation 返回的 id\"}"),
)

private val toolSamples = listOf(
    ToolSample("创建", "notes.create", "{\"title\":\"Phase2 模拟创建\",\"content\":\"从本地工具模拟器创建\",\"type\":\"normal\",\"tags\":[\"Phase2\"]}"),
    ToolSample("搜索", "notes.search", "{\"query\":\"phase2\",\"limit\":10}"),
    ToolSample("最近", "notes.list_recent", "{\"limit\":5,\"include_archived\":true}"),
    ToolSample("追加", "notes.append", "{\"note_id\":1,\"content\":\"模拟追加一行内容\"}"),
    ToolSample("改标题", "notes.update_title", "{\"note_id\":1,\"title\":\"通过命令改标题\"}"),
    ToolSample("覆盖正文", "notes.replace_content", "{\"note_id\":1,\"content\":\"这是一段确认后才会覆盖的新正文\"}"),
    ToolSample("完成", "notes.toggle_done", "{\"note_id\":1,\"done\":true}"),
    ToolSample("置顶", "notes.pin", "{\"note_id\":1,\"pinned\":true}"),
    ToolSample("归档", "notes.archive", "{\"note_id\":1,\"archived\":true}"),
    ToolSample("删除", "notes.delete", "{\"note_id\":1}"),
    ToolSample("加标签", "tags.bind", "{\"note_id\":1,\"tags\":[\"客户\",\"Phase2\"],\"mode\":\"add\"}"),
    ToolSample("替换标签", "tags.bind", "{\"note_id\":1,\"tags\":[\"替换后\"],\"mode\":\"replace\"}"),
    ToolSample("删除标签", "tags.delete", "{\"tag_id\":1}"),
    ToolSample("恢复版本", "notes.restore_revision", "{\"note_id\":1,\"revision_id\":1}"),
)

private fun colorFromHex(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.White)
