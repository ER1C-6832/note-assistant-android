package com.er1cmo.noteassistant.notes.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.ui.components.NoteColorPalette

@Composable
fun NoteEditorRoute(
    onBackClick: () -> Unit,
    viewModel: NoteEditorViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    LaunchedEffect(state.saved) {
        if (state.saved) onBackClick()
    }
    NoteEditorScreen(
        state = state,
        onTitleChange = viewModel::updateTitle,
        onContentChange = viewModel::updateContent,
        onTagTextChange = viewModel::updateTagText,
        onTypeChange = viewModel::updateType,
        onColorChange = viewModel::updateColor,
        onPinnedChange = viewModel::updatePinned,
        onSaveClick = viewModel::save,
        onBackClick = onBackClick,
    )
}

@Composable
fun NoteEditorScreen(
    state: NoteEditorState,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagTextChange: (String) -> Unit,
    onTypeChange: (NoteType) -> Unit,
    onColorChange: (String) -> Unit,
    onPinnedChange: (Boolean) -> Unit,
    onSaveClick: () -> Unit,
    onBackClick: () -> Unit,
) {
    Surface(color = Color(0xFFF8F3EA), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (state.noteId == null) "新建便签" else "编辑便签",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF222832),
                    )
                    Text("标题、正文、标签、类型、置顶和颜色", style = MaterialTheme.typography.bodySmall, color = Color(0xFF7A7280))
                }
                OutlinedButton(onClick = onBackClick, shape = RoundedCornerShape(16.dp)) { Text("返回") }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color.White.copy(alpha = 0.94f), RoundedCornerShape(26.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = state.title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标题") },
                    placeholder = { Text("给便签起个标题") },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.content,
                    onValueChange = onContentChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("正文") },
                    placeholder = { Text("写下想法、信息或待办详情") },
                    minLines = 7,
                )
                OutlinedTextField(
                    value = state.tagText,
                    onValueChange = onTagTextChange,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("标签") },
                    placeholder = { Text("例如：客户、硬件、灵感") },
                    supportingText = { Text("多个标签可用顿号、逗号或空格分隔。") },
                    singleLine = true,
                )

                Text("便签类型", style = MaterialTheme.typography.labelLarge, color = Color(0xFF6B5F4A))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.type == NoteType.Normal,
                        onClick = { onTypeChange(NoteType.Normal) },
                        label = { Text("普通便签") },
                    )
                    FilterChip(
                        selected = state.type == NoteType.Todo,
                        onClick = { onTypeChange(NoteType.Todo) },
                        label = { Text("待办便签") },
                    )
                }

                Text("列表位置", style = MaterialTheme.typography.labelLarge, color = Color(0xFF6B5F4A))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !state.pinned,
                        onClick = { onPinnedChange(false) },
                        label = { Text("不置顶") },
                    )
                    FilterChip(
                        selected = state.pinned,
                        onClick = { onPinnedChange(true) },
                        label = { Text("置顶") },
                    )
                }

                Text("颜色", style = MaterialTheme.typography.labelLarge, color = Color(0xFF6B5F4A))
                ColorGrid(selectedHex = state.color, onColorChange = onColorChange)
            }

            Button(
                onClick = onSaveClick,
                enabled = !state.isSaving && (state.title.isNotBlank() || state.content.isNotBlank()),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSaving) "保存中……" else "保存便签")
            }
        }
    }
}

@Composable
private fun ColorGrid(selectedHex: String, onColorChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NoteColorPalette.options.chunked(5).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { option ->
                    Surface(
                        onClick = { onColorChange(option.hex) },
                        shape = CircleShape,
                        color = option.color,
                        tonalElevation = if (selectedHex == option.hex) 4.dp else 0.dp,
                        shadowElevation = if (selectedHex == option.hex) 4.dp else 0.dp,
                    ) {
                        Text(
                            text = if (selectedHex == option.hex) "✓" else " ",
                            modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp),
                            color = Color(0xFF374151),
                        )
                    }
                }
            }
        }
    }
}
