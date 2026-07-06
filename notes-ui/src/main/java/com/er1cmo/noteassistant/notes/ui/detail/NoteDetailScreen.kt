package com.er1cmo.noteassistant.notes.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.ui.components.NoteColorPalette

@Composable
fun NoteDetailRoute(
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteDetailScreen(
        state = state,
        onBackClick = onBackClick,
        onEditClick = onEditClick,
    )
}

@Composable
fun NoteDetailScreen(
    state: NoteDetailState,
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "便签详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222832),
                    )
                    Text("查看内容，需要修改时再进入编辑", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A7280))
                }
                OutlinedButton(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
            }

            when {
                state.isLoading -> {
                    Text(
                        text = "正在读取便签……",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF6F7FB), RoundedCornerShape(24.dp))
                            .padding(18.dp),
                        color = Color(0xFF6B7280),
                    )
                }
                state.note == null -> {
                    Text(
                        text = "没有找到这条便签。",
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFF6F7FB), RoundedCornerShape(24.dp))
                            .padding(18.dp),
                        color = Color(0xFF6B7280),
                    )
                }
                else -> {
                    NoteDetailContent(note = state.note)
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick = { onEditClick(state.note.id) },
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("编辑便签")
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteDetailContent(note: Note) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NoteColorPalette.colorFor(note.color), RoundedCornerShape(26.dp))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title.ifBlank { "未命名便签" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF20242C),
            )
            if (note.pinned) {
                Text("置顶", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7C5C00))
            }
        }
        Text(
            text = when (note.type) {
                NoteType.Todo -> if (note.isDone) "待办 · 已完成" else "待办 · 未完成"
                NoteType.Normal -> "普通便签"
            },
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF6B7280),
        )
        Text(
            text = note.content.ifBlank { "暂无正文" },
            style = MaterialTheme.typography.bodyLarge,
            color = Color(0xFF404756),
        )
        if (note.tags.isNotEmpty()) {
            Text(
                text = note.tags.joinToString("  ") { "#${it.name}" },
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF5C6372),
            )
        }
    }
}
