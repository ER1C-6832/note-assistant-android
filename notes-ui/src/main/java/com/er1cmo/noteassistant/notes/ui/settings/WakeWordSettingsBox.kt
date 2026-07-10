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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordPreset
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordSensitivity
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceState
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordState

@Composable
internal fun WakeWordSettingsBox(
    state: WakeWordState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetChange: (WakeWordPreset) -> Unit,
    onSensitivityChange: (WakeWordSensitivity) -> Unit,
    onCooldownChange: (Long) -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onMarkFalseTrigger: () -> Unit,
    onResetStats: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "本地唤醒词",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF222832),
                )
                Text(
                    text = "Phase5-01：命中后仅显示状态，不连接服务器、不启动助手录音。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF697386),
                )
            }
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
        }

        Text(
            text = "状态=${state.serviceState.name} · 麦克风=${state.microphoneOwner.name}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
        Text(
            text = state.lastStatus,
            style = MaterialTheme.typography.bodySmall,
            color = if (state.errorMessage == null) Color(0xFF344054) else Color(0xFFB42318),
        )
        Text(
            text = "当前唤醒词：${state.activePhrase}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
        )

        Text("预设", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WakeWordPreset.values().forEach { preset ->
                PresetChoiceButton(
                    text = preset.displayName,
                    selected = state.preset == preset,
                    modifier = Modifier.weight(1f),
                    onClick = { onPresetChange(preset) },
                )
            }
        }
        Text(
            text = state.preset.description,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF697386),
        )

        Text("灵敏度", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WakeWordSensitivity.values().forEach { sensitivity ->
                PresetChoiceButton(
                    text = sensitivity.label,
                    selected = state.sensitivity == sensitivity,
                    modifier = Modifier.weight(1f),
                    onClick = { onSensitivityChange(sensitivity) },
                )
            }
        }
        Text(
            text = state.sensitivity.description,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF697386),
        )

        Text("冷却时间", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            listOf(1_000L, 1_500L, 2_500L).forEach { cooldown ->
                PresetChoiceButton(
                    text = "${cooldown / 1000.0}s",
                    selected = state.cooldownMs == cooldown,
                    modifier = Modifier.weight(1f),
                    onClick = { onCooldownChange(cooldown) },
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = state.enabled && state.serviceState != WakeWordServiceState.Paused,
                onClick = onPause,
            ) { Text("暂停监听") }
            Button(
                modifier = Modifier.weight(1f),
                enabled = state.enabled && state.serviceState != WakeWordServiceState.Listening,
                onClick = onResume,
            ) { Text("恢复监听") }
        }

        Text(
            text = "最近命中：${state.lastDetectedKeyword} · latency=${state.lastDetectionLatencyMs ?: -1}ms",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
        Text(
            text = "命中 ${state.hitCount} · 冷却忽略 ${state.cooldownIgnoredCount} · 误触 ${state.falseTriggerCount}",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF344054),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                enabled = state.lastDetectedKeyword != "暂无",
                onClick = onMarkFalseTrigger,
            ) { Text("标记误触") }
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = onResetStats,
            ) { Text("清空统计") }
        }

        Text(
            text = "自定义唤醒词的数据模型已建立，本阶段 UI 只开放三个已验证预设。",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF697386),
        )
    }
}

@Composable
private fun PresetChoiceButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(modifier = modifier, onClick = onClick) { Text(text, maxLines = 1) }
    } else {
        OutlinedButton(modifier = modifier, onClick = onClick) { Text(text, maxLines = 1) }
    }
}
