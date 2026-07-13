package com.er1cmo.noteassistant.notes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
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
    onBargeInEnabledChange: (Boolean) -> Unit,
) {
    SettingsCard {
        Text(
            text = "语音模式",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF202632),
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "允许插话",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF343C49),
            )
            Switch(
                checked = state.streamingBargeInEnabled,
                onCheckedChange = onBargeInEnabledChange,
            )
        }
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
