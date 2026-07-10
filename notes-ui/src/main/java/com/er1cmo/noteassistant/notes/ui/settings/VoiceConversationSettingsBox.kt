package com.er1cmo.noteassistant.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.assistant.runtime.state.AssistantState
import com.er1cmo.noteassistant.assistant.runtime.state.VoiceInteractionMode

@Composable
internal fun VoiceConversationSettingsBox(
    state: AssistantState,
    onModeChange: (VoiceInteractionMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "语音对话模式",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF222832),
        )
        Text(
            text = "Phase5-02：按住说话保持单轮；流式对话单点启停，VAD 自动提交并进入下一轮。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF697386),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ModeButton(
                text = "按住说话",
                selected = state.preferredVoiceMode == VoiceInteractionMode.HoldToTalk,
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(VoiceInteractionMode.HoldToTalk) },
            )
            ModeButton(
                text = "流式对话",
                selected = state.preferredVoiceMode == VoiceInteractionMode.StreamingConversation,
                modifier = Modifier.weight(1f),
                onClick = { onModeChange(VoiceInteractionMode.StreamingConversation) },
            )
        }

        Text(
            text = "当前=${state.preferredVoiceMode.label} · session=${state.streamingConversationState.storageValue} · mic=${state.microphoneOwner.name}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
        Text(
            text = "VAD=${state.vadState.storageValue} · ${state.vadStatusText}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
        Text(
            text = "本阶段流式入口仅由按钮启动；唤醒词命中仍不会自动连接助手。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF697386),
        )
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) { Text(text) }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(text) }
    }
}
