package com.er1cmo.noteassistant.notes.ui.detail

import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
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
    LaunchedEffect(state.hardDeleted) {
        if (state.hardDeleted) onBackClick()
    }
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
        onToggleArchived = viewModel::toggleArchived,
        onDeleteClick = viewModel::softDelete,
        onRestoreClick = viewModel::restore,
        onPermanentDeleteClick = viewModel::permanentlyDelete,
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
    onToggleArchived: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onPermanentDeleteClick: () -> Unit,
    onColorClick: (Long) -> Unit,
) {
    var showPermanentDeleteDialog by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(onClick = onBackClick, shape = RoundedCornerShape(16.dp), color = Color(0xFFF4F5F7)) {
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
                        readOnly = state.note.deleted,
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
                        ) { Text(if (state.isSaving) "保存中……" else "保存内容") }
                    }
                    ActionArea(
                        note = state.note,
                        onTypeChange = onTypeChange,
                        onToggleDone = onToggleDone,
                        onTogglePinned = onTogglePinned,
                        onToggleArchived = onToggleArchived,
                        onDeleteClick = onDeleteClick,
                        onRestoreClick = onRestoreClick,
                        onCopyClick = { clipboard.setText(AnnotatedString(state.note.toCopyText())) },
                        onPermanentDeleteClick = { showPermanentDeleteDialog = true },
                        onColorClick = { onColorClick(state.note.id) },
                    )
                    state.message?.let { Text(it, color = Color(0xFF6B7280), style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }

    if (showPermanentDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showPermanentDeleteDialog = false },
            title = { Text("彻底删除便签？") },
            text = { Text("彻底删除后无法恢复。确认删除这条便签吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showPermanentDeleteDialog = false
                        onPermanentDeleteClick()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                ) { Text("彻底删除") }
            },
            dismissButton = {
                Surface(onClick = { showPermanentDeleteDialog = false }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F5F7)) {
                    Text("取消", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            },
        )
    }
}

@Composable
private fun DetailEditorCard(
    note: Note,
    title: String,
    content: String,
    tagText: String,
    readOnly: Boolean,
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
                readOnly = readOnly,
                singleLine = false,
            )
            when {
                note.deleted -> Text("最近删除", style = MaterialTheme.typography.labelMedium, color = Color(0xFFB42318))
                note.archived -> Text("已归档", style = MaterialTheme.typography.labelMedium, color = Color(0xFF475467))
                note.pinned -> Text("置顶", style = MaterialTheme.typography.labelMedium, color = Color(0xFF7C5C00))
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color(0x1F000000)))
        InlineTextField(
            value = content,
            onValueChange = onContentChange,
            placeholder = "暂无正文",
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Color(0xFF404756)),
            readOnly = readOnly,
            minHeight = 160,
        )
        InlineTagField(value = tagText, readOnly = readOnly, onValueChange = onTagTextChange)
    }
}

@Composable
private fun InlineTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    textStyle: TextStyle,
    readOnly: Boolean,
    singleLine: Boolean = false,
    minHeight: Int = 0,
) {
    Box(modifier = modifier.then(if (minHeight > 0) Modifier.height(minHeight.dp) else Modifier)) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = textStyle,
            singleLine = singleLine,
            readOnly = readOnly,
            modifier = Modifier.fillMaxWidth(),
            decorationBox = { innerTextField ->
                if (value.isBlank()) Text(placeholder, style = textStyle, color = Color(0x88404756))
                innerTextField()
            },
        )
    }
}

@Composable
private fun InlineTagField(value: String, readOnly: Boolean, onValueChange: (String) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("#", style = MaterialTheme.typography.bodyMedium, color = Color(0xFF5C6372))
        Spacer(Modifier.width(4.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF5C6372)),
            singleLine = true,
            readOnly = readOnly,
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                if (value.isBlank()) Text("标签", style = MaterialTheme.typography.bodyMedium, color = Color(0x885C6372))
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
    onToggleArchived: () -> Unit,
    onDeleteClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onCopyClick: () -> Unit,
    onPermanentDeleteClick: () -> Unit,
    onColorClick: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (note.deleted) {
            Text("最近删除中的便签不可编辑，可复制、恢复或彻底删除。", color = Color(0xFF8A8490), style = MaterialTheme.typography.bodySmall)
            ActionPill(text = "复制内容", onClick = onCopyClick, modifier = Modifier.fillMaxWidth())
            ActionPill(text = "恢复便签", onClick = onRestoreClick, modifier = Modifier.fillMaxWidth())
            ActionPill(
                text = "彻底删除",
                onClick = onPermanentDeleteClick,
                modifier = Modifier.fillMaxWidth(),
                containerColor = Color(0xFFFFE4E6),
                contentColor = Color(0xFFB42318),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TypePill(text = "普通便签", selected = note.type == NoteType.Normal, modifier = Modifier.weight(1f), onClick = { onTypeChange(NoteType.Normal) })
                TypePill(text = "待办便签", selected = note.type == NoteType.Todo, modifier = Modifier.weight(1f), onClick = { onTypeChange(NoteType.Todo) })
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note.type == NoteType.Todo) {
                    ActionPill(text = if (note.isDone) "取消完成" else "标记完成", modifier = Modifier.weight(1f), onClick = onToggleDone)
                }
                ActionPill(text = if (note.pinned) "取消置顶" else "置顶", modifier = Modifier.weight(1f), onClick = onTogglePinned)
                ActionPill(text = "改变颜色", modifier = Modifier.weight(1f), onClick = onColorClick)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ActionPill(text = if (note.archived) "取消归档" else "归档", modifier = Modifier.weight(1f), onClick = onToggleArchived)
                ActionPill(
                    text = "删除便签",
                    onClick = onDeleteClick,
                    modifier = Modifier.weight(1f),
                    containerColor = Color(0xFFFFE4E6),
                    contentColor = Color(0xFFB42318),
                )
            }
        }
    }
}

@Composable
private fun TypePill(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) Color(0xFFEDE0FF) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.Transparent else Color(0xFFE5E7EB)),
        modifier = modifier,
    ) {
        Text(text, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), color = Color(0xFF344054), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun ActionPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = Color(0xFFF6F7FB),
    contentColor: Color = Color(0xFF344054),
) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = containerColor, modifier = modifier) {
        Text(text, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp), color = contentColor, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun StatusBox(text: String) {
    Box(modifier = Modifier.fillMaxWidth().background(Color(0xFFF6F7FB), RoundedCornerShape(24.dp)).padding(20.dp)) {
        Text(text, color = Color(0xFF6B7280))
    }
}

private fun Note.toCopyText(): String = buildString {
    appendLine(title.ifBlank { "未命名便签" })
    if (content.isNotBlank()) appendLine(content)
    if (tags.isNotEmpty()) append(tags.joinToString("、", prefix = "#"))
}
