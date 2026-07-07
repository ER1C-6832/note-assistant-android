package com.er1cmo.noteassistant.notes.ui.settings

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.er1cmo.noteassistant.app.settings.SettingsRepository
import com.er1cmo.noteassistant.notes.ui.components.StatusPill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@Composable
fun SettingsRoute(
    onBackClick: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    val background = colorFromHex(state.homeBackgroundColor)
    val dark = state.homeBackgroundColor.isDarkHex()
    val titleColor = if (dark) Color.White else Color(0xFF111827)
    val subColor = if (dark) Color(0xFFD0D5DD) else Color(0xFF697386)
    Surface(color = background, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小泓便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = titleColor)
                    Text("设置与调试", style = MaterialTheme.typography.bodySmall, color = subColor)
                }
                StatusPill(text = "已连接")
            }
            SettingBox("WebSocket 地址", "wss://example.invalid/xiaozhi")
            SettingBox("语音助手状态", "Phase 3 接入真实小智服务")
            SettingBox("搜索模式", "标题 / 正文 / 标签 / 拼音即时筛选")
            SettingBox("数据库", "本地 SQLite note_assistant.db")
            ColorSettingBox(
                label = "主界面背景",
                selectedHex = state.homeBackgroundColor,
                options = homeBackgroundOptions,
                onSelect = viewModel::setHomeBackgroundColor,
            )
            ColorSettingBox(
                label = "Tag 列表背景",
                selectedHex = state.tagDrawerBackgroundColor,
                options = tagDrawerBackgroundOptions,
                onSelect = viewModel::setTagDrawerBackgroundColor,
            )
            Button(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
        }
    }
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {
    val state = combine(
        settingsRepository.homeBackgroundColor,
        settingsRepository.tagDrawerBackgroundColor,
    ) { home, tagDrawer ->
        SettingsUiState(homeBackgroundColor = home, tagDrawerBackgroundColor = tagDrawer)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(),
    )

    fun setHomeBackgroundColor(hex: String) {
        viewModelScope.launch { settingsRepository.setHomeBackgroundColor(hex) }
    }

    fun setTagDrawerBackgroundColor(hex: String) {
        viewModelScope.launch { settingsRepository.setTagDrawerBackgroundColor(hex) }
    }
}

data class SettingsUiState(
    val homeBackgroundColor: String = SettingsRepository.DEFAULT_HOME_BACKGROUND,
    val tagDrawerBackgroundColor: String = SettingsRepository.DEFAULT_TAG_DRAWER_BACKGROUND,
)

private val homeBackgroundOptions = listOf(
    ColorOption("白色", "#FFFFFF"),
    ColorOption("米白", "#FFFDF7"),
    ColorOption("暖杏", "#F8F3EA"),
    ColorOption("浅蓝", "#F3F6FB"),
    ColorOption("深色", "#111827"),
    ColorOption("黑色", "#000000"),
)

private val tagDrawerBackgroundOptions = listOf(
    ColorOption("暖黄", "#FFF3D1"),
    ColorOption("米白", "#FFFDF7"),
    ColorOption("浅蓝", "#E7F0FF"),
    ColorOption("浅绿", "#E4F6EC"),
    ColorOption("深色", "#111827"),
    ColorOption("黑色", "#000000"),
)

private data class ColorOption(val name: String, val hex: String)

@Composable
private fun SettingBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color(0xFF111827))
    }
}

@Composable
private fun ColorSettingBox(
    label: String,
    selectedHex: String,
    options: List<ColorOption>,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.92f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            options.forEach { option ->
                Surface(
                    onClick = { onSelect(option.hex) },
                    shape = RoundedCornerShape(14.dp),
                    color = if (selectedHex == option.hex) Color(0xFFEDE0FF) else Color(0xFFF6F7FB),
                    border = androidx.compose.foundation.BorderStroke(1.dp, if (selectedHex == option.hex) Color.Transparent else Color(0xFFE5E7EB)),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = Modifier.size(14.dp).background(colorFromHex(option.hex), CircleShape))
                        Text(option.name, style = MaterialTheme.typography.labelMedium, color = Color(0xFF344054))
                    }
                }
            }
        }
    }
}

private fun colorFromHex(hex: String): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color.White)

private fun String.isDarkHex(): Boolean = runCatching {
    val color = AndroidColor.parseColor(this)
    val r = AndroidColor.red(color)
    val g = AndroidColor.green(color)
    val b = AndroidColor.blue(color)
    (0.299 * r + 0.587 * g + 0.114 * b) < 110.0
}.getOrDefault(false)
