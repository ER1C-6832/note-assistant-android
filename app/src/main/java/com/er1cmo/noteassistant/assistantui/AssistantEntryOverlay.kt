package com.er1cmo.noteassistant.assistantui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.assistant.runtime.controller.AssistantController
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantActivationStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantConnectionStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantEntrySource
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantPhase
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallEventStore
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiState
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlinx.coroutines.delay
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

    fun hasRecordAudioPermission(): Boolean = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.RECORD_AUDIO,
    ) == PackageManager.PERMISSION_GRANTED

    val microphoneLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && uiState.state.preferredVoiceMode == VoiceInteractionMode.StreamingConversation) {
            viewModel.onPrimaryTap(hasRecordAudioPermission = true)
        }
    }

    LaunchedEffect(
        uiState.toolCall.visible,
        uiState.toolCall.updatedAtMillis,
        uiState.toolCall.status,
    ) {
        if (uiState.toolCall.visible && uiState.toolCall.status.shouldAutoDismiss()) {
            delay(TOOL_FEEDBACK_AUTO_DISMISS_MS)
            viewModel.dismissToolCall()
        }
    }

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

        if (uiState.showActivationCode) {
            ActivationCodeCard(uiState = uiState)
        }

        if (uiState.textPanelEnabled) {
            ConversationTextCard(
                uiState = uiState,
                onInputChange = viewModel::setInputText,
                onSendClick = viewModel::sendCurrentText,
            )
        }

        ProductAssistantButton(
            uiState = uiState,
            onTap = {
                val needsMicrophone = uiState.state.activation == AssistantActivationStatus.Activated &&
                    uiState.state.isConnected &&
                    !uiState.state.streamingSessionActive
                if (needsMicrophone && !hasRecordAudioPermission()) {
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                } else {
                    viewModel.onPrimaryTap(hasRecordAudioPermission = hasRecordAudioPermission())
                }
            },
            onHoldStart = {
                if (hasRecordAudioPermission()) {
                    viewModel.startHoldToTalk(hasRecordAudioPermission = true)
                } else {
                    microphoneLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            onHoldEnd = viewModel::stopHoldToTalk,
        )
    }
}

@Composable
private fun ActivationCodeCard(uiState: AssistantEntryUiState) {
    Surface(
        modifier = Modifier.widthIn(max = 326.dp),
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF20283A).copy(alpha = 0.96f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color(0xFFFFC857).copy(alpha = 0.6f)),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "设备激活",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.72f),
            )
            Text(
                text = uiState.state.activationCode.orEmpty(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD978),
                letterSpacing = 2.sp,
            )
            Text(
                text = "完成激活后，再点小智继续",
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.68f),
            )
        }
    }
}

@Composable
private fun ConversationTextCard(
    uiState: AssistantEntryUiState,
    onInputChange: (String) -> Unit,
    onSendClick: () -> Unit,
) {
    Surface(
        modifier = Modifier.widthIn(max = 332.dp),
        shape = RoundedCornerShape(24.dp),
        color = Color(0xFF111827).copy(alpha = 0.90f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val userText = uiState.state.lastUserText.orEmpty().trim()
            val assistantText = uiState.state.lastAssistantText.orEmpty().trim()
            if (userText.isBlank() && assistantText.isBlank()) {
                Text(
                    text = "对话文字会显示在这里",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.46f),
                )
            } else {
                if (userText.isNotBlank()) {
                    Text(
                        text = userText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFD8E3FF),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (assistantText.isNotBlank()) {
                    Text(
                        text = assistantText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.88f),
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("输入消息") },
                )
                Button(
                    onClick = onSendClick,
                    enabled = uiState.inputText.isNotBlank(),
                ) {
                    Text("发送")
                }
            }
        }
    }
}

@Composable
private fun ProductAssistantButton(
    uiState: AssistantEntryUiState,
    onTap: () -> Unit,
    onHoldStart: () -> Unit,
    onHoldEnd: () -> Unit,
) {
    val target = remember(uiState.state) { uiState.state.toAuroraTarget() }
    val transitionSpec = tween<Float>(durationMillis = 650, easing = FastOutSlowInEasing)
    val colorTransitionSpec = tween<Color>(durationMillis = 650, easing = FastOutSlowInEasing)
    val globalAlpha by animateFloatAsState(target.globalAlpha, transitionSpec, label = "assistant_alpha")
    val fluidScale by animateFloatAsState(target.fluidScale, transitionSpec, label = "assistant_scale")
    val speed by animateFloatAsState(target.speed, transitionSpec, label = "assistant_speed")
    val colorA by animateColorAsState(target.colorA, colorTransitionSpec, label = "assistant_color_a")
    val colorB by animateColorAsState(target.colorB, colorTransitionSpec, label = "assistant_color_b")
    val elapsedSeconds by produceState(initialValue = 0f, target.motion) {
        val startNanos = withFrameNanos { it }
        while (true) {
            withFrameNanos { frameNanos ->
                value = ((frameNanos - startNanos) / 1_000_000_000f).coerceAtLeast(0f)
            }
        }
    }

    val readyForHold = uiState.state.preferredVoiceMode == VoiceInteractionMode.HoldToTalk &&
        uiState.state.activation == AssistantActivationStatus.Activated &&
        uiState.state.isConnected &&
        !uiState.state.streamingSessionActive &&
        uiState.state.phase in setOf(AssistantPhase.Connected, AssistantPhase.Idle)

    Box(
        modifier = Modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.10f), CircleShape)
            .border(1.dp, target.borderColor, CircleShape)
            .pointerInput(readyForHold, uiState.state.streamingSessionActive, uiState.state.phase) {
                detectTapGestures(
                    onPress = {
                        if (readyForHold) {
                            onHoldStart()
                            tryAwaitRelease()
                            onHoldEnd()
                        } else if (tryAwaitRelease()) {
                            onTap()
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(76.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val radius = min(size.width, size.height) * 0.29f * fluidScale
            val phase = elapsedSeconds * TWO_PI * speed
            val offsetA = Offset(cos(phase) * 9.dp.toPx(), sin(phase * 0.8f) * 8.dp.toPx())
            val offsetB = Offset(sin(phase * 0.9f + 1.2f) * 10.dp.toPx(), cos(phase) * 9.dp.toPx())
            drawAuroraBlob(colorA, center + offsetA, radius * 1.08f, globalAlpha)
            drawAuroraBlob(colorB, center + offsetB, radius, globalAlpha)
        }
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.20f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "小智",
                    color = Color.White.copy(alpha = 0.88f),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = uiState.compactStatusLabel,
                    color = Color.White.copy(alpha = 0.66f),
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
    val density = LocalDensity.current
    val dismissThresholdPx = with(density) { 96.dp.toPx() }
    var dragOffsetX by remember(toolCall.updatedAtMillis) { mutableFloatStateOf(0f) }
    Surface(
        modifier = Modifier
            .width(306.dp)
            .graphicsLayer {
                translationX = dragOffsetX
                alpha = (1f - abs(dragOffsetX) / (dismissThresholdPx * 2f)).coerceIn(0.35f, 1f)
            }
            .pointerInput(toolCall.updatedAtMillis) {
                detectHorizontalDragGestures(
                    onDragCancel = { dragOffsetX = 0f },
                    onDragEnd = {
                        if (abs(dragOffsetX) >= dismissThresholdPx) onDismiss() else dragOffsetX = 0f
                    },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount
                    },
                )
            },
        shape = RoundedCornerShape(22.dp),
        color = Color(0xFF111827).copy(alpha = 0.90f),
        shadowElevation = 10.dp,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.46f)),
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
                    color = Color.White.copy(alpha = 0.90f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                toolCall.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.58f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    }
}

data class AssistantEntryUiState(
    val state: AssistantState,
    val toolCall: ToolCallUiState,
    val textPanelEnabled: Boolean,
    val inputText: String,
) {
    val showActivationCode: Boolean = state.activation == AssistantActivationStatus.Required &&
        !state.activationCode.isNullOrBlank()

    val compactStatusLabel: String = when {
        state.activation == AssistantActivationStatus.Required -> "激活"
        state.activation == AssistantActivationStatus.Activating -> "激活中"
        state.streamingSessionActive && state.phase == AssistantPhase.Listening -> "聆听"
        state.streamingSessionActive && state.phase == AssistantPhase.Speaking -> "回复"
        state.streamingSessionActive && state.phase == AssistantPhase.Thinking -> "思考"
        state.phase == AssistantPhase.Listening || state.audio == AssistantAudioStatus.Recording -> "聆听"
        state.phase == AssistantPhase.Speaking || state.audio == AssistantAudioStatus.Playing -> "回复"
        state.phase == AssistantPhase.Thinking || state.phase == AssistantPhase.UploadingAudio -> "思考"
        state.phase == AssistantPhase.Connecting ||
            state.phase == AssistantPhase.Reconnecting ||
            state.connection == AssistantConnectionStatus.Connecting -> "连接"
        state.isConnected -> "在线"
        state.phase == AssistantPhase.Error -> "重试"
        else -> "待命"
    }
}

@HiltViewModel
class AssistantEntryViewModel @Inject constructor(
    private val assistantController: AssistantController,
    private val toolCallEventStore: ToolCallEventStore,
    settingsRepository: SettingsRepository,
) : ViewModel() {
    private val inputText = MutableStateFlow("")
    private val primaryActionRunning = AtomicBoolean(false)

    val uiState: StateFlow<AssistantEntryUiState> = combine(
        assistantController.state,
        toolCallEventStore.state,
        settingsRepository.assistantTextPanelEnabled,
        inputText,
    ) { state, toolCall, textPanelEnabled, input ->
        AssistantEntryUiState(
            state = state,
            toolCall = toolCall,
            textPanelEnabled = textPanelEnabled,
            inputText = input,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AssistantEntryUiState(
            state = assistantController.state.value,
            toolCall = toolCallEventStore.state.value,
            textPanelEnabled = false,
            inputText = "",
        ),
    )

    fun dismissToolCall() {
        toolCallEventStore.clear()
    }

    fun setInputText(value: String) {
        inputText.value = value
    }

    fun onPrimaryTap(hasRecordAudioPermission: Boolean) {
        if (!primaryActionRunning.compareAndSet(false, true)) return
        viewModelScope.launch {
            try {
                ensureEnabled()
                ensureRealRuntime()

                if (assistantController.state.value.activation != AssistantActivationStatus.Activated) {
                    assistantController.runRealActivation()
                    if (assistantController.state.value.activation != AssistantActivationStatus.Activated) return@launch
                }

                if (!assistantController.state.value.isConnected) {
                    if (
                        assistantController.state.value.phase == AssistantPhase.Error ||
                        assistantController.state.value.reconnectAttempt > 0
                    ) {
                        assistantController.reconnect()
                    } else {
                        assistantController.connect()
                    }
                    return@launch
                }

                if (assistantController.state.value.streamingSessionActive) {
                    assistantController.stopStreamingConversation("assistant_primary_button")
                    return@launch
                }

                if (
                    assistantController.state.value.preferredVoiceMode == VoiceInteractionMode.StreamingConversation &&
                    hasRecordAudioPermission
                ) {
                    assistantController.startStreamingConversation(
                        hasRecordAudioPermission = true,
                        source = AssistantEntrySource.StreamingButton,
                    )
                }
            } finally {
                primaryActionRunning.set(false)
            }
        }
    }

    fun startHoldToTalk(hasRecordAudioPermission: Boolean) {
        if (!hasRecordAudioPermission) return
        viewModelScope.launch {
            ensureEnabled()
            ensureRealRuntime()
            if (
                assistantController.state.value.activation == AssistantActivationStatus.Activated &&
                assistantController.state.value.isConnected
            ) {
                assistantController.startPushToTalk(hasRecordAudioPermission = true)
            }
        }
    }

    fun stopHoldToTalk() {
        viewModelScope.launch { assistantController.stopPushToTalk() }
    }

    fun sendCurrentText() {
        val text = inputText.value.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            ensureEnabled()
            ensureRealRuntime()
            if (assistantController.state.value.activation != AssistantActivationStatus.Activated) {
                assistantController.runRealActivation()
                if (assistantController.state.value.activation != AssistantActivationStatus.Activated) return@launch
            }
            if (!assistantController.state.value.isConnected) {
                assistantController.connect()
            }
            if (assistantController.state.value.isConnected) {
                assistantController.sendText(text)
                inputText.value = ""
            }
        }
    }

    private suspend fun ensureEnabled() {
        if (!assistantController.state.value.assistantEnabled) {
            assistantController.enableAssistant()
        }
    }

    private suspend fun ensureRealRuntime() {
        if (!assistantController.state.value.isRealRuntime) {
            assistantController.useRealRuntime()
        }
    }
}

private fun AssistantState.toAuroraTarget(): AuroraTarget = when {
    activation == AssistantActivationStatus.Required -> AuroraTarget(
        globalAlpha = 1f,
        fluidScale = 0.96f,
        speed = 0.45f,
        colorA = ColorAmber,
        colorB = ColorLilac,
        borderColor = ColorAmber.copy(alpha = 0.68f),
        motion = AuroraMotion.Activating,
    )
    phase == AssistantPhase.Error || audio == AssistantAudioStatus.Error -> AuroraTarget(
        globalAlpha = 1f,
        fluidScale = 0.92f,
        speed = 0.55f,
        colorA = ColorRose,
        colorB = ColorSlate,
        borderColor = ColorRose.copy(alpha = 0.68f),
        motion = AuroraMotion.Error,
    )
    phase == AssistantPhase.Listening || audio == AssistantAudioStatus.Recording -> AuroraTarget(
        globalAlpha = 1f,
        fluidScale = 1.12f,
        speed = 1.10f,
        colorA = ColorPeach,
        colorB = ColorBlue,
        borderColor = ColorPeach.copy(alpha = 0.62f),
        motion = AuroraMotion.Listening,
    )
    phase == AssistantPhase.Speaking || audio == AssistantAudioStatus.Playing -> AuroraTarget(
        globalAlpha = 1f,
        fluidScale = 1.05f,
        speed = 0.85f,
        colorA = ColorPeach,
        colorB = ColorLilac,
        borderColor = ColorAmber.copy(alpha = 0.62f),
        motion = AuroraMotion.Speaking,
    )
    phase == AssistantPhase.Thinking || phase == AssistantPhase.UploadingAudio -> AuroraTarget(
        globalAlpha = 0.98f,
        fluidScale = 1.05f,
        speed = 0.95f,
        colorA = ColorBlue,
        colorB = ColorLilac,
        borderColor = ColorLilac.copy(alpha = 0.62f),
        motion = AuroraMotion.Thinking,
    )
    phase == AssistantPhase.Activating ||
        phase == AssistantPhase.Connecting ||
        phase == AssistantPhase.Reconnecting ||
        connection == AssistantConnectionStatus.Connecting -> AuroraTarget(
        globalAlpha = 0.95f,
        fluidScale = 0.98f,
        speed = 0.72f,
        colorA = ColorBlue,
        colorB = ColorAmber,
        borderColor = ColorBlue.copy(alpha = 0.58f),
        motion = AuroraMotion.Activating,
    )
    isConnected -> AuroraTarget(
        globalAlpha = 0.98f,
        fluidScale = 1f,
        speed = 0.32f,
        colorA = ColorBlue,
        colorB = ColorLilac,
        borderColor = ColorBlue.copy(alpha = 0.48f),
        motion = AuroraMotion.Online,
    )
    else -> AuroraTarget(
        globalAlpha = 0.78f,
        fluidScale = 0.88f,
        speed = 0.18f,
        colorA = ColorBlue.copy(alpha = 0.72f),
        colorB = ColorSlate.copy(alpha = 0.60f),
        borderColor = Color.White.copy(alpha = 0.26f),
        motion = AuroraMotion.Idle,
    )
}

private fun DrawScope.drawAuroraBlob(
    color: Color,
    center: Offset,
    radius: Float,
    globalAlpha: Float,
) {
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                color.copy(alpha = (0.86f * globalAlpha).coerceIn(0f, 1f)),
                color.copy(alpha = (0.42f * globalAlpha).coerceIn(0f, 1f)),
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

private fun ToolCallUiStatus.shouldAutoDismiss(): Boolean = when (this) {
    ToolCallUiStatus.Success,
    ToolCallUiStatus.Failed,
    ToolCallUiStatus.PartialSuccess,
    ToolCallUiStatus.Blocked,
    ToolCallUiStatus.NotImplemented -> true
    ToolCallUiStatus.Idle,
    ToolCallUiStatus.Running,
    ToolCallUiStatus.RequiresConfirmation -> false
}

private fun ToolCallUiStatus.bannerAccent(): Color = when (this) {
    ToolCallUiStatus.Idle -> ColorSlate
    ToolCallUiStatus.Running -> ColorBlue
    ToolCallUiStatus.Success -> Color(0xFF22C55E)
    ToolCallUiStatus.RequiresConfirmation -> ColorAmber
    ToolCallUiStatus.PartialSuccess -> ColorLilac
    ToolCallUiStatus.Failed,
    ToolCallUiStatus.Blocked -> ColorRose
    ToolCallUiStatus.NotImplemented -> ColorSlate
}

private enum class AuroraMotion { Idle, Activating, Online, Listening, Thinking, Speaking, Error }

private data class AuroraTarget(
    val globalAlpha: Float,
    val fluidScale: Float,
    val speed: Float,
    val colorA: Color,
    val colorB: Color,
    val borderColor: Color,
    val motion: AuroraMotion,
)

private const val TOOL_FEEDBACK_AUTO_DISMISS_MS = 3_000L
private const val TWO_PI = (PI * 2.0).toFloat()
private val ColorBlue = Color(0xFF3A86FF)
private val ColorLilac = Color(0xFFA78BFA)
private val ColorPeach = Color(0xFFFFA07A)
private val ColorAmber = Color(0xFFFFC857)
private val ColorRose = Color(0xFFF45B69)
private val ColorSlate = Color(0xFF6B7280)
