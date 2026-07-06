package com.er1cmo.noteassistant.notes.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
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
    onColorClick: (Long) -> Unit,
    viewModel: NoteDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteDetailScreen(
        state = state,
        onBackClick = onBackClick,
        onTitleChange = viewModel::updateTitle,
        onContentChange = viewModel::updateContent,
        onTagTextChange = viewModel::updateTagText,
        onSaveClick = viewModel::saveTextFields,
        onTypeChange = viewModel::changeType,
        onToggleDone = viewModel::toggleDone,
        onTogglePinned = viewModel::togglePinned,
        onDeleteClick = viewModel::softDelete,
        onRestoreClick = viewModel::restore,
        onColorClick = onColorClick,
    )
}

@Composable
fun NoteDetailScreen(
    state: NoteDetailState,
    onBackClick: () -> Unit,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagTextChange: (String) -> Unit,
    onSaveClick: () -> Unit,
    onTypeChange: (NoteType) -> Unit,
    onToggleDone: () -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onColorClick: (Long) -> Unit,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    onClick = onBackClick,
                    shape = RoundedCornerShape(16.dp),
                    color = Color(0xFFF4F5F7),
                ) {
                    Text(
                        text = "返回",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                        color = Color(0xFF394052),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }

            when {
                state.isLoading -> StatusBox("正在读取便签……")
                state.note == null -> StatusBox(state.message ?: "没有找到这条便签。")
                else -> {
                    DetailEditorCard(
                        note = state.note,
                        title = state.titleInput,
                        content = state.contentInput,
                        tagText = state.tagTextInput,
                        onTitleChange = onTitleChange,
                        onContentChange = onContentChange,
                        onTagTextChange = onTagTextChange,
                    )
                    if (state.isDirty) {
                        Button(
                            onClick = onSaveClick,
                            enabled = !state.isSaving,
                            shape = RoundedCornerShape(18.dp),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.isSaving) "保存中……" else "保存内容")
                        }
                    }
                    ActionArea(
                        note = state.note,
                        onTypeChange = onTypeChange,
                        onToggleDone = onToggleDone,
                        onTogglePinned = onTogglePinned,
                        onDeleteClick = onDeleteClick,
                        onRestoreClick = onRestoreClick,
                        onColorClick = { onColorClick(state.note.id) },
                    )
                    state.message?.let { Text(it, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun DetailEditorCard(
    note: Note,
    title: String,
    content: String,
    tagText: String,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onTagTextChange: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(NoteColorPalette.colorFor(note.color), RoundedCornerShape(28.dp))
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            InlineTextField(
                value = title,
                onValueChange = onTitleChange,
                placeholder = "未命名便签",
                modifier = Modifier.weight(1f),
                textStyle = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF20242C),
                    textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                ),
                singleLine = false,
            )
            if (note.pinned) {
                Text("置顶", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7C5C00))
            }
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x1F000000)),
        )
        InlineTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = "暂无正文",
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF404756)),
            minHeight = 160,
        )
        InlineTagField(
            value = tagText,
            onValueChange = onTagTextChange,
        )
    }
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    singleLine: Boolean = false,
    minHeight: Int = 0,
) {
    Box(modifier = modifier.then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = singleLine,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text(placeholder, style = textStyle, color = Color(0x88404756))
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun InlineTagField(value: String, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C6372))
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF5C6372)),
            singleLine = true,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (value.isBlank()) {
                    Text("标签", style = MaterialTheme.typography.bodyMedium, color = Color(0x885C6372))
                }
                innerTextField()
            },
        )
    }
}

@Composable
private fun ActionArea(
    note: Note,
    onTypeChange: (NoteType) -> Unit,
    onToggleDone: () -> Unit,
    onTogglePinned: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onColorClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TypePill(
                text = "普通便签",
                selected = note.type == NoteType.Normal,
                modifier = Modifier.weight(1f),
                onClick = { onTypeChange(NoteType.Normal) },
            )
            TypePill(
                text = "待办便签",
                selected = note.type == NoteType.Todo,
                modifier = Modifier.weight(1f),
                onClick = { onTypeChange(NoteType.Todo) },
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (note.type == NoteType.Todo && !note.deleted) {
                ActionPill(
                    text = if (note.isDone) "取消完成" else "标记完成",
                    modifier = Modifier.weight(1f),
                    onClick = onToggleDone,
                )
            }
            ActionPill(
                text = if (note.pinned) "取消置顶" else "置顶",
                modifier = Modifier.weight(1f),
                enabled = !note.deleted,
                onClick = onTogglePinned,
            )
            ActionPill(
                text = "改变颜色",
                modifier = Modifier.weight(1f),
                enabled = !note.deleted,
                onClick = onColorClick,
            )
        }
        if (note.deleted) {
            ActionPill(text = "恢复便签", onClick = onRestoreClick, modifier = Modifier.fillMaxWidth())
        } else {
            ActionPill(
                text = "删除便签",
                onClick = onDeleteClick,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color(0xFFFFE4E6),
                contentColor = Color(0xFFB42318),
            )
        }
    }
}

@Composable
private fun TypePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFFE9D8FF) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.Transparent else Color(0xFF9BA1AE)),
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF2F3340),
        )
    }
}

@Composable
private fun ActionPill(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color(0xFFF5F6FA),
    contentColor: Color = Color(0xFF344054),
    onClick: () -> Unit,
) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(18.dp),
        color = if (enabled) containerColor else Color(0xFFE5E7EB),
        modifier = modifier,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (enabled) contentColor else Color(0xFF9CA3AF),
        )
    }
}

@Composable
private fun StatusBox(text: String) {
    Text(
        text = text,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F7FB), RoundedCornerShape(24.dp))
            .padding(18.dp),
        color = Color(0xFF6B7280),
    )
}
