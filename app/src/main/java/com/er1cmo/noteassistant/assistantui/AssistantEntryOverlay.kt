package com.er1cmo.noteassistant.assistantui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantRuntimeMode
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@Composable
fun AssistantEntryOverlay(
    modifier: Modifier = Modifier,
    viewModel: AssistantEntryViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val microphoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.startPushToTalk(hasRecordAudioPermission = true)
    }

    fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
    ) {
        if (uiState.expanded) {
            AssistantExpandedPanel(
                uiState = uiState,
                onCollapse = viewModel::collapse,
                onEnableClick = viewModel::toggleAssistantEnabled,
                onModeClick = viewModel::toggleRuntimeMode,
                onActivateClick = viewModel::activateCurrentRuntime,
                onConnectClick = viewModel::connectOrDisconnect,
                onInputChange = viewModel::setInputText,
                onSendClick = viewModel::sendCurrentText,
                onStartPtt = {
                    if (hasRecordAudioPermission()) {
                        viewModel.startPushToTalk(hasRecordAudioPermission = true)
                    } else {
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopPtt = viewModel::stopPushToTalk,
            )
            Spacer(Modifier.height(10.dp))
        }
        AssistantAuroraButton(
            uiState = uiState,
            onClick = viewModel::toggleExpanded,
        )
    }
}

@Composable
private fun AssistantAuroraButton(
    uiState: AssistantEntryUiState,
    onClick: () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "assistant_aurora")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "aurora_rotation",
    )
    val borderColor = when {
        uiState.state.audio == AssistantAudioStatus.Recording -> Color(0xFFFFF176)
        uiState.state.isConnected -> Color(0xFFB2FF59)
        uiState.state.assistantEnabled -> Color(0xFF80D8FF)
        else -> Color.White.copy(alpha = 0.45f)
    }

    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(1.5.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer(rotationZ = rotation)
                .background(
                    brush = Brush.sweepGradient(
                        listOf(
                            Color(0xFF7C4DFF),
                            Color(0xFF00E5FF),
                            Color(0xFFFF4081),
                            Color(0xFF69F0AE),
                            Color(0xFF7C4DFF),
                        ),
                    ),
                    shape = CircleShape,
                ),
        )
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.24f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "小智",
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = uiState.compactStatusLabel,
                    color = Color.White.copy(alpha = 0.92f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AssistantExpandedPanel(
    uiState: AssistantEntryUiState,
    onCollapse: () -> Unit,
    onEnableClick: () -> Unit,
    onModeClick: () -> Unit,
    onActivateClick: () -> Unit,
    onConnectClick: () -> Unit,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStartPtt: () -> Unit,
    onStopPtt: () -> Unit,
) {
    Surface(
        modifier = Modifier.width(330.dp),
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 10.dp,
        shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "小智语音助手",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = uiState.statusLine,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                TextButton(onClick = onCollapse) { Text("收起") }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                AssistantStatusChip(uiState.enabledLabel)
                AssistantStatusChip(uiState.modeLabel)
                AssistantStatusChip(uiState.connectionLabel)
                AssistantStatusChip(uiState.activationLabel)
                AssistantStatusChip(uiState.audioLabel)
            }

            if (!uiState.state.lastAssistantText.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                ) {
                    Column(Modifier.padding(10.dp)) {
                        Text(
                            text = "最近助手回复",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Text(
                            text = uiState.state.lastAssistantText.orEmpty(),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            Text(
                text = uiState.state.statusText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onEnableClick, modifier = Modifier.weight(1f)) {
                    Text(if (uiState.state.assistantEnabled) "关闭" else "启用")
                }
                OutlinedButton(onClick = onModeClick, modifier = Modifier.weight(1f)) {
                    Text(if (uiState.state.isRealRuntime) "切 Fake" else "切 Real")
                }
                OutlinedButton(onClick = onActivateClick, modifier = Modifier.weight(1f)) {
                    Text("激活")
                }
            }

            Button(onClick = onConnectClick, modifier = Modifier.fillMaxWidth()) {
                Text(if (uiState.state.isConnected) "断开当前模式" else "连接当前模式")
            }

            OutlinedTextField(
                value = uiState.inputText,
                onValueChange = onInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("文本调试入口") },
                placeholder = { Text("例如：帮我创建一条便签") },
                minLines = 1,
                maxLines = 3,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onSendClick, modifier = Modifier.weight(1f)) {
                    Text("发送文本")
                }
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = {
                                    onStartPtt()
                                    try {
                                        tryAwaitRelease()
                                    } finally {
                                        onStopPtt()
                                    }
                                },
                            )
                        },
                    shape = RoundedCornerShape(16.dp),
                    color = if (uiState.state.audio == AssistantAudioStatus.Recording) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer
                    },
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (uiState.state.audio == AssistantAudioStatus.Recording) "松手发送" else "按住说话",
                            color = if (uiState.state.audio == AssistantAudioStatus.Recording) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onTertiaryContainer
                            },
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AssistantStatusChip(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

data class AssistantEntryUiState(
    val state: AssistantState,
    val expanded: Boolean,
    val inputText: String,
) {
    val enabledLabel: String = if (state.assistantEnabled) "enabled" else "disabled"
    val modeLabel: String = state.runtimeMode.storageValue
    val connectionLabel: String = state.connection.storageValue
    val activationLabel: String = when (state.activation) {
        AssistantActivationStatus.Activated -> "activated"
        else -> state.activation.storageValue
    }
    val audioLabel: String = state.audio.storageValue
    val compactStatusLabel: String = when {
        !state.assistantEnabled -> "off"
        state.audio == AssistantAudioStatus.Recording -> "listening"
        state.isConnected -> "online"
        state.connection == AssistantConnectionStatus.Connecting -> "linking"
        else -> state.runtimeMode.storageValue
    }
    val statusLine: String = buildString {
        append(enabledLabel)
        append(" / ")
        append(modeLabel)
        append(" / ")
        append(connectionLabel)
        append(" / ")
        append(activationLabel)
    }
}

@HiltViewModel
class AssistantEntryViewModel @Inject constructor(
    private val assistantController: AssistantController,
) : ViewModel() {
    private val expanded = MutableStateFlow(false)
    private val inputText = MutableStateFlow("小智，帮我创建一条测试便签")

    val uiState: StateFlow<AssistantEntryUiState> = combine(
        assistantController.state,
        expanded,
        inputText,
    ) { state, expandedValue, input ->
        AssistantEntryUiState(
            state = state,
            expanded = expandedValue,
            inputText = input,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantEntryUiState(
            state = assistantController.state.value,
            expanded = false,
            inputText = inputText.value,
        ),
    )

    fun toggleExpanded() {
        expanded.value = !expanded.value
    }

    fun collapse() {
        expanded.value = false
    }

    fun setInputText(value: String) {
        inputText.value = value
    }

    fun toggleAssistantEnabled() {
        viewModelScope.launch {
            if (assistantController.state.value.assistantEnabled) {
                assistantController.disableAssistant()
            } else {
                assistantController.enableAssistant()
            }
        }
    }

    fun toggleRuntimeMode() {
        viewModelScope.launch {
            ensureEnabled()
            if (assistantController.state.value.isRealRuntime) {
                assistantController.useFakeRuntime()
            } else {
                assistantController.useRealRuntime()
            }
        }
    }

    fun activateCurrentRuntime() {
        viewModelScope.launch {
            ensureEnabled()
            if (assistantController.state.value.runtimeMode == AssistantRuntimeMode.Real) {
                assistantController.runRealActivation()
            } else {
                assistantController.runFakeActivation()
            }
        }
    }

    fun connectOrDisconnect() {
        viewModelScope.launch {
            ensureEnabled()
            if (assistantController.state.value.isConnected) {
                assistantController.disconnect(reason = "assistant_entry_overlay")
            } else {
                assistantController.connect()
            }
        }
    }

    fun sendCurrentText() {
        val text = inputText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            ensureEnabled()
            if (!assistantController.state.value.isConnected) {
                assistantController.connect()
            }
            assistantController.sendText(text)
        }
    }

    fun startPushToTalk(hasRecordAudioPermission: Boolean) {
        viewModelScope.launch {
            ensureEnabled()
            if (!assistantController.state.value.isConnected) {
                assistantController.connect()
            }
            assistantController.startPushToTalk(hasRecordAudioPermission = hasRecordAudioPermission)
        }
    }

    fun stopPushToTalk() {
        viewModelScope.launch {
            assistantController.stopPushToTalk()
        }
    }

    private suspend fun ensureEnabled() {
        if (!assistantController.state.value.assistantEnabled) {
            assistantController.enableAssistant()
        }
    }
}
