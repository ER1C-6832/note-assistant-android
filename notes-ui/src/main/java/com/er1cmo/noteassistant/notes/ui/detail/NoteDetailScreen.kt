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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
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
    LaunchedEffect(state.closeAfterAction) {
        if (state.closeAfterAction) onBackClick()
    }
    NoteDetailScreen(
        state = state,
        onBackClick = onBackClick,
        onEditClick = onEditClick,
        onDoneClick = viewModel::toggleDone,
        onPinClick = viewModel::togglePinned,
        onDeleteClick = viewModel::softDelete,
        onRestoreClick = viewModel::restore,
    )
}

@Composable
fun NoteDetailScreen(
    state: NoteDetailState,
    onBackClick: () -> Unit,
    onEditClick: (Long) -> Unit,
    onDoneClick: (Boolean) -> Unit,
    onPinClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
                Spacer(Modifier.weight(1f))
                state.note?.let { note ->
                    if (!note.deleted) {
                        OutlinedButton(
                            onClick = onPinClick,
                            enabled = !state.isActing,
                            shape = RoundedCornerShape(16.dp),
                        ) { Text(if (note.pinned) "取消置顶" else "置顶") }
                    }
                }
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
                    NoteDetailActions(
                        note = state.note,
                        isActing = state.isActing,
                        onEditClick = onEditClick,
                        onDoneClick = onDoneClick,
                        onDeleteClick = onDeleteClick,
                        onRestoreClick = onRestoreClick,
                    )
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
                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
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
        if (note.deleted) {
            Text("这条便签已在最近删除中。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A6B2D))
        }
    }
}

@Composable
private fun NoteDetailActions(
    note: Note,
    isActing: Boolean,
    onEditClick: (Long) -> Unit,
    onDoneClick: (Boolean) -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (note.deleted) {
            Button(
                onClick = onRestoreClick,
                enabled = !isActing,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("恢复便签")
            }
        } else {
            if (note.type == NoteType.Todo) {
                OutlinedButton(
                    onClick = { onDoneClick(!note.isDone) },
                    enabled = !isActing,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (note.isDone) "取消完成" else "标记完成")
                }
            }
            Button(
                onClick = { onEditClick(note.id) },
                enabled = !isActing,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("编辑便签")
            }
            OutlinedButton(
                onClick = onDeleteClick,
                enabled = !isActing,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("删除到最近删除")
            }
        }
    }
}
