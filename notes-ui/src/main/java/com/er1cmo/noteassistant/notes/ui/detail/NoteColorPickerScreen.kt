package com.er1cmo.noteassistant.notes.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
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
import com.er1cmo.noteassistant.notes.ui.components.NoteColorPalette

@Composable
fun NoteColorPickerRoute(
    onBackClick: () -> Unit,
    viewModel: NoteColorPickerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteColorPickerScreen(
        state = state,
        onBackClick = onBackClick,
        onColorClick = viewModel::selectColor,
    )
}

@Composable
fun NoteColorPickerScreen(
    state: NoteColorPickerState,
    onBackClick: () -> Unit,
    onColorClick: (String) -> Unit,
) {
    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
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
            Text(
                text = "改变颜色",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF20242C),
            )
            if (state.isLoading) {
                Text("正在读取便签……", color = Color(0xFF6B7280))
            } else if (state.note == null) {
                Text("没有找到这条便签。", color = Color(0xFF6B7280))
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    NoteColorPalette.options.chunked(2).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { option ->
                                ColorOption(
                                    label = option.name,
                                    color = option.color,
                                    selected = state.note.color == option.hex,
                                    onClick = { onColorClick(option.hex) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ColorOption(
    label: String,
    color: Color,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = color,
        tonalElevation = if (selected) 6.dp else 0.dp,
        shadowElevation = if (selected) 4.dp else 0.dp,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.72f)) {
                Text(
                    text = if (selected) "✓" else " ",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFF20242C),
                )
            }
            Text(
                text = label,
                modifier = Modifier.padding(start = 10.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF20242C),
            )
        }
    }
}
