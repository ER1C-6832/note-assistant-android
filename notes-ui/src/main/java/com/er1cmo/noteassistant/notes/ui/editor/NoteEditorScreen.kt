package com.er1cmo.noteassistant.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
fun NoteEditorRoute(onBackClick: () -> Unit) {
    Surface(color = Color(0xFFF3F6FB), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("新建便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("填写新便签内容", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                StatusPill(text = "编辑中", color = Color(0xFFFFB020))
            }
            InfoBox("●  输入标题、正文和标签后保存。")
            FieldBox(label = "标题", value = "屏幕校色记录")
            FieldBox(label = "正文", value = "记录 27 寸屏幕亮度、色温和边框间隙。")
            FieldBox(label = "标签", value = "屏幕、硬件、待办")
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("保存") }
            }
            StatusPill(text = "便签已保存")
        }
    }
}

@Composable
private fun FieldBox(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color(0xFF9AA3B2), style = MaterialTheme.typography.bodyMedium)
        Text(
            text = value,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(16.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun InfoBox(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(16.dp))
            .padding(14.dp),
        color = Color(0xFF4B5563),
    )
}
