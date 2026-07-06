package com.er1cmo.noteassistant.notes.ui.list

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.ui.components.NoteCard

@Composable
fun NoteListRoute(
    onCreateClick: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteListScreen(
        state = state,
        onCreateClick = onCreateClick,
        onNoteClick = onNoteClick,
        onSettingsClick = onSettingsClick,
        onVoiceClick = {},
    )
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    onCreateClick: () -> Unit,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onVoiceClick: () -> Unit,
) {
    var tagPanelOpen by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf("全部") }
    val displayNotes = remember(state.notes, selectedFilter) {
        when (selectedFilter) {
            "待办" -> state.notes.filter { it.type == NoteType.Todo && !it.isDone }
            "已完成" -> state.notes.filter { it.type == NoteType.Todo && it.isDone }
            "置顶" -> state.notes.filter { it.pinned }
            else -> state.notes
        }
    }

    Surface(color = Color(0xFFF5F6FA), modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(
                        onClick = { tagPanelOpen = true },
                        shape = RoundedCornerShape(16.dp),
                        contentPadding = ButtonDefaults.ContentPadding,
                    ) {
                        Text("☰")
                    }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp),
                    ) {
                        Text("小泓便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text("为记录而生，也为效率而来", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                    }
                    OutlinedButton(onClick = onSettingsClick, shape = RoundedCornerShape(16.dp)) {
                        Text("设置")
                    }
                }

                SearchBox()

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("全部", "待办", "已完成", "置顶").forEach { filter ->
                        FilterChip(
                            selected = selectedFilter == filter,
                            onClick = { selectedFilter = filter },
                            label = { Text(filter) },
                        )
                    }
                }

                if (displayNotes.isEmpty()) {
                    EmptyNotes(onCreateClick = onCreateClick)
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(displayNotes, key = { it.id }) { note ->
                            NoteCard(note = note, onClick = { onNoteClick(note.id) })
                        }
                        item { Spacer(Modifier.height(112.dp)) }
                    }
                }

                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2F6BFF)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("+ 新建便签")
                }
            }

            FloatingActionButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 86.dp)
                    .size(64.dp)
                    .shadow(10.dp, CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF5B6CFF),
                contentColor = Color.White,
            ) {
                Text("声", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            if (tagPanelOpen) {
                TagPanel(
                    selectedFilter = selectedFilter,
                    onFilterSelected = {
                        selectedFilter = it
                        tagPanelOpen = false
                    },
                    onDismiss = { tagPanelOpen = false },
                )
            }
        }
    }
}

@Composable
private fun SearchBox() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕  搜索标题、正文或标签", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun EmptyNotes(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White, RoundedCornerShape(24.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("还没有便签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Text("先记下一条想法、待办或临时信息。", color = Color(0xFF6B7280))
        Button(onClick = onCreateClick, shape = RoundedCornerShape(16.dp)) {
            Text("创建第一条便签")
        }
    }
}

@Composable
private fun TagPanel(
    selectedFilter: String,
    onFilterSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f))
                .clickable(onClick = onDismiss),
        )
        Surface(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 14.dp, top = 112.dp)
                .width(270.dp),
            shape = RoundedCornerShape(24.dp),
            color = Color.White,
            tonalElevation = 8.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("标签与筛选", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                listOf("全部", "待办", "已完成", "置顶").forEach { item ->
                    TagPanelRow(
                        text = item,
                        selected = selectedFilter == item,
                        onClick = { onFilterSelected(item) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text("标签预览", style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A94A6))
                listOf("客户", "硬件", "生活", "灵感").forEach { tag ->
                    TagPanelRow(text = "# $tag", selected = false, onClick = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun TagPanelRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFEFF3FF) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, modifier = Modifier.weight(1f), color = if (selected) Color(0xFF2F6BFF) else Color(0xFF333B4F))
        if (selected) Text("✓", color = Color(0xFF2F6BFF))
    }
}
