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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordPhraseType
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
    customViewModel: CustomWakeWordViewModel = hiltViewModel(),
) {
    val customState by customViewModel.state.collectAsState()
    var customEditorOpen by rememberSaveable { mutableStateOf(state.phraseType == WakeWordPhraseType.Custom) }
    LaunchedEffect(state.phraseType) {
        if (state.phraseType == WakeWordPhraseType.Custom) customEditorOpen = true
    }

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
                    text = "预设与自定义词均在本机 sherpa-onnx 中检测，不上传唤醒音频。",
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

        Text("唤醒词", style = MaterialTheme.typography.labelLarge)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WakeWordPreset.values().forEach { preset ->
                PresetChoiceButton(
                    text = preset.displayName,
                    selected = state.phraseType == WakeWordPhraseType.Preset && state.preset == preset,
                    modifier = Modifier.weight(1f),
                    onClick = {
                        customEditorOpen = false
                        onPresetChange(preset)
                    },
                )
            }
        }
        PresetChoiceButton(
            text = "自定义",
            selected = state.phraseType == WakeWordPhraseType.Custom,
            modifier = Modifier.fillMaxWidth(),
            onClick = { customEditorOpen = true },
        )

        if (customEditorOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF7F8FC), RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("自定义唤醒词", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text(
                    "仅支持 2～6 个常用汉字。数字、标点、空格和中英文混合会被拒绝。校验失败不会覆盖当前配置。",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF697386),
                )
                OutlinedTextField(
                    value = customState.inputText,
                    onValueChange = customViewModel::setInputText,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("自定义文字") },
                    placeholder = { Text("例如：你好小泓") },
                    singleLine = true,
                )

                if (customState.candidates.isNotEmpty()) {
                    Text("读音候选", style = MaterialTheme.typography.labelLarge)
                    customState.candidates.forEachIndexed { index, candidate ->
                        PresetChoiceButton(
                            text = "${index + 1}. ${candidate.pronunciationLabel}",
                            selected = customState.selectedCandidateId == candidate.id,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { customViewModel.selectCandidate(candidate.id) },
                        )
                    }
                }

                Text(
                    text = customState.statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (customState.error) Color(0xFFB42318) else Color(0xFF344054),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = !customState.isChecking && !customState.isTesting && !customState.isSaving,
                        onClick = customViewModel::checkAvailability,
                    ) { Text(if (customState.isChecking) "检查中" else "检查可用性", maxLines = 1) }
                    OutlinedButton(
                        modifier = Modifier.weight(1f),
                        enabled = customState.canTest,
                        onClick = customViewModel::runLocalTest,
                    ) { Text(if (customState.isTesting) "测试中" else "本机测试", maxLines = 1) }
                    Button(
                        modifier = Modifier.weight(1f),
                        enabled = customState.canSave,
                        onClick = customViewModel::save,
                    ) { Text(if (customState.isSaving) "保存中" else "保存", maxLines = 1) }
                }
                if (customState.testPassed) {
                    Text("✓ 本机说话测试已通过", color = Color(0xFF067647), style = MaterialTheme.typography.bodySmall)
                }
            }
        } else {
            Text(
                text = state.preset.description,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF697386),
            )
        }

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
