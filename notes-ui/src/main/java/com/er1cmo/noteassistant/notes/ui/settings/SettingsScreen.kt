package com.er1cmo.noteassistant.notes.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.notes.ui.components.StatusPill

@Composable
fun SettingsRoute(onBackClick: () -> Unit) {
    Surface(color = Color(0xFFF3F6FB), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小智便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("设置与调试", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                StatusPill(text = "已连接")
            }
            SettingBox("WebSocket 地址", "wss://example.invalid/xiaozhi")
            SettingBox("语音助手状态", "Phase 3 接入真实小智服务")
            SettingBox("搜索模式", "Room FTS + LIKE fallback")
            SettingBox("数据库", "本地 SQLite note_assistant.db")
            Button(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
        }
    }
}

@Composable
private fun SettingBox(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}
