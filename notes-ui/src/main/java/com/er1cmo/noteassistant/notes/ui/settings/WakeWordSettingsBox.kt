package com.er1cmo.noteassistant.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordState

@Composable
internal fun WakeWordSettingsBox(
    state: WakeWordState,
    onEnabledChange: (Boolean) -> Unit,
    onPresetChange: (WakeWordPreset) -> Unit,
    onSensitivityChange: (WakeWordSensitivity) -> Unit,
    onCooldownChange: (Long) -> Unit,
    customViewModel: CustomWakeWordViewModel = hiltViewModel(),
) {
    val customState by customViewModel.state.collectAsState()
    var advancedOpen by rememberSaveable { mutableStateOf(false) }
    var customEditorOpen by rememberSaveable { mutableStateOf(state.phraseType == WakeWordPhraseType.Custom) }

    SettingsCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "唤醒词",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF202632),
            )
            Switch(checked = state.enabled, onCheckedChange = onEnabledChange)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { advancedOpen = !advancedOpen },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (advancedOpen) "收起高级选项" else "高级选项",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF5268D8),
            )
            Text(
                text = if (advancedOpen) "−" else "+",
                color = Color(0xFF5268D8),
                style = MaterialTheme.typography.titleMedium,
            )
        }

        if (advancedOpen) {
            Text(
                text = "当前：${state.activePhrase}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF343C49),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WakeWordPreset.values().forEach { preset ->
                    ChoiceButton(
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

            ChoiceButton(
                text = "自定义唤醒词",
                selected = state.phraseType == WakeWordPhraseType.Custom,
                modifier = Modifier.fillMaxWidth(),
                onClick = { customEditorOpen = !customEditorOpen },
            )

            if (customEditorOpen) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFF5F7FC), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = customState.inputText,
                        onValueChange = customViewModel::setInputText,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("2～6 个汉字") },
                        singleLine = true,
                    )

                    customState.candidates.forEachIndexed { index, candidate ->
                        ChoiceButton(
                            text = "${index + 1}. ${candidate.pronunciationLabel}",
                            selected = customState.selectedCandidateId == candidate.id,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { customViewModel.selectCandidate(candidate.id) },
                        )
                    }

                    if (customState.statusText.isNotBlank()) {
                        Text(
                            text = customState.statusText,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (customState.error) Color(0xFFB42318) else Color(0xFF4F5968),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = !customState.isChecking && !customState.isTesting && !customState.isSaving,
                            onClick = customViewModel::checkAvailability,
                        ) { Text(if (customState.isChecking) "检查中" else "检查") }
                        OutlinedButton(
                            modifier = Modifier.weight(1f),
                            enabled = customState.canTest,
                            onClick = customViewModel::runLocalTest,
                        ) { Text(if (customState.isTesting) "测试中" else "测试") }
                        Button(
                            modifier = Modifier.weight(1f),
                            enabled = customState.canSave,
                            onClick = customViewModel::save,
                        ) { Text(if (customState.isSaving) "保存中" else "保存") }
                    }
                }
            }

            Text(
                text = "灵敏度",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF545D6D),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WakeWordSensitivity.values().forEach { sensitivity ->
                    ChoiceButton(
                        text = sensitivity.label,
                        selected = state.sensitivity == sensitivity,
                        modifier = Modifier.weight(1f),
                        onClick = { onSensitivityChange(sensitivity) },
                    )
                }
            }

            Text(
                text = "重复唤醒间隔",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF545D6D),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf(1_000L, 1_500L, 2_500L).forEach { cooldown ->
                    ChoiceButton(
                        text = when (cooldown) {
                            1_000L -> "1 秒"
                            1_500L -> "1.5 秒"
                            else -> "2.5 秒"
                        },
                        selected = state.cooldownMs == cooldown,
                        modifier = Modifier.weight(1f),
                        onClick = { onCooldownChange(cooldown) },
                    )
                }
            }

            state.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB42318),
                )
            }
        }
    }
}

@Composable
private fun ChoiceButton(
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
