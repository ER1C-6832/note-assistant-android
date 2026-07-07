package com.er1cmo.noteassistant.notes.ui.list

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.ui.R
import com.er1cmo.noteassistant.notes.ui.components.NoteCard

private const val FILTER_ALL = "全部"
private const val FILTER_TODO = "待办"
private const val FILTER_DONE = "已完成"
private const val FILTER_PINNED = "置顶"
private const val FILTER_DELETED = "最近删除"
private const val TAG_PREFIX = "tag-id:"

@Composable
fun NoteListRoute(
    onCreateClick: (String?) -> Unit,
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
        onTodoCheckedChange = viewModel::toggleTodoDone,
        onCreateTag = viewModel::createTag,
        onRenameTag = viewModel::renameTag,
        onDeleteTag = viewModel::deleteTag,
    )
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    onCreateClick: (String?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onTodoCheckedChange: (Long, Boolean) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Long) -> Unit,
) {
    var tagPanelOpen by remember { mutableStateOf(false) }
    var selectedFilter by remember { mutableStateOf(FILTER_ALL) }
    var searchQuery by remember { mutableStateOf("") }

    val selectedTag = remember(state.tags, selectedFilter) {
        selectedFilter.selectedTagId()?.let { id -> state.tags.firstOrNull { it.id == id } }
    }
    val selectedCreateTag = selectedTag?.name
    val baseNotes = remember(state.notes, state.deletedNotes, selectedFilter, selectedTag) {
        when {
            selectedFilter == FILTER_DELETED -> state.deletedNotes
            selectedFilter == FILTER_TODO -> state.notes.filter { it.type == NoteType.Todo && !it.isDone }
            selectedFilter == FILTER_DONE -> state.notes.filter { it.type == NoteType.Todo && it.isDone }
            selectedFilter == FILTER_PINNED -> state.notes.filter { it.pinned }
            selectedTag != null -> state.notes.filter { note ->
                note.tags.any { it.id == selectedTag.id || it.normalizedName == selectedTag.normalizedName }
            }
            else -> state.notes
        }
    }
    val displayNotes = remember(baseNotes, searchQuery) {
        baseNotes.searchBy(searchQuery)
    }

    BackHandler(enabled = tagPanelOpen) {
        tagPanelOpen = false
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
                SearchBox(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                )
                FilterBar(
                    selectedFilter = selectedFilter,
                    onTagPanelClick = { tagPanelOpen = true },
                    onFilterSelected = { selectedFilter = it },
                )
                SearchResultSummary(
                    query = searchQuery,
                    selectedTagName = selectedCreateTag,
                    resultCount = displayNotes.size,
                    onClearQuery = { searchQuery = "" },
                )

                if (displayNotes.isEmpty()) {
                    EmptyNotes(
                        selectedFilter = selectedFilter,
                        selectedTagName = selectedCreateTag,
                        searchQuery = searchQuery,
                        onCreateClick = { onCreateClick(selectedCreateTag) },
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(displayNotes, key = { it.id }) { note ->
                            NoteCard(
                                note = note,
                                onClick = { onNoteClick(note.id) },
                                onTodoCheckedChange = { done -> onTodoCheckedChange(note.id, done) },
                            )
                        }
                        item { Spacer(Modifier.height(116.dp)) }
                    }
                }
            }

            Button(
                onClick = { onCreateClick(selectedCreateTag) },
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
                        .background(Color.Black.copy(alpha = 0.16f))
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
                    tags = state.tags,
                    deletedCount = state.deletedNotes.size,
                    onFilterSelected = {
                        selectedFilter = it
                        tagPanelOpen = false
                    },
                    onCreateTag = onCreateTag,
                    onRenameTag = onRenameTag,
                    onDeleteTag = { tag ->
                        if (selectedFilter == tag.filterValue()) selectedFilter = FILTER_ALL
                        onDeleteTag(tag.id)
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
            color = Color.White.copy(alpha = 0.96f),
            shadowElevation = 2.dp,
            tonalElevation = 1.dp,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_settings_gear_simple),
                    contentDescription = "设置",
                    modifier = Modifier.size(22.dp),
                    tint = Color(0xFF667085),
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
            .background(Color.White.copy(alpha = 0.9f), CircleShape),
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
private fun SearchBox(query: String, onQueryChange: (String) -> Unit) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(22.dp),
        placeholder = {
            Text("搜索标题、正文或标签", color = Color(0xFF7D8190))
        },
        leadingIcon = {
            Text("⌕", color = Color(0xFF7D8190), style = MaterialTheme.typography.bodyMedium)
        },
        trailingIcon = if (query.isNotBlank()) {
            {
                Text(
                    text = "清除",
                    modifier = Modifier.clickable(onClick = { onQueryChange("") }),
                    color = Color(0xFF3D6BFF),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        } else {
            null
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color.Transparent,
            unfocusedBorderColor = Color.Transparent,
            focusedContainerColor = Color(0xFFF6F7FB),
            unfocusedContainerColor = Color(0xFFF6F7FB),
            cursorColor = Color(0xFF3D6BFF),
        ),
    )
}

@Composable
private fun FilterBar(
    selectedFilter: String,
    onTagPanelClick: () -> Unit,
    onFilterSelected: (String) -> Unit,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            onClick = onTagPanelClick,
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFFFFE3A1),
            tonalElevation = 1.dp,
        ) {
            Text(
                text = "☰",
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 11.dp),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFF604410),
                maxLines = 1,
            )
        }
        listOf(FILTER_ALL, FILTER_TODO, FILTER_DONE, FILTER_PINNED).forEach { filter ->
            FilterPill(
                text = filter,
                selected = selectedFilter == filter,
                onClick = { onFilterSelected(filter) },
            )
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFEDE0FF) else Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) Color.Transparent else Color(0xFF535864)),
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            style = MaterialTheme.typography.labelLarge,
            color = Color(0xFF222832),
            maxLines = 1,
        )
    }
}

@Composable
private fun SearchResultSummary(
    query: String,
    selectedTagName: String?,
    resultCount: Int,
    onClearQuery: () -> Unit,
) {
    val trimmedQuery = query.trim()
    if (trimmedQuery.isBlank() && selectedTagName == null) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF8FAFF), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val scopeText = selectedTagName?.let { "# $it" } ?: "当前列表"
        val queryText = if (trimmedQuery.isBlank()) "" else " · 关键词：$trimmedQuery"
        Text(
            text = "$scopeText$queryText · $resultCount 条",
            modifier = Modifier.weight(1f),
            color = Color(0xFF596074),
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trimmedQuery.isNotBlank()) {
            Text(
                text = "清除",
                modifier = Modifier.clickable(onClick = onClearQuery),
                color = Color(0xFF3D6BFF),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun EmptyNotes(
    selectedFilter: String,
    selectedTagName: String?,
    searchQuery: String,
    onCreateClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFF6F7FB), RoundedCornerShape(26.dp))
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val hasQuery = searchQuery.trim().isNotEmpty()
        val title = when {
            hasQuery -> "没有找到相关便签"
            selectedFilter == FILTER_DELETED -> "最近删除为空"
            selectedTagName != null -> "# $selectedTagName 下还没有便签"
            else -> "还没有便签"
        }
        val description = when {
            hasQuery -> "可以删减关键词，或新建一条更容易找到的便签。"
            selectedFilter == FILTER_DELETED -> "软删除的便签会出现在这里。"
            selectedTagName != null -> "新建便签会自动带上这个标签。"
            else -> "先记下一条想法、待办或临时信息。"
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = Color(0xFF6B7280))
        if (selectedFilter != FILTER_DELETED) {
            Button(onClick = onCreateClick, shape = RoundedCornerShape(18.dp)) {
                Text(if (selectedTagName != null) "使用 #$selectedTagName 创建" else "创建第一条便签")
            }
        }
    }
}

@Composable
private fun TagDrawer(
    selectedFilter: String,
    tags: List<Tag>,
    deletedCount: Int,
    onFilterSelected: (String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    var editingTagId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmTag by remember { mutableStateOf<Tag?>(null) }

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
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 20.dp, end = 18.dp, top = 50.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("标签与筛选", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = Color(0xFF3A2D16))
            Text("选择范围后返回列表", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A7651))
            Spacer(Modifier.height(8.dp))
            listOf(FILTER_ALL, FILTER_PINNED, FILTER_TODO, FILTER_DONE).forEach { item ->
                TagDrawerRow(
                    text = item,
                    selected = selectedFilter == item,
                    onClick = { onFilterSelected(item) },
                )
            }
            TagDrawerRow(
                text = "最近删除${if (deletedCount > 0) "  $deletedCount" else ""}",
                selected = selectedFilter == FILTER_DELETED,
                onClick = { onFilterSelected(FILTER_DELETED) },
            )
            Spacer(Modifier.height(12.dp))
            Text("标签管理", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A7651))
            OutlinedTextField(
                value = newTagName,
                onValueChange = { newTagName = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("新建标签") },
                singleLine = true,
            )
            Button(
                onClick = {
                    val name = newTagName.trim()
                    if (name.isNotEmpty()) {
                        onCreateTag(name)
                        newTagName = ""
                    }
                },
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("创建标签")
            }

            Text("已创建标签", style = MaterialTheme.typography.labelLarge, color = Color(0xFF8A7651))
            if (tags.isEmpty()) {
                Text("暂无标签。", style = MaterialTheme.typography.bodySmall, color = Color(0xFF9A8A70))
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(tags, key = { it.id }) { tag ->
                        if (editingTagId == tag.id) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                OutlinedTextField(
                                    value = editingName,
                                    onValueChange = { editingName = it },
                                    label = { Text("重命名") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        onClick = {
                                            val name = editingName.trim()
                                            if (name.isNotEmpty()) onRenameTag(tag.id, name)
                                            editingTagId = null
                                            editingName = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color(0xFFFFD978),
                                    ) {
                                        Text("保存", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF3A2A08))
                                    }
                                    Surface(
                                        onClick = {
                                            editingTagId = null
                                            editingName = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = Color.White.copy(alpha = 0.72f),
                                    ) {
                                        Text("取消", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = Color(0xFF4A3A20))
                                    }
                                }
                            }
                        } else {
                            TagManageRow(
                                tag = tag,
                                selected = selectedFilter == tag.filterValue(),
                                onSelect = { onFilterSelected(tag.filterValue()) },
                                onRename = {
                                    editingTagId = tag.id
                                    editingName = tag.name
                                },
                                onDelete = { deleteConfirmTag = tag },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteConfirmTag?.let { tag ->
        AlertDialog(
            onDismissRequest = { deleteConfirmTag = null },
            title = { Text("删除标签？") },
            text = { Text("删除 #${tag.name} 后，该标签会从所有便签中移除，且不能直接恢复。确认删除吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        deleteConfirmTag = null
                        onDeleteTag(tag)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                ) {
                    Text("删除")
                }
            },
            dismissButton = {
                Surface(onClick = { deleteConfirmTag = null }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F5F7)) {
                    Text("取消", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            },
        )
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

@Composable
private fun TagManageRow(
    tag: Tag,
    selected: Boolean,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) Color(0xFFFFD978) else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "# ${tag.name}",
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onSelect),
                color = if (selected) Color(0xFF3A2A08) else Color(0xFF4A3A20),
                style = MaterialTheme.typography.bodyLarge,
            )
            if (selected) Text("✓", color = Color(0xFF5E4100), fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "重命名",
                modifier = Modifier.clickable(onClick = onRename),
                color = Color(0xFF5E4100),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = "删除",
                modifier = Modifier.clickable(onClick = onDelete),
                color = Color(0xFFB42318),
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

private fun List<Note>.searchBy(query: String): List<Note> {
    val normalizedQuery = query.trim().lowercase()
    if (normalizedQuery.isBlank()) return this
    return mapNotNull { note ->
        note.searchRank(normalizedQuery)?.let { rank -> rank to note }
    }.sortedWith(
        compareBy<Pair<Int, Note>> { it.first }
            .thenByDescending { it.second.pinned }
            .thenByDescending { it.second.updatedAt }
            .thenByDescending { it.second.id },
    ).map { it.second }
}

private fun Note.searchRank(normalizedQuery: String): Int? {
    val titleText = title.lowercase()
    val contentText = content.lowercase()
    val tagTexts = tags.map { it.name.lowercase() }
    return when {
        titleText == normalizedQuery -> 0
        titleText.contains(normalizedQuery) -> 1
        tagTexts.any { it == normalizedQuery } -> 2
        tagTexts.any { it.contains(normalizedQuery) } -> 3
        contentText.contains(normalizedQuery) -> 4
        else -> null
    }
}

private fun Tag.filterValue(): String = "$TAG_PREFIX$id"

private fun String.selectedTagId(): Long? = if (startsWith(TAG_PREFIX)) removePrefix(TAG_PREFIX).toLongOrNull() else null
