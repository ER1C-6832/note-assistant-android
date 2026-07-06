package com.er1cmo.noteassistant.notes.ui.list

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.ui.R
import com.er1cmo.noteassistant.notes.ui.components.NoteCard

private const val FILTER_ALL = "全部"
private const val FILTER_TODO = "待办"
private const val FILTER_DONE = "已完成"
private const val FILTER_PINNED = "置顶"
private const val TAG_PREFIX = "tag:"

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
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    val tagNames = remember(state.notes) {
        state.notes
            .flatMap { note -> note.tags.map { it.name } }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }
    val displayNotes = remember(state.notes, selectedFilter) {
        when {
            selectedFilter == FILTER_TODO -> state.notes.filter { it.type == NoteType.Todo && !it.isDone }
            selectedFilter == FILTER_DONE -> state.notes.filter { it.type == NoteType.Todo && it.isDone }
            selectedFilter == FILTER_PINNED -> state.notes.filter { it.pinned }
            selectedFilter.startsWith(TAG_PREFIX) -> {
                val tagName = selectedFilter.removePrefix(TAG_PREFIX)
                state.notes.filter { note -> note.tags.any { it.name == tagName } }
            }
            else -> state.notes
        }
    }

    Surface(color = Color.White, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HeaderBar(onSettingsClick = onSettingsClick)
                SearchBox()
                FilterBar(
                    selectedFilter = selectedFilter,
                    onTagPanelClick = { tagPanelOpen = true },
                    onFilterSelected = { selectedFilter = it },
                )

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
                        item { Spacer(Modifier.height(116.dp)) }
                    }
                }
            }

            Button(
                onClick = onCreateClick,
                shape = RoundedCornerShape(22.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D6BFF)),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp)
                    .width(154.dp)
                    .height(46.dp),
                contentPadding = ButtonDefaults.ContentPadding,
            ) {
                Text("+ 新建")
            }

            FloatingActionButton(
                onClick = onVoiceClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 22.dp, bottom = 84.dp)
                    .size(58.dp)
                    .shadow(8.dp, CircleShape),
                shape = CircleShape,
                containerColor = Color(0xFF5E6FFF),
                contentColor = Color.White,
            ) {
                Box(Modifier.size(22.dp))
            }

            if (tagPanelOpen) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.14f))
                        .clickable(onClick = { tagPanelOpen = false }),
                )
            }
            AnimatedVisibility(
                visible = tagPanelOpen,
                enter = slideInHorizontally(initialOffsetX = { -it }),
                exit = slideOutHorizontally(targetOffsetX = { -it }),
            ) {
                TagDrawer(
                    selectedFilter = selectedFilter,
                    tagNames = tagNames,
                    onFilterSelected = {
                        selectedFilter = it
                        tagPanelOpen = false
                    },
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(onSettingsClick: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RingLogo(size = 38)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = "小泓便签",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF222832),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "语音智能的便签 App",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF7A7280),
            )
        }
        Surface(
            onClick = onSettingsClick,
            shape = CircleShape,
            color = Color.White,
            shadowElevation = 2.dp,
            tonalElevation = 1.dp,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_gear),
                    contentDescription = "设置",
                    tint = Color(0xFF667085),
                    modifier = Modifier.size(21.dp),
                )
            }
        }
    }
}

@Composable
private fun RingLogo(size: Int) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .background(Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size((size * 0.62f).dp)
                .background(Color(0xFF5272FF), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .size((size * 0.28f).dp)
                    .background(Color.White, CircleShape),
            )
        }
    }
}

@Composable
private fun SearchBox() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FB), RoundedCornerShape(22.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("⌕  搜索标题、正文或标签", color = Color(0xFF7D8190), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun FilterBar(
    selectedFilter: String,
    onTagPanelClick: () -> Unit,
    onFilterSelected: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(
            onClick = onTagPanelClick,
            shape = RoundedCornerShape(15.dp),
            color = Color(0xFFFFE5A8),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = "☰",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF604410),
            )
        }
        listOf(FILTER_ALL, FILTER_TODO, FILTER_DONE, FILTER_PINNED).forEach { filter ->
            FilterChip(
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
                label = { Text(filter) },
            )
        }
    }
}

@Composable
private fun EmptyNotes(onCreateClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF7F8FB), RoundedCornerShape(26.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("还没有便签", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("先记下一条想法、待办或临时信息。", color = Color(0xFF6B7280))
        Button(onClick = onCreateClick, shape = RoundedCornerShape(18.dp)) {
            Text("创建第一条便签")
        }
    }
}

@Composable
private fun TagDrawer(
    selectedFilter: String,
    tagNames: List<String>,
    onFilterSelected: (String) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxHeight()
            .width(286.dp),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = Color(0xFFFFF3D1),
        tonalElevation = 10.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier.padding(start = 20.dp, end = 18.dp, top = 50.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("标签与筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A2D16))
            Text("选择范围后返回列表", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A7651))
            Spacer(Modifier.height(8.dp))
            listOf(FILTER_ALL, FILTER_TODO, FILTER_DONE, FILTER_PINNED).forEach { item ->
                TagDrawerRow(
                    text = item,
                    selected = selectedFilter == item,
                    onClick = { onFilterSelected(item) },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("标签", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A7651))
            if (tagNames.isEmpty()) {
                Text("暂无标签，可在编辑页先填写。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A8A70))
            } else {
                tagNames.forEach { tag ->
                    val filterValue = "$TAG_PREFIX$tag"
                    TagDrawerRow(
                        text = "# $tag",
                        selected = selectedFilter == filterValue,
                        onClick = { onFilterSelected(filterValue) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagDrawerRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFFFD978) else Color.Transparent, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            color = if (selected) Color(0xFF3A2A08) else Color(0xFF4A3A20),
            style = MaterialTheme.typography.bodyLarge,
        )
        if (selected) Text("✓", color = Color(0xFF5E4100), fontWeight = FontWeight.SemiBold)
    }
}
