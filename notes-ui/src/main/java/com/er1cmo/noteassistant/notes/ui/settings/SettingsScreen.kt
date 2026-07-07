package com.er1cmo.noteassistant.notes.ui.settings

import android.graphics.Color as AndroidColor
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.notes.domain.command.CommandResult
import com.er1cmo.noteassistant.notes.domain.command.CommandSource
import com.er1cmo.noteassistant.notes.domain.command.NoteCommandService
import com.er1cmo.noteassistant.notes.domain.model.AssistantCommandLog
import com.er1cmo.noteassistant.notes.domain.repository.CommandTraceRepository
import com.er1cmo.noteassistant.notes.ui.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
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
                    Text("设置与 Phase2 调试", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                StatusPill(text = "本地优先")
            }
            SettingBox("WebSocket 地址", "Phase3 再接入真实小智服务")
            SettingBox("语音助手状态", "Phase2 只做可信命令路径，不接麦克风 / WebSocket")
            SettingBox("搜索模式", "手动搜索与命令搜索逐步收口到 SearchNotesUseCase")
            SettingBox("数据库", "本地 SQLite note_assistant.db")
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
            ToolSimulatorBox(
                state = state,
                onToolNameChange = viewModel::setToolName,
                onArgumentsChange = viewModel::setArgumentsJson,
                onSampleClick = viewModel::applySample,
                onExecuteClick = viewModel::executeTool,
                onConfirmClick = viewModel::confirmPending,
                onRejectClick = viewModel::rejectPending,
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
    commandTraceRepository: CommandTraceRepository,
) : ViewModel() {
    private val toolName = MutableStateFlow("notes.create")
    private val argumentsJson = MutableStateFlow("{\"title\":\"Phase2 模拟创建\",\"content\":\"从本地工具模拟器创建\",\"type\":\"normal\",\"tags\":[\"Phase2\"]}")
    private val isRunning = MutableStateFlow(false)
    private val resultText = MutableStateFlow("尚未执行命令")
    private val pendingConfirmationId = MutableStateFlow<String?>(null)

    private data class ThemeState(val home: String, val tagDrawer: String)
    private data class SimulatorState(
        val toolName: String,
        val argumentsJson: String,
        val isRunning: Boolean,
        val resultText: String,
        val pendingConfirmationId: String?,
    )

    private val themeState = combine(
        settingsRepository.homeBackgroundColor,
        settingsRepository.tagDrawerBackgroundColor,
    ) { home, tagDrawer -> ThemeState(home = home, tagDrawer = tagDrawer) }

    private val simulatorState = combine(toolName, argumentsJson, isRunning, resultText, pendingConfirmationId) { tool, args, running, result, confirmationId ->
        SimulatorState(
            toolName = tool,
            argumentsJson = args,
            isRunning = running,
            resultText = result,
            pendingConfirmationId = confirmationId,
        )
    }

    val state = combine(
        themeState,
        simulatorState,
        commandTraceRepository.observeRecentCommandLogs(10),
    ) { theme, simulator, logs ->
        SettingsUiState(
            homeBackgroundColor = theme.home,
            tagDrawerBackgroundColor = theme.tagDrawer,
            toolName = simulator.toolName,
            argumentsJson = simulator.argumentsJson,
            isRunning = simulator.isRunning,
            resultText = simulator.resultText,
            pendingConfirmationId = simulator.pendingConfirmationId,
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

    fun setToolName(value: String) {
        toolName.value = value
    }

    fun setArgumentsJson(value: String) {
        argumentsJson.value = value
    }

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
}

data class SettingsUiState(
    val homeBackgroundColor: String = SettingsRepository.DEFAULT_HOME_BACKGROUND,
    val tagDrawerBackgroundColor: String = SettingsRepository.DEFAULT_TAG_DRAWER_BACKGROUND,
    val toolName: String = "notes.create",
    val argumentsJson: String = "{}",
    val isRunning: Boolean = false,
    val resultText: String = "尚未执行命令",
    val pendingConfirmationId: String? = null,
    val recentLogs: List<AssistantCommandLog> = emptyList(),
)

private data class ColorOption(val name: String, val hex: String)

data class ToolSample(
    val label: String,
    val toolName: String,
    val argumentsJson: String,
)

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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Phase2 本地工具模拟器", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "支持中低风险直接执行；notes.delete / notes.replace_content / tags.delete / tags.bind replace 会先返回 requires_confirmation。",
            color = Color(0xFF697386),
            style = MaterialTheme.typography.bodySmall,
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            toolSamples.forEach { sample ->
                Surface(
                    onClick = { onSampleClick(sample) },
                    shape = RoundedCornerShape(14.dp),
                    color = Color(0xFFF6F7FB),
                    border = BorderStroke(1.dp, Color(0xFFE5E7EB)),
                ) {
                    Text(sample.label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        OutlinedTextField(
            value = state.toolName,
            onValueChange = onToolNameChange,
            label = { Text("tool name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.argumentsJson,
            onValueChange = onArgumentsChange,
            label = { Text("arguments JSON") },
            minLines = 4,
            maxLines = 8,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onExecuteClick,
            enabled = !state.isRunning,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.isRunning) "执行中……" else "执行 tools/call")
        }
        state.pendingConfirmationId?.let { confirmationId ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFFF7ED), RoundedCornerShape(16.dp))
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("待确认操作", fontWeight = FontWeight.SemiBold, color = Color(0xFF9A3412))
                Text("confirmation_id=$confirmationId", color = Color(0xFF9A3412), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onConfirmClick,
                        enabled = !state.isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("确认执行") }
                    Button(
                        onClick = onRejectClick,
                        enabled = !state.isRunning,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6B7280)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("拒绝") }
                }
            }
        }
        Text("执行结果", color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodySmall)
        Text(
            state.resultText,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp))
                .padding(10.dp),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
    }
}

@Composable
private fun CommandLogBox(logs: List<AssistantCommandLog>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("最近命令日志", color = Color(0xFF222832), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        if (logs.isEmpty()) {
            Text("暂无命令日志。", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
        } else {
            logs.forEach { log ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF6F7FB), RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("#${log.id} ${log.toolName.storageValue}", modifier = Modifier.weight(1f), fontWeight = FontWeight.SemiBold, color = Color(0xFF344054))
                        Text(log.status.storageValue, color = Color(0xFF3D6BFF), style = MaterialTheme.typography.labelMedium)
                    }
                    Text("risk=${log.riskLevel.storageValue} · confirmation=${log.confirmationStatus.storageValue}", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall)
                    log.errorMessage?.let { Text(it, color = Color(0xFFB42318), style = MaterialTheme.typography.bodySmall) }
                    log.affectedNoteIdsJson?.let { Text("notes=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
                    log.affectedTagIdsJson?.let { Text("tags=$it", color = Color(0xFF697386), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun SettingBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ColorSettingBox(
    label: String,
    selectedHex: String,
    options: List<ColorOption>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEach { option ->
                Surface(
                    onClick = { onSelect(option.hex) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedHex == option.hex) Color(0xFFEDE0FF) else Color(0xFFF6F7FB),
                    border = BorderStroke(1.dp, if (selectedHex == option.hex) Color.Transparent else Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .background(colorFromHex(option.hex), CircleShape),
                        )
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
)

private fun colorFromHex(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.White)
