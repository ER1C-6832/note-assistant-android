package com.er1cmo.noteassistant.notes.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.er1cmo.noteassistant.notes.domain.model.Note

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardColor = note.color?.let { runCatching { Color(android.graphics.Color.parseColor(it)) }.getOrNull() } ?: Color.White
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(cardColor, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = note.title.ifBlank { "未命名便签" },
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (note.pinned) "置顶" else "今天",
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF8A94A6),
            )
        }
        Text(
            text = note.content,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF374151),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("客户", "跟进").take(if (note.title.contains("王总")) 2 else 1).forEach { tag ->
                AssistChip(
                    onClick = {},
                    label = { Text(tag) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = Color.White.copy(alpha = 0.55f)),
                    border = null,
                )
            }
        }
    }
}
