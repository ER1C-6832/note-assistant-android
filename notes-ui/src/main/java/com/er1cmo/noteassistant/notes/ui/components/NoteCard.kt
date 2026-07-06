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
    modifier: Modifier = Modifier,
) {
    val cardColor = NoteColorPalette.colorFor(note.color)
    val isTodo = note.type == NoteType.Todo
    val contentAlpha = if (note.isDone) 0.55f else 1f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(22.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
            .alpha(contentAlpha),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (isTodo) {
                Checkbox(
                    checked = note.isDone,
                    onCheckedChange = null,
                    modifier = Modifier.size(32.dp),
                )
            }
            Text(
                text = note.title.ifBlank { "未命名便签" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textDecoration = if (note.isDone) TextDecoration.LineThrough else TextDecoration.None,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (note.pinned) {
                Text(
                    text = "置顶",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF7C5C00),
                )
            } else {
                Text(
                    text = "便签",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF8A94A6),
                )
            }
        }
        Text(
            text = note.content.ifBlank { "暂无正文" },
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF374151),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(
                onClick = {},
                label = { Text(if (isTodo) "待办" else "普通") },
                colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.56f)),
                border = null,
            )
            if (note.title.contains("王总") || note.content.contains("客户")) {
                AssistChip(
                    onClick = {},
                    label = { Text("客户") },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.56f)),
                    border = null,
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "刚刚更新",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF7B8494),
            )
        }
    }
}
