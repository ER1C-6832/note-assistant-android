package com.er1cmo.noteassistant.notes.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.app.settings.WakeWordSettingsRepository
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordCompileResult
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordGrammarCompiler
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordPronunciationCandidate
import com.er1cmo.noteassistant.assistant.wakeword.WakeWordServiceController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CustomWakeWordUiState(
    val inputText: String = "",
    val candidates: List<WakeWordPronunciationCandidate> = emptyList(),
    val selectedCandidateId: String? = null,
    val verifiedCandidateId: String? = null,
    val isChecking: Boolean = false,
    val isTesting: Boolean = false,
    val isSaving: Boolean = false,
    val testPassed: Boolean = false,
    val statusText: String = "输入 2～6 个汉字后检查可用性。",
    val error: Boolean = false,
) {
    val selectedCandidate: WakeWordPronunciationCandidate?
        get() = candidates.firstOrNull { it.id == selectedCandidateId }

    val canTest: Boolean
        get() = selectedCandidateId != null && selectedCandidateId == verifiedCandidateId && !isChecking && !isTesting

    val canSave: Boolean
        get() = selectedCandidateId != null && selectedCandidateId == verifiedCandidateId && !isChecking && !isTesting && !isSaving
}

@HiltViewModel
class CustomWakeWordViewModel @Inject constructor(
    private val compiler: WakeWordGrammarCompiler,
    private val serviceController: WakeWordServiceController,
    private val settingsRepository: WakeWordSettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(CustomWakeWordUiState())
    val state: StateFlow<CustomWakeWordUiState> = mutableState.asStateFlow()

    init {
        viewModelScope.launch {
            val saved = settingsRepository.current()
            if (saved.customText.isBlank()) return@launch
            val result = withContext(Dispatchers.Default) { compiler.compile(saved.customText) }
            if (result is WakeWordCompileResult.Success) {
                val selected = result.candidates.firstOrNull { it.grammar == saved.customGrammar }
                    ?: result.candidates.firstOrNull()
                mutableState.value = CustomWakeWordUiState(
                    inputText = saved.customText,
                    candidates = result.candidates,
                    selectedCandidateId = selected?.id,
                    verifiedCandidateId = selected?.takeIf { it.grammar == saved.customGrammar }?.id,
                    statusText = if (selected?.grammar == saved.customGrammar) {
                        "已加载上次保存的自定义唤醒词。"
                    } else {
                        "已加载文本，请重新检查所选读音。"
                    },
                )
            } else {
                mutableState.update { it.copy(inputText = saved.customText) }
            }
        }
    }

    fun setInputText(value: String) {
        mutableState.value = CustomWakeWordUiState(
            inputText = value,
            statusText = "内容已改变，请重新检查可用性。",
        )
    }

    fun selectCandidate(candidateId: String) {
        val current = mutableState.value
        if (current.candidates.none { it.id == candidateId }) return
        mutableState.value = current.copy(
            selectedCandidateId = candidateId,
            verifiedCandidateId = current.verifiedCandidateId.takeIf { it == candidateId },
            testPassed = false,
            statusText = if (current.verifiedCandidateId == candidateId) {
                "已选择通过检查的读音。"
            } else {
                "已切换读音，请再次点击“检查可用性”。"
            },
            error = false,
        )
    }

    fun checkAvailability() {
        if (mutableState.value.isChecking) return
        viewModelScope.launch {
            val previous = mutableState.value
            mutableState.value = previous.copy(isChecking = true, statusText = "正在生成拼音并校验 tokens……", error = false)
            val compiled = withContext(Dispatchers.Default) { compiler.compile(previous.inputText) }
            if (compiled is WakeWordCompileResult.Failure) {
                mutableState.value = previous.copy(
                    candidates = emptyList(),
                    selectedCandidateId = null,
                    verifiedCandidateId = null,
                    testPassed = false,
                    isChecking = false,
                    statusText = compiled.message,
                    error = true,
                )
                return@launch
            }
            compiled as WakeWordCompileResult.Success
            val selected = compiled.candidates.firstOrNull { it.id == previous.selectedCandidateId }
                ?: compiled.candidates.first()
            mutableState.value = previous.copy(
                inputText = compiled.normalizedText,
                candidates = compiled.candidates,
                selectedCandidateId = selected.id,
                verifiedCandidateId = null,
                testPassed = false,
                isChecking = true,
                statusText = "正在创建 sherpa 测试 stream……",
                error = false,
            )
            val check = serviceController.verifyCustomPhrase(selected)
            mutableState.update { current ->
                current.copy(
                    isChecking = false,
                    verifiedCandidateId = selected.id.takeIf { check.success },
                    statusText = buildString {
                        append(check.message)
                        if (compiled.candidates.size > 1) append(" 找到 ${compiled.candidates.size} 个读音候选，请确认。")
                        if (compiled.candidateLimitReached) append(" 候选较多，仅展示前 16 个。")
                    },
                    error = !check.success,
                )
            }
        }
    }

    fun runLocalTest() {
        val candidate = mutableState.value.selectedCandidate ?: return
        if (!mutableState.value.canTest) return
        viewModelScope.launch {
            mutableState.update { it.copy(isTesting = true, testPassed = false, statusText = "请在 10 秒内清晰说出“${candidate.displayText}”……", error = false) }
            val result = serviceController.testCustomPhrase(candidate)
            mutableState.update {
                it.copy(
                    isTesting = false,
                    testPassed = result.success,
                    statusText = result.message + (result.latencyMs?.let { latency -> " · latency=${latency}ms" } ?: ""),
                    error = !result.success,
                )
            }
        }
    }

    fun save() {
        val candidate = mutableState.value.selectedCandidate ?: return
        if (!mutableState.value.canSave) return
        viewModelScope.launch {
            mutableState.update { it.copy(isSaving = true, statusText = "正在保存并更新前台服务……", error = false) }
            runCatching { serviceController.saveCustomPhrase(candidate) }
                .onSuccess {
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            statusText = "已保存“${candidate.displayText}”（${candidate.pronunciationLabel}），正式 KWS 已更新。",
                            error = false,
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(
                            isSaving = false,
                            statusText = "保存失败：${error.message ?: error::class.java.simpleName}；原唤醒词保持不变。",
                            error = true,
                        )
                    }
                }
        }
    }
}
