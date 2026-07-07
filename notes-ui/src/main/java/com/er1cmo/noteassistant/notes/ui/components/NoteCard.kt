package com.er1cmo.noteassistant.notes.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTodoCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
) {
    val cardColor = NoteColorPalette.colorFor(note.color)
    val isTodo = note.type == NoteType.Todo
    val contentAlpha = if (note.isDone) 0.58f else 1f
    val doneDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None
    val cardShape = RoundedCornerShape(24.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, cardShape)
            .then(
                if (selected) Modifier.border(2.dp, Color(0xFF3D6BFF), cardShape) else Modifier,
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(16.dp)
            .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selectionMode) {
                SelectionDot(selected = selected)
            } else if (isTodo) {
                Checkbox(
                    checked = note.isDone,
                    onCheckedChange = onTodoCheckedChange,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = note.title.ifBlank { "未命名便签" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textDecoration = doneDecoration,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color(0xFF20242C),
            )
            if (note.pinned) {
                Text(
                    text = "置顶",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7C5C00),
                )
            }
        }
        Text(
            text = note.content.ifBlank { "暂无正文" },
            style = MaterialTheme.typography.bodyMedium,
            textDecoration = doneDecoration,
            color = Color(0xFF404756),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            note.tags.take(3).forEach { tag ->
                SoftChip(text = tag.name)
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (note.deleted) "最近删除" else "刚刚更新",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8A8490),
            )
        }
    }
}

@Composable
private fun SelectionDot(selected: Boolean) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .size(24.dp)
            .background(if (selected) Color(0xFF3D6BFF) else Color.White.copy(alpha = 0.86f), CircleShape)
            .border(1.dp, if (selected) Color(0xFF3D6BFF) else Color(0xFF9CA3AF), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Text("✓", color = Color.White, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun SoftChip(text: String) {
    AssistChip(
        onClick = {},
        label = { Text(text) },
        colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.62f)),
        border = null,
    )
}
