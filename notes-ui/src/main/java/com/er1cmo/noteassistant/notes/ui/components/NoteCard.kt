package com.er1cmo.noteassistant.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onTodoCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = NoteColorPalette.colorFor(note.color)
    val isTodo = note.type == NoteType.Todo
    val contentAlpha = if (note.isDone) 0.58f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isTodo) {
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
                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
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
                text = if (note.deleted) "最近删除" else "已保存",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF8A8490),
            )
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
