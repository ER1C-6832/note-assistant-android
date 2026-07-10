package com.er1cmo.noteassistant.assistantui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantRuntimeMode
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallEventStore
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiState
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

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
        if (granted) viewModel.startPreferredVoiceInteraction(hasRecordAudioPermission = true)
    }

    fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (uiState.toolCall.visible) {
            AssistantOperationBanner(
                toolCall = uiState.toolCall,
                onDismiss = viewModel::dismissToolCall,
            )
        }
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
                onStartHoldToTalk = {
                    if (hasRecordAudioPermission()) {
                        viewModel.startPushToTalk(hasRecordAudioPermission = true)
                    } else {
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopHoldToTalk = viewModel::stopPushToTalk,
                onStartStreaming = {
                    if (hasRecordAudioPermission()) {
                        viewModel.startStreamingConversation(hasRecordAudioPermission = true)
                    } else {
                        microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                },
                onStopStreaming = viewModel::stopStreamingConversation,
            )
        }
        CompactAssistantAuroraButton(
            uiState = uiState,
            onClick = viewModel::toggleExpanded,
        )
    }
}

@Composable
private fun CompactAssistantAuroraButton(
    uiState: AssistantEntryUiState,
    onClick: () -> Unit,
) {
    val target = remember(uiState.state) { uiState.state.toAuroraTarget() }
    val transitionSpec = tween<Float>(durationMillis = 850, easing = FastOutSlowInEasing)
    val colorTransitionSpec = tween<Color>(durationMillis = 850, easing = FastOutSlowInEasing)

    val globalAlpha by animateFloatAsState(
        targetValue = target.globalAlpha,
        animationSpec = transitionSpec,
        label = "entry_aurora_global_alpha",
    )
    val fluidScale by animateFloatAsState(
        targetValue = target.fluidScale,
        animationSpec = transitionSpec,
        label = "entry_aurora_fluid_scale",
    )
    val animationSpeedScale by animateFloatAsState(
        targetValue = target.animationSpeedScale,
        animationSpec = transitionSpec,
        label = "entry_aurora_animation_speed",
    )
    val colorBlendRatio by animateFloatAsState(
        targetValue = target.colorBlendRatio,
        animationSpec = transitionSpec,
        label = "entry_aurora_color_blend",
    )
    val animatedColorA by animateColorAsState(
        targetValue = target.colorA,
        animationSpec = colorTransitionSpec,
        label = "entry_aurora_color_a",
    )
    val animatedColorB by animateColorAsState(
        targetValue = target.colorB,
        animationSpec = colorTransitionSpec,
        label = "entry_aurora_color_b",
    )
    val animatedColorC by animateColorAsState(
        targetValue = target.colorC ?: ColorSunsetAmber,
        animationSpec = colorTransitionSpec,
        label = "entry_aurora_color_c",
    )

    val elapsedSeconds by produceState(initialValue = 0f, target.motion) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                value = ((frameNanos - startNanos) / 1_000_000_000f).coerceAtLeast(0f)
            }
        }
    }

    val borderColor = when (target.motion) {
        AuroraMotion.Disabled -> Color.White.copy(alpha = 0.20f)
        AuroraMotion.Connecting -> ColorSunsetAmber.copy(alpha = 0.40f)
        AuroraMotion.Connected -> ColorMistyBlue.copy(alpha = 0.40f)
        AuroraMotion.Listening -> ColorPeach.copy(alpha = 0.45f)
        AuroraMotion.Thinking -> ColorLilac.copy(alpha = 0.45f)
        AuroraMotion.Speaking -> ColorSunsetAmber.copy(alpha = 0.52f)
        AuroraMotion.Error -> ColorRoseRed.copy(alpha = 0.55f)
    }

    Box(
        modifier = Modifier
            .size(74.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .border(1.dp, borderColor, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(74.dp)) {
            val canvasCenter = Offset(size.width / 2f, size.height / 2f)
            val baseRadius = min(size.width, size.height) * 0.28f
            val d8 = 8.dp.toPx()
            val d10 = 10.dp.toPx()
            val d12 = 12.dp.toPx()
            val d14 = 14.dp.toPx()
            val orbitRadius = min(size.width, size.height) * 0.18f
            val basePhase = elapsedSeconds * TWO_PI / BASE_CYCLE_SECONDS
            val fluidPhase = basePhase * animationSpeedScale
            val rotatingAngleRadians = elapsedSeconds * TWO_PI / THINKING_ORBIT_SECONDS
            val syntheticVolumeScale = when (target.motion) {
                AuroraMotion.Listening -> (0.18f + 0.82f * abs(sin(elapsedSeconds * 2.7f))).coerceIn(0f, 1f)
                AuroraMotion.Speaking -> (0.22f + 0.78f * abs(sin(elapsedSeconds * 3.1f + 0.6f))).coerceIn(0f, 1f)
                else -> 0f
            }
            val listeningPerturb = if (target.motion == AuroraMotion.Listening) syntheticVolumeScale * 0.42f else 0f
            val effectiveFluidScale = if (target.motion == AuroraMotion.Speaking) {
                fluidScale * (1f + syntheticVolumeScale * 0.14f)
            } else {
                fluidScale
            }

            val colorA = blendAuroraColor(ColorSlateGray, animatedColorA, colorBlendRatio)
            val colorB = blendAuroraColor(ColorSlateGray.copy(alpha = 0.55f), animatedColorB, colorBlendRatio)
            val radiusA = baseRadius * effectiveFluidScale *
                (1.08f + 0.18f * sin(fluidPhase * 1.0f) + listeningPerturb)
            val radiusB = baseRadius * effectiveFluidScale *
                (0.98f + 0.14f * cos(fluidPhase * 1.3f) + listeningPerturb)

            val defaultOffsetA = canvasCenter + Offset(
                x = cos(fluidPhase * 0.80f) * d12,
                y = sin(fluidPhase * 0.80f) * d8,
            )
            val defaultOffsetB = canvasCenter + Offset(
                x = sin(fluidPhase * 0.90f + 1.35f) * -d10,
                y = cos(fluidPhase * 0.90f + 1.35f) * d10,
            )
            val thinkingOffsetA = canvasCenter + Offset(
                x = cos(rotatingAngleRadians) * orbitRadius,
                y = sin(rotatingAngleRadians) * orbitRadius,
            )
            val thinkingOffsetB = canvasCenter + Offset(
                x = cos(rotatingAngleRadians + PI.toFloat()) * orbitRadius,
                y = sin(rotatingAngleRadians + PI.toFloat()) * orbitRadius,
            )
            val centerA = if (target.motion == AuroraMotion.Thinking) thinkingOffsetA else defaultOffsetA
            val centerB = if (target.motion == AuroraMotion.Thinking) thinkingOffsetB else defaultOffsetB

            drawFeatheredAuroraBlob(
                color = colorA,
                center = centerA,
                radius = radiusA,
                globalAlpha = globalAlpha,
            )
            drawFeatheredAuroraBlob(
                color = colorB,
                center = centerB,
                radius = radiusB,
                globalAlpha = globalAlpha,
            )

            if (target.motion == AuroraMotion.Speaking || target.colorC != null) {
                val radiusC = baseRadius * effectiveFluidScale * (0.68f + syntheticVolumeScale * 0.34f)
                val centerC = canvasCenter + Offset(
                    x = sin(fluidPhase * 0.7f) * d8,
                    y = d12 + syntheticVolumeScale * d14,
                )
                drawFeatheredAuroraBlob(
                    color = animatedColorC,
                    center = centerC,
                    radius = radiusC,
                    globalAlpha = globalAlpha * 0.90f,
                )
            }
        }

        Box(
            modifier = Modifier
                .size(53.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "小智",
                    color = Color.White.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.3.sp,
                )
                Text(
                    text = uiState.compactStatusLabel,
                    color = Color.White.copy(alpha = 0.58f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AssistantOperationBanner(
    toolCall: ToolCallUiState,
    onDismiss: () -> Unit,
) {
    val accent = toolCall.status.bannerAccent()
    Surface(
        modifier = Modifier.width(306.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF111827).copy(alpha = 0.88f),
        shadowElevation = 10.dp,
        tonalElevation = 8.dp,
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.46f)),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(accent),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = toolCall.message,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White.copy(alpha = 0.88f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = toolCall.detailLine()
                if (detail.isNotBlank()) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onDismiss) {
                Text(
                    text = "隐藏",
                    color = Color.White.copy(alpha = 0.62f),
                    style = MaterialTheme.typography.labelSmall,
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
    onStartHoldToTalk: () -> Unit,
    onStopHoldToTalk: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
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

            if (uiState.toolCall.visible) {
                InlineToolCallSummary(uiState.toolCall)
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
                AssistantVoiceActionButton(
                    state = uiState.state,
                    modifier = Modifier.weight(1f),
                    onStartHoldToTalk = onStartHoldToTalk,
                    onStopHoldToTalk = onStopHoldToTalk,
                    onStartStreaming = onStartStreaming,
                    onStopStreaming = onStopStreaming,
                )
            }
        }
    }
}

@Composable
private fun InlineToolCallSummary(toolCall: ToolCallUiState) {
    val accent = toolCall.status.bannerAccent()
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.14f),
        border = androidx.compose.foundation.BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
    ) {
        Column(Modifier.padding(10.dp)) {
            Text(
                text = "最近工具调用",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = toolCall.message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val detail = toolCall.detailLine()
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
    val toolCall: ToolCallUiState,
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
    val audioLabel: String = buildString {
        append(state.audio.storageValue)
        append("/")
        append(state.preferredVoiceMode.storageValue)
    }
    val compactStatusLabel: String = when {
        !state.assistantEnabled || state.phase == AssistantPhase.Disabled -> "关闭"
        state.phase == AssistantPhase.Error || state.audio == AssistantAudioStatus.Error -> "异常"
        state.phase == AssistantPhase.Listening || state.audio == AssistantAudioStatus.Recording -> "聆听"
        state.phase == AssistantPhase.Speaking || state.audio == AssistantAudioStatus.Playing -> "回复"
        state.phase == AssistantPhase.Thinking || state.phase == AssistantPhase.UploadingAudio -> "思考"
        state.phase == AssistantPhase.Activating ||
            state.phase == AssistantPhase.Connecting ||
            state.phase == AssistantPhase.Reconnecting ||
            state.connection == AssistantConnectionStatus.Connecting -> "连接"
        state.connection == AssistantConnectionStatus.Connected -> "在线"
        state.runtimeMode == AssistantRuntimeMode.Real -> "Real"
        else -> "Fake"
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
    private val toolCallEventStore: ToolCallEventStore,
) : ViewModel() {
    private val expanded = MutableStateFlow(false)
    private val inputText = MutableStateFlow("小智，帮我创建一条测试便签")

    val uiState: StateFlow<AssistantEntryUiState> = combine(
        assistantController.state,
        toolCallEventStore.state,
        expanded,
        inputText,
    ) { state, toolCall, expandedValue, input ->
        AssistantEntryUiState(
            state = state,
            toolCall = toolCall,
            expanded = expandedValue,
            inputText = input,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantEntryUiState(
            state = assistantController.state.value,
            toolCall = toolCallEventStore.state.value,
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

    fun dismissToolCall() {
        toolCallEventStore.clear()
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

    fun startPreferredVoiceInteraction(hasRecordAudioPermission: Boolean) {
        when (assistantController.state.value.preferredVoiceMode) {
            com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode.HoldToTalk ->
                startPushToTalk(hasRecordAudioPermission)
            com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode.StreamingConversation ->
                startStreamingConversation(hasRecordAudioPermission)
        }
    }

    fun startStreamingConversation(hasRecordAudioPermission: Boolean) {
        viewModelScope.launch {
            ensureEnabled()
            assistantController.startStreamingConversation(
                hasRecordAudioPermission = hasRecordAudioPermission,
                source = com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource.StreamingButton,
            )
        }
    }

    fun stopStreamingConversation() {
        viewModelScope.launch {
            assistantController.stopStreamingConversation("assistant_entry_button")
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

private fun AssistantState.toAuroraTarget(): AuroraTarget {
    return when {
        !assistantEnabled || phase == AssistantPhase.Disabled -> AuroraTarget(
            globalAlpha = 0.62f,
            fluidScale = 0.82f,
            animationSpeedScale = 0.12f,
            colorBlendRatio = 0f,
            colorA = ColorSlateGray,
            colorB = ColorSlateGray.copy(alpha = 0.45f),
            motion = AuroraMotion.Disabled,
        )
        phase == AssistantPhase.Error || audio == AssistantAudioStatus.Error -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 0.92f,
            animationSpeedScale = 0.46f,
            colorBlendRatio = 1f,
            colorA = ColorRoseRed,
            colorB = ColorSlateGray,
            motion = AuroraMotion.Error,
        )
        phase == AssistantPhase.Listening || audio == AssistantAudioStatus.Recording -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.10f,
            animationSpeedScale = 2.05f,
            colorBlendRatio = 1f,
            colorA = ColorPeach,
            colorB = ColorMistyBlue,
            motion = AuroraMotion.Listening,
        )
        phase == AssistantPhase.Speaking || audio == AssistantAudioStatus.Playing -> AuroraTarget(
            globalAlpha = 1.0f,
            fluidScale = 1.0f,
            animationSpeedScale = 1.18f,
            colorBlendRatio = 1f,
            colorA = ColorPeach,
            colorB = ColorLilac,
            colorC = ColorSunsetAmber,
            motion = AuroraMotion.Speaking,
        )
        phase == AssistantPhase.Thinking || phase == AssistantPhase.UploadingAudio -> AuroraTarget(
            globalAlpha = 0.98f,
            fluidScale = 1.04f,
            animationSpeedScale = 1.75f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorLilac,
            motion = AuroraMotion.Thinking,
        )
        phase == AssistantPhase.Activating ||
            phase == AssistantPhase.Connecting ||
            phase == AssistantPhase.Reconnecting ||
            connection == AssistantConnectionStatus.Connecting ||
            activation == AssistantActivationStatus.Activating -> AuroraTarget(
            globalAlpha = 0.94f,
            fluidScale = 0.98f,
            animationSpeedScale = 1.02f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorSunsetAmber.copy(alpha = 0.82f),
            motion = AuroraMotion.Connecting,
        )
        phase == AssistantPhase.Connected || connection == AssistantConnectionStatus.Connected -> AuroraTarget(
            globalAlpha = 0.96f,
            fluidScale = 0.98f,
            animationSpeedScale = 0.50f,
            colorBlendRatio = 1f,
            colorA = ColorMistyBlue,
            colorB = ColorLilac,
            motion = AuroraMotion.Connected,
        )
        else -> AuroraTarget(
            globalAlpha = 0.78f,
            fluidScale = 0.88f,
            animationSpeedScale = 0.18f,
            colorBlendRatio = 0.35f,
            colorA = ColorMistyBlue.copy(alpha = 0.72f),
            colorB = ColorSlateGray.copy(alpha = 0.55f),
            motion = AuroraMotion.Disabled,
        )
    }
}

private fun ToolCallUiStatus.bannerAccent(): Color = when (this) {
    ToolCallUiStatus.Idle -> ColorSlateGray
    ToolCallUiStatus.Running -> ColorMistyBlue
    ToolCallUiStatus.Success -> Color(0xFF22C55E)
    ToolCallUiStatus.RequiresConfirmation -> ColorSunsetAmber
    ToolCallUiStatus.PartialSuccess -> ColorLilac
    ToolCallUiStatus.Failed -> ColorRoseRed
    ToolCallUiStatus.Blocked -> ColorRoseRed
    ToolCallUiStatus.NotImplemented -> ColorSlateGray
}

private fun ToolCallUiState.detailLine(): String {
    val name = toolName.orEmpty()
    val base = detail.orEmpty()
    return listOf(name, base)
        .filter { it.isNotBlank() }
        .joinToString("  ")
}

private fun DrawScope.drawFeatheredAuroraBlob(
    color: Color,
    center: Offset,
    radius: Float,
    globalAlpha: Float,
) {
    val centerAlpha = auroraCenterAlpha(color) * globalAlpha
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = centerAlpha.coerceIn(0f, 1f)),
                color.copy(alpha = (centerAlpha * 0.48f).coerceIn(0f, 1f)),
                Color.Transparent,
            ),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
        blendMode = BlendMode.SrcOver,
    )
}

private fun auroraCenterAlpha(color: Color): Float {
    return when {
        color.sameRgb(ColorMistyBlue) -> 0.85f
        color.sameRgb(ColorLilac) -> 0.80f
        color.sameRgb(ColorPeach) -> 0.85f
        color.sameRgb(ColorSlateGray) -> min(color.alpha, 0.75f)
        color.sameRgb(ColorSunsetAmber) -> 0.80f
        color.sameRgb(ColorRoseRed) -> 0.90f
        else -> color.alpha.coerceIn(0.80f, 0.90f)
    }
}

private fun Color.sameRgb(other: Color): Boolean {
    return abs(red - other.red) < 0.002f &&
        abs(green - other.green) < 0.002f &&
        abs(blue - other.blue) < 0.002f
}

private fun blendAuroraColor(
    from: Color,
    to: Color,
    ratio: Float,
): Color {
    val t = ratio.coerceIn(0f, 1f)
    return Color(
        red = from.red + (to.red - from.red) * t,
        green = from.green + (to.green - from.green) * t,
        blue = from.blue + (to.blue - from.blue) * t,
        alpha = from.alpha + (to.alpha - from.alpha) * t,
    )
}

private enum class AuroraMotion {
    Disabled,
    Connecting,
    Connected,
    Listening,
    Thinking,
    Speaking,
    Error,
}

private data class AuroraTarget(
    val globalAlpha: Float,
    val fluidScale: Float,
    val animationSpeedScale: Float,
    val colorBlendRatio: Float,
    val colorA: Color,
    val colorB: Color,
    val colorC: Color? = null,
    val motion: AuroraMotion,
)

private val ColorMistyBlue = Color(0xFF3A86FF)
private val ColorLilac = Color(0xFF8338EC)
private val ColorPeach = Color(0xFFFF006E)
private val ColorSlateGray = Color(0xFF475569)
private val ColorSunsetAmber = Color(0xFFFF9F1C)
private val ColorRoseRed = Color(0xFFE63946)
private const val BASE_CYCLE_SECONDS = 6f
private const val THINKING_ORBIT_SECONDS = 3.5f
private const val TWO_PI = (2.0 * PI).toFloat()
