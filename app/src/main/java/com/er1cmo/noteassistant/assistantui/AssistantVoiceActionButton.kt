package com.er1cmo.noteassistant.assistantui

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantAudioStatus
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode

@Composable
internal fun AssistantVoiceActionButton(
    state: AssistantState,
    onStartHoldToTalk: () -> Unit,
    onStopHoldToTalk: () -> Unit,
    onStartStreaming: () -> Unit,
    onStopStreaming: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val mode = state.preferredVoiceMode
    val streamingActive = state.streamingSessionActive
    val recording = state.audio == AssistantAudioStatus.Recording

    val label = when (mode) {
        VoiceInteractionMode.HoldToTalk -> if (recording) "松开发送" else "按住说话"
        VoiceInteractionMode.StreamingConversation -> if (streamingActive) "结束对话" else "开始对话"
    }

    val baseModifier = modifier
        .fillMaxWidth()
        .height(48.dp)

    val gestureModifier = when (mode) {
        VoiceInteractionMode.HoldToTalk -> baseModifier.pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    onStartHoldToTalk()
                    try {
                        tryAwaitRelease()
                    } finally {
                        onStopHoldToTalk()
                    }
                },
            )
        }
        VoiceInteractionMode.StreamingConversation -> baseModifier.pointerInput(streamingActive) {
            detectTapGestures(
                onTap = {
                    if (streamingActive) onStopStreaming() else onStartStreaming()
                },
            )
        }
    }

    Surface(
        modifier = gestureModifier,
        shape = RoundedCornerShape(16.dp),
        color = if (recording || streamingActive) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.tertiaryContainer
        },
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (recording || streamingActive) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onTertiaryContainer
                },
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
