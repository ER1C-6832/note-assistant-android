package com.er1cmo.noteassistant.notes.ui.list

import android.graphics.Color as AndroidColor
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.model.Tag
import com.er1cmo.noteassistant.notes.ui.components.NoteCard

private const val FILTER_ALL = "全部"
private const val FILTER_PINNED = "置顶"
private const val FILTER_TODO = "待办"
private const val FILTER_DONE = "已完成"
private const val FILTER_ARCHIVED = "已归档"
private const val FILTER_DELETED = "最近删除"
private const val TAG_PREFIX = "tag-id:"
private val MAIN_FILTERS = listOf(FILTER_ALL, FILTER_PINNED, FILTER_TODO, FILTER_DONE)
private val DRAWER_FIXED_FILTERS = listOf(FILTER_ALL, FILTER_PINNED, FILTER_TODO, FILTER_DONE, FILTER_ARCHIVED, FILTER_DELETED)

private enum class BatchConfirmAction { SoftDelete, PermanentDelete }

@Composable
fun NoteListRoute(
    onCreateClick: (String?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    externalCommand: NoteListExternalCommand? = null,
    onExternalCommandConsumed: (Long) -> Unit = {},
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteListScreen(
        state = state,
        externalCommand = externalCommand,
        onExternalCommandConsumed = onExternalCommandConsumed,
        onCreateClick = onCreateClick,
        onNoteClick = onNoteClick,
        onSettingsClick = onSettingsClick,
        onTodoCheckedChange = viewModel::toggleTodoDone,
        onBatchSetPinned = viewModel::setPinned,
        onBatchSetDone = viewModel::setTodoDone,
        onBatchSetArchived = viewModel::setArchived,
        onBatchSoftDelete = viewModel::softDelete,
        onBatchRestore = viewModel::restoreDeleted,
        onBatchPermanentDelete = viewModel::permanentlyDelete,
        onBatchAddTag = viewModel::addTagToNotes,
        onCreateTag = viewModel::createTag,
        onRenameTag = viewModel::renameTag,
        onDeleteTag = viewModel::deleteTag,
    )
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    externalCommand: NoteListExternalCommand? = null,
    onExternalCommandConsumed: (Long) -> Unit = {},
    onCreateClick: (String?) -> Unit,
    onNoteClick: (Long) -> Unit,
    onSettingsClick: () -> Unit,
    onTodoCheckedChange: (Long, Boolean) -> Unit,
    onBatchSetPinned: (Set<Long>, Boolean) -> Unit,
    onBatchSetDone: (Set<Long>, Boolean) -> Unit,
    onBatchSetArchived: (Set<Long>, Boolean) -> Unit,
    onBatchSoftDelete: (Set<Long>) -> Unit,
    onBatchRestore: (Set<Long>) -> Unit,
    onBatchPermanentDelete: (Set<Long>) -> Unit,
    onBatchAddTag: (Set<Long>, String) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Long) -> Unit,
) {
    var selectedFilter by rememberSaveable { mutableStateOf(FILTER_ALL) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var tagPanelOpen by remember { mutableStateOf(false) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var addTagDialogOpen by remember { mutableStateOf(false) }
    var batchTagText by remember { mutableStateOf("") }
    var confirmAction by remember { mutableStateOf<BatchConfirmAction?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    var pinnedHomeTagId by rememberSaveable { mutableStateOf<Long?>(null) }
    var gridColumns by rememberSaveable { mutableStateOf(1) }
    val snackbarHostState = remember { SnackbarHostState() }

    val selectedTag = remember(state.tags, selectedFilter) {
        selectedFilter.selectedTagId()?.let { id -> state.tags.firstOrNull { it.id == id } }
    }
    val pinnedHomeTag = remember(state.tags, pinnedHomeTagId) {
        pinnedHomeTagId?.let { id -> state.tags.firstOrNull { it.id == id } }
    }
    val selectedCreateTag = selectedTag?.name
    val displayNotes = remember(state.notes, state.deletedNotes, state.archivedNotes, selectedFilter, selectedTag, searchQuery) {
        val scopedNotes = when {
            selectedFilter == FILTER_DELETED -> state.deletedNotes
            selectedFilter == FILTER_ARCHIVED -> state.archivedNotes
            selectedFilter == FILTER_TODO -> state.notes.filter { it.type == NoteType.Todo }
            selectedFilter == FILTER_DONE -> state.notes.filter { it.type == NoteType.Todo && it.isDone }
            selectedFilter == FILTER_PINNED -> state.notes.filter { it.pinned }
            selectedTag != null -> state.notes.filter { note ->
                note.tags.any { it.id == selectedTag.id || it.normalizedName == selectedTag.normalizedName }
            }
            else -> state.notes
        }
        scopedNotes.filteredAndSortedBy(searchQuery)
    }
    val displayedIds = remember(displayNotes) { displayNotes.map { it.id }.toSet() }
    val selectedNotes = remember(displayNotes, selectedIds) { displayNotes.filter { it.id in selectedIds } }
    val isDeletedScope = selectedFilter == FILTER_DELETED
    val isArchivedScope = selectedFilter == FILTER_ARCHIVED
    val scopeLabel = selectedFilter.labelFor(selectedTag)
    val homeBackground = colorFromHex(state.homeBackgroundColor, Color.White)
    val tagDrawerBackground = colorFromHex(state.tagDrawerBackgroundColor, Color(0xFFFFF3D1))
    val homeIsDark = state.homeBackgroundColor.isDarkHex()
    val onHomeColor = if (homeIsDark) Color.White else Color(0xFF222832)

    fun exitSelection() {
        selectionMode = false
        selectedIds = emptySet()
    }

    fun selectFilter(filter: String) {
        selectedFilter = filter
        exitSelection()
    }

    fun showTagByExternalCommand(tagId: Long?, tagName: String) {
        val cleanedName = tagName.trim().trimStart('#')
        val tag = when {
            tagId != null -> state.tags.firstOrNull { it.id == tagId }
            cleanedName.isNotBlank() -> state.tags.firstOrNull {
                it.name.equals(cleanedName, ignoreCase = true) ||
                    it.normalizedName == cleanedName.lowercase()
            }
            else -> null
        }
        if (tag != null) {
            selectedFilter = tag.filterValue()
            searchQuery = ""
            snackbarMessage = "已显示 #${tag.name}"
        } else {
            selectedFilter = FILTER_ALL
            searchQuery = cleanedName
            snackbarMessage = if (cleanedName.isBlank()) "没有指定标签" else "没有找到标签 #$cleanedName，已改为搜索"
        }
        tagPanelOpen = false
        exitSelection()
    }

    LaunchedEffect(externalCommand, state.tags) {
        when (val command = externalCommand) {
            null -> Unit
            is NoteListExternalCommand.ShowSearch -> {
                selectedFilter = FILTER_ALL
                searchQuery = command.query.trim()
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowTag -> showTagByExternalCommand(command.tagId, command.tagName)
            is NoteListExternalCommand.ShowArchive -> {
                selectedFilter = FILTER_ARCHIVED
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowTrash -> {
                selectedFilter = FILTER_DELETED
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowPinned -> {
                selectedFilter = FILTER_PINNED
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowTodos -> {
                selectedFilter = FILTER_TODO
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowDone -> {
                selectedFilter = FILTER_DONE
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
            is NoteListExternalCommand.ShowNoteList -> {
                selectedFilter = FILTER_ALL
                searchQuery = ""
                tagPanelOpen = false
                exitSelection()
            }
        }
        externalCommand?.let { onExternalCommandConsumed(it.sequence) }
    }

    LaunchedEffect(pinnedHomeTagId, state.tags) {
        if (pinnedHomeTagId != null && pinnedHomeTag == null) pinnedHomeTagId = null
    }

    LaunchedEffect(displayedIds) {
        selectedIds = selectedIds.intersect(displayedIds)
    }

    LaunchedEffect(snackbarMessage) {
        val message = snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        snackbarMessage = null
    }

    BackHandler(enabled = tagPanelOpen || selectionMode) {
        when {
            tagPanelOpen -> tagPanelOpen = false
            selectionMode -> exitSelection()
        }
    }

    Scaffold(
        containerColor = homeBackground,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Surface(
            color = homeBackground,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    HeaderBar(onSettingsClick = onSettingsClick, textColor = onHomeColor)
                    SearchBox(query = searchQuery, onQueryChange = { searchQuery = it }, onClearClick = { searchQuery = "" })
                    FilterBar(
                        selectedFilter = selectedFilter,
                        pinnedHomeTag = pinnedHomeTag,
                        onTagPanelClick = { tagPanelOpen = true },
                        onFilterSelected = ::selectFilter,
                    )
                    SummaryBar(
                        scopeLabel = scopeLabel,
                        searchQuery = searchQuery,
                        resultCount = displayNotes.size,
                        gridColumns = gridColumns,
                        textColor = if (homeIsDark) Color(0xFFD0D5DD) else Color(0xFF7A7280),
                        onToggleColumns = { gridColumns = if (gridColumns == 1) 2 else 1 },
                    )

                    if (displayNotes.isEmpty()) {
                        EmptyNotes(
                            selectedFilter = selectedFilter,
                            selectedTagName = selectedCreateTag,
                            searchQuery = searchQuery,
                            onCreateClick = { onCreateClick(selectedCreateTag) },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        val rows = remember(displayNotes, gridColumns) { displayNotes.chunked(gridColumns) }
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(rows, key = { row -> row.joinToString(separator = ":") { it.id.toString() } }) { rowNotes ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    rowNotes.forEach { note ->
                                        val selected = note.id in selectedIds
                                        NoteCard(
                                            note = note,
                                            selected = selected,
                                            selectionMode = selectionMode,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (selectionMode) {
                                                    selectedIds = selectedIds.toggle(note.id)
                                                } else {
                                                    onNoteClick(note.id)
                                                }
                                            },
                                            onLongClick = {
                                                selectionMode = true
                                                selectedIds = selectedIds.toggle(note.id)
                                            },
                                            onTodoCheckedChange = { done -> onTodoCheckedChange(note.id, done) },
                                        )
                                    }
                                    repeat(gridColumns - rowNotes.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                            item { Spacer(Modifier.height(if (selectionMode) 126.dp else 96.dp)) }
                        }
                    }
                }

                if (!selectionMode && !isDeletedScope && !isArchivedScope) {
                    Button(
                        onClick = { onCreateClick(selectedCreateTag) },
                        shape = RoundedCornerShape(22.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3D6BFF)),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 14.dp)
                            .width(154.dp)
                            .height(46.dp),
                    ) { Text("+ 新建") }
                }

                if (selectionMode) {
                    SelectionFloatingBar(
                        selectedNotes = selectedNotes,
                        totalVisibleCount = displayNotes.size,
                        allVisibleSelected = displayedIds.isNotEmpty() && selectedIds.containsAll(displayedIds),
                        isDeletedScope = isDeletedScope,
                        isArchivedScope = isArchivedScope,
                        onSelectAllClick = {
                            selectedIds = if (displayedIds.isNotEmpty() && selectedIds.containsAll(displayedIds)) emptySet() else displayedIds
                        },
                        onExitClick = ::exitSelection,
                        onSetPinned = { pinned ->
                            if (selectedIds.isNotEmpty()) {
                                onBatchSetPinned(selectedIds, pinned)
                                snackbarMessage = if (pinned) "已置顶 ${selectedIds.size} 条便签" else "已取消置顶 ${selectedIds.size} 条便签"
                                exitSelection()
                            }
                        },
                        onSetDone = { done ->
                            if (selectedIds.isNotEmpty()) {
                                onBatchSetDone(selectedIds, done)
                                snackbarMessage = if (done) "已标记完成" else "已取消完成"
                                exitSelection()
                            }
                        },
                        onSetArchived = { archived ->
                            if (selectedIds.isNotEmpty()) {
                                onBatchSetArchived(selectedIds, archived)
                                snackbarMessage = if (archived) "已归档 ${selectedIds.size} 条便签" else "已取消归档 ${selectedIds.size} 条便签"
                                exitSelection()
                            }
                        },
                        onAddTag = { if (selectedIds.isNotEmpty()) addTagDialogOpen = true },
                        onSoftDelete = { if (selectedIds.isNotEmpty()) confirmAction = BatchConfirmAction.SoftDelete },
                        onRestore = {
                            if (selectedIds.isNotEmpty()) {
                                onBatchRestore(selectedIds)
                                snackbarMessage = "已恢复 ${selectedIds.size} 条便签"
                                exitSelection()
                            }
                        },
                        onPermanentDelete = { if (selectedIds.isNotEmpty()) confirmAction = BatchConfirmAction.PermanentDelete },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth(0.94f)
                            .padding(bottom = 12.dp),
                    )
                }

                if (tagPanelOpen) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.16f))
                            .clickable(onClick = { tagPanelOpen = false }),
                    )
                    TagDrawer(
                        selectedFilter = selectedFilter,
                        pinnedHomeTagId = pinnedHomeTagId,
                        tags = state.tags,
                        deletedCount = state.deletedNotes.size,
                        archivedCount = state.archivedNotes.size,
                        backgroundColor = tagDrawerBackground,
                        backgroundHex = state.tagDrawerBackgroundColor,
                        onFilterSelected = {
                            selectFilter(it)
                            tagPanelOpen = false
                        },
                        onPinnedHomeTagChange = { tagId -> pinnedHomeTagId = tagId },
                        onCreateTag = onCreateTag,
                        onRenameTag = onRenameTag,
                        onDeleteTag = { tag ->
                            if (selectedFilter == tag.filterValue()) selectedFilter = FILTER_ALL
                            if (pinnedHomeTagId == tag.id) pinnedHomeTagId = null
                            exitSelection()
                            onDeleteTag(tag.id)
                        },
                    )
                }
            }
        }
    }

    if (addTagDialogOpen) {
        AlertDialog(
            onDismissRequest = { addTagDialogOpen = false },
            title = { Text("批量加标签") },
            text = {
                OutlinedTextField(
                    value = batchTagText,
                    onValueChange = { batchTagText = it },
                    label = { Text("标签") },
                    placeholder = { Text("例如：客户、硬件") },
                    singleLine = true,
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val selectedCount = selectedIds.size
                        onBatchAddTag(selectedIds, batchTagText)
                        snackbarMessage = "已给 $selectedCount 条便签添加标签"
                        batchTagText = ""
                        addTagDialogOpen = false
                        exitSelection()
                    },
                    enabled = batchTagText.isNotBlank() && selectedIds.isNotEmpty(),
                ) { Text("保存") }
            },
            dismissButton = {
                Surface(onClick = { addTagDialogOpen = false }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F5F7)) {
                    Text("取消", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            },
        )
    }

    confirmAction?.let { action ->
        val selectedCount = selectedIds.size
        AlertDialog(
            onDismissRequest = { confirmAction = null },
            title = { Text(if (action == BatchConfirmAction.PermanentDelete) "彻底删除便签？" else "删除便签？") },
            text = {
                Text(
                    if (action == BatchConfirmAction.PermanentDelete) {
                        "将彻底删除 $selectedCount 条便签，删除后无法恢复。确认继续吗？"
                    } else {
                        "将 $selectedCount 条便签移入最近删除，可稍后恢复。确认继续吗？"
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (action == BatchConfirmAction.PermanentDelete) {
                            onBatchPermanentDelete(selectedIds)
                            snackbarMessage = "已彻底删除 $selectedCount 条便签"
                        } else {
                            onBatchSoftDelete(selectedIds)
                            snackbarMessage = "已删除 $selectedCount 条便签，可在最近删除中恢复"
                        }
                        confirmAction = null
                        exitSelection()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    enabled = selectedIds.isNotEmpty(),
                ) { Text(if (action == BatchConfirmAction.PermanentDelete) "彻底删除" else "删除") }
            },
            dismissButton = {
                Surface(onClick = { confirmAction = null }, shape = RoundedCornerShape(12.dp), color = Color(0xFFF4F5F7)) {
                    Text("取消", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp))
                }
            },
        )
    }
}

@Composable
private fun HeaderBar(onSettingsClick: () -> Unit, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        RingLogo(size = 34)
        Text(
            text = "小泓便签",
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Surface(onClick = onSettingsClick, shape = CircleShape, color = Color.White.copy(alpha = 0.96f), shadowElevation = 2.dp, tonalElevation = 1.dp) {
            Text("设置", modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp), color = Color(0xFF667085), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun RingLogo(size: Int) {
    Box(modifier = Modifier.size(size.dp).background(Color.White.copy(alpha = 0.9f), CircleShape), contentAlignment = Alignment.Center) {
        Box(modifier = Modifier.size((size * 0.62f).dp).background(Color(0xFF5272FF), CircleShape), contentAlignment = Alignment.Center) {
            Box(modifier = Modifier.size((size * 0.28f).dp).background(Color.White, CircleShape))
        }
    }
}

@Composable
private fun SearchBox(query: String, onQueryChange: (String) -> Unit, onClearClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(Color(0xFFF6F7FB), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("搜索", color = Color(0xFF7D8190), style = MaterialTheme.typography.bodySmall)
        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF222832)),
            modifier = Modifier.weight(1f),
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (query.isBlank()) Text("搜索标题、正文、标签或拼音", color = Color(0xFF8B90A0), style = MaterialTheme.typography.bodyMedium)
                    innerTextField()
                }
            },
        )
        if (query.isNotEmpty()) {
            Text(text = "清除", modifier = Modifier.clickable(onClick = onClearClick), color = Color(0xFF3D6BFF), style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun FilterBar(
    selectedFilter: String,
    pinnedHomeTag: Tag?,
    onTagPanelClick: () -> Unit,
    onFilterSelected: (String) -> Unit,
) {
    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(onClick = onTagPanelClick, shape = RoundedCornerShape(16.dp), color = Color(0xFFFFE3A1), tonalElevation = 1.dp) {
            Text(text = "标签", modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = Color(0xFF604410), maxLines = 1)
        }
        MAIN_FILTERS.forEach { filter ->
            FilterPill(text = filter, selected = selectedFilter == filter, onClick = { onFilterSelected(filter) })
        }
        pinnedHomeTag?.let { tag ->
            FilterPill(text = "#${tag.name}", selected = selectedFilter == tag.filterValue(), onClick = { onFilterSelected(tag.filterValue()) })
        }
    }
}

@Composable
private fun FilterPill(text: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) Color(0xFFEDE0FF) else Color.White,
        border = BorderStroke(1.dp, if (selected) Color.Transparent else Color(0xFF535864)),
        tonalElevation = if (selected) 1.dp else 0.dp,
    ) {
        Text(text = text, modifier = Modifier.padding(horizontal = 18.dp, vertical = 9.dp), style = MaterialTheme.typography.labelLarge, color = Color(0xFF222832), maxLines = 1)
    }
}

@Composable
private fun SummaryBar(
    scopeLabel: String,
    searchQuery: String,
    resultCount: Int,
    gridColumns: Int,
    textColor: Color,
    onToggleColumns: () -> Unit,
) {
    val text = if (searchQuery.isBlank()) "$scopeLabel · $resultCount 条" else "$scopeLabel · 搜索“${searchQuery.trim()}” · $resultCount 条"
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Surface(onClick = onToggleColumns, shape = RoundedCornerShape(12.dp), color = Color(0xFFF6F7FB)) {
            Text(if (gridColumns == 1) "双列" else "单列", modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium, color = Color(0xFF3D6BFF))
        }
    }
}

@Composable
private fun SelectionFloatingBar(
    selectedNotes: List<Note>,
    totalVisibleCount: Int,
    allVisibleSelected: Boolean,
    isDeletedScope: Boolean,
    isArchivedScope: Boolean,
    onSelectAllClick: () -> Unit,
    onExitClick: () -> Unit,
    onSetPinned: (Boolean) -> Unit,
    onSetDone: (Boolean) -> Unit,
    onSetArchived: (Boolean) -> Unit,
    onAddTag: () -> Unit,
    onSoftDelete: () -> Unit,
    onRestore: () -> Unit,
    onPermanentDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedCount = selectedNotes.size
    val hasSelection = selectedCount > 0
    val allPinned = hasSelection && selectedNotes.all { it.pinned }
    val allTodo = hasSelection && selectedNotes.all { it.type == NoteType.Todo }
    val allDone = allTodo && selectedNotes.all { it.isDone }
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = Color(0xFFF6F7FB), tonalElevation = 8.dp, shadowElevation = 12.dp) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "已选择 $selectedCount 条", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = Color(0xFF222832))
                Text(text = if (allVisibleSelected) "全不选" else "全选", modifier = Modifier.clickable(enabled = totalVisibleCount > 0, onClick = onSelectAllClick).padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF3D6BFF), style = MaterialTheme.typography.labelLarge)
                Text(text = "退出", modifier = Modifier.clickable(onClick = onExitClick).padding(horizontal = 8.dp, vertical = 4.dp), color = Color(0xFF6B7280), style = MaterialTheme.typography.labelLarge)
            }
            Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    isDeletedScope -> {
                        CompactActionPill("恢复", enabled = hasSelection, onClick = onRestore)
                        CompactActionPill("彻底删除", danger = true, enabled = hasSelection, onClick = onPermanentDelete)
                    }
                    isArchivedScope -> {
                        CompactActionPill("取消归档", enabled = hasSelection, onClick = { onSetArchived(false) })
                        CompactActionPill("删除", danger = true, enabled = hasSelection, onClick = onSoftDelete)
                    }
                    else -> {
                        CompactActionPill(if (allPinned) "取消置顶" else "置顶", enabled = hasSelection, onClick = { onSetPinned(!allPinned) })
                        if (allTodo) CompactActionPill(if (allDone) "取消完成" else "已完成", enabled = hasSelection, onClick = { onSetDone(!allDone) })
                        CompactActionPill("归档", enabled = hasSelection, onClick = { onSetArchived(true) })
                        CompactActionPill("加标签", enabled = hasSelection, onClick = onAddTag)
                        CompactActionPill("删除", danger = true, enabled = hasSelection, onClick = onSoftDelete)
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactActionPill(text: String, danger: Boolean = false, enabled: Boolean = true, onClick: () -> Unit) {
    Surface(
        onClick = { if (enabled) onClick() },
        shape = RoundedCornerShape(14.dp),
        color = when {
            !enabled -> Color(0xFFE5E7EB)
            danger -> Color(0xFFFFE4E6)
            else -> Color.White
        },
        border = BorderStroke(1.dp, if (danger) Color(0xFFFDA4AF) else Color(0xFFE5E7EB)),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            color = when {
                !enabled -> Color(0xFF9CA3AF)
                danger -> Color(0xFFB42318)
                else -> Color(0xFF344054)
            },
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

@Composable
private fun EmptyNotes(
    selectedFilter: String,
    selectedTagName: String?,
    searchQuery: String,
    onCreateClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth().background(Color(0xFFF6F7FB), RoundedCornerShape(26.dp)).padding(24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val isSearching = searchQuery.isNotBlank()
        val title = when {
            selectedFilter == FILTER_DELETED && isSearching -> "最近删除中没有相关便签"
            selectedFilter == FILTER_DELETED -> "最近删除为空"
            selectedFilter == FILTER_ARCHIVED && isSearching -> "已归档中没有相关便签"
            selectedFilter == FILTER_ARCHIVED -> "已归档为空"
            isSearching -> "没有找到相关便签"
            selectedTagName != null -> "# $selectedTagName 下还没有便签"
            else -> "还没有便签"
        }
        val description = when {
            selectedFilter == FILTER_DELETED -> "软删除的便签会出现在这里。"
            selectedFilter == FILTER_ARCHIVED -> "归档便签不会出现在首页或标签筛选里。"
            isSearching -> "试试删减关键词，或切换筛选范围。"
            selectedTagName != null -> "新建便签会自动带上这个标签。"
            else -> "先记下一条想法、待办或临时信息。"
        }
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(description, color = Color(0xFF6B7280))
        if (selectedFilter != FILTER_DELETED && selectedFilter != FILTER_ARCHIVED) {
            Button(onClick = onCreateClick, shape = RoundedCornerShape(18.dp)) {
                Text(if (selectedTagName != null) "使用 #$selectedTagName 创建" else "创建第一条便签")
            }
        }
    }
}

@Composable
private fun TagDrawer(
    selectedFilter: String,
    pinnedHomeTagId: Long?,
    tags: List<Tag>,
    deletedCount: Int,
    archivedCount: Int,
    backgroundColor: Color,
    backgroundHex: String,
    onFilterSelected: (String) -> Unit,
    onPinnedHomeTagChange: (Long?) -> Unit,
    onCreateTag: (String) -> Unit,
    onRenameTag: (Long, String) -> Unit,
    onDeleteTag: (Tag) -> Unit,
) {
    var newTagName by remember { mutableStateOf("") }
    var editingTagId by remember { mutableStateOf<Long?>(null) }
    var editingName by remember { mutableStateOf("") }
    var deleteConfirmTag by remember { mutableStateOf<Tag?>(null) }
    val dark = backgroundHex.isDarkHex()
    val primaryText = if (dark) Color(0xFFF9FAFB) else Color(0xFF4A3A20)
    val secondaryText = if (dark) Color(0xFFD0D5DD) else Color(0xFF8A7651)
    val selectedBg = if (dark) Color(0xFF374151) else Color(0xFFFFD978)
    val fieldBg = if (dark) Color(0xFF1F2937) else Color.White.copy(alpha = 0.58f)

    Surface(
        modifier = Modifier.fillMaxHeight().width(286.dp),
        shape = RoundedCornerShape(topEnd = 28.dp, bottomEnd = 28.dp),
        color = backgroundColor,
        tonalElevation = 10.dp,
        shadowElevation = 12.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(start = 18.dp, end = 16.dp, top = 22.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            DRAWER_FIXED_FILTERS.forEach { item ->
                TagDrawerRow(
                    text = when (item) {
                        FILTER_DELETED -> "最近删除${if (deletedCount > 0) "  $deletedCount" else ""}"
                        FILTER_ARCHIVED -> "已归档${if (archivedCount > 0) "  $archivedCount" else ""}"
                        else -> item
                    },
                    selected = selectedFilter == item,
                    selectedBg = selectedBg,
                    textColor = primaryText,
                    onClick = { onFilterSelected(item) },
                )
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = primaryText),
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .background(fieldBg, RoundedCornerShape(14.dp))
                        .padding(horizontal = 12.dp),
                    decorationBox = { innerTextField ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (newTagName.isBlank()) Text("新建标签", color = secondaryText, style = MaterialTheme.typography.bodyMedium)
                            innerTextField()
                        }
                    },
                )
                Button(
                    onClick = {
                        val name = newTagName.trim()
                        if (name.isNotEmpty()) {
                            onCreateTag(name)
                            newTagName = ""
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(42.dp),
                ) { Text("创建") }
            }

            Text("已创建标签", style = MaterialTheme.typography.labelLarge, color = secondaryText)
            if (tags.isEmpty()) {
                Text("暂无标签。", style = MaterialTheme.typography.bodySmall, color = secondaryText)
            } else {
                LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(tags, key = { it.id }) { tag ->
                        if (editingTagId == tag.id) {
                            Column(modifier = Modifier.fillMaxWidth().background(fieldBg, RoundedCornerShape(16.dp)).padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedTextField(value = editingName, onValueChange = { editingName = it }, label = { Text("重命名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Surface(
                                        onClick = {
                                            val name = editingName.trim()
                                            if (name.isNotEmpty()) onRenameTag(tag.id, name)
                                            editingTagId = null
                                            editingName = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = selectedBg,
                                    ) { Text("保存", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = primaryText) }
                                    Surface(
                                        onClick = {
                                            editingTagId = null
                                            editingName = ""
                                        },
                                        shape = RoundedCornerShape(12.dp),
                                        color = fieldBg,
                                    ) { Text("取消", modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = primaryText) }
                                }
                            }
                        } else {
                            TagManageRow(
                                tag = tag,
                                selected = selectedFilter == tag.filterValue(),
                                pinnedToHome = pinnedHomeTagId == tag.id,
                                selectedBg = selectedBg,
                                textColor = primaryText,
                                actionColor = if (dark) Color(0xFFFDE68A) else Color(0xFF5E4100),
                                onSelect = { onFilterSelected(tag.filterValue()) },
                                onRename = {
                                    editingTagId = tag.id
                                    editingName = tag.name
                                },
                                onToggleHomePin = { onPinnedHomeTagChange(if (pinnedHomeTagId == tag.id) null else tag.id) },
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
                ) { Text("删除") }
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
private fun TagDrawerRow(text: String, selected: Boolean, selectedBg: Color, textColor: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) selectedBg else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = text, modifier = Modifier.weight(1f), color = textColor, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
        if (selected) Text("ok", color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TagManageRow(
    tag: Tag,
    selected: Boolean,
    pinnedToHome: Boolean,
    selectedBg: Color,
    textColor: Color,
    actionColor: Color,
    onSelect: () -> Unit,
    onRename: () -> Unit,
    onToggleHomePin: () -> Unit,
    onDelete: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) selectedBg else Color.Transparent, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "# ${tag.name}", modifier = Modifier.weight(1f).clickable(onClick = onSelect), color = textColor, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (selected) Text("ok", color = textColor, fontWeight = FontWeight.SemiBold)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text = if (pinnedToHome) "取消固定" else "固定首页", modifier = Modifier.clickable(onClick = onToggleHomePin), color = actionColor, style = MaterialTheme.typography.labelMedium)
            Text(text = "重命名", modifier = Modifier.clickable(onClick = onRename), color = actionColor, style = MaterialTheme.typography.labelMedium)
            Text(text = "删除", modifier = Modifier.clickable(onClick = onDelete), color = Color(0xFFB42318), style = MaterialTheme.typography.labelMedium)
        }
    }
}

private fun Tag.filterValue(): String = "$TAG_PREFIX$id"

private fun String.selectedTagId(): Long? = if (startsWith(TAG_PREFIX)) removePrefix(TAG_PREFIX).toLongOrNull() else null

private fun String.labelFor(selectedTag: Tag?): String = when (this) {
    FILTER_ARCHIVED -> FILTER_ARCHIVED
    FILTER_DELETED -> FILTER_DELETED
    else -> if (selectedTag != null) "# ${selectedTag.name}" else this
}

private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

private fun List<Note>.filteredAndSortedBy(query: String): List<Note> {
    val normalizedQuery = query.toSearchToken()
    if (normalizedQuery.isBlank()) return sortedWith(defaultNoteComparator())
    return mapNotNull { note -> note.searchScore(normalizedQuery)?.let { score -> note to score } }
        .sortedWith(
            compareByDescending<Pair<Note, Int>> { it.second }
                .thenByDescending { it.first.pinned }
                .thenByDescending { it.first.updatedAt }
                .thenByDescending { it.first.id },
        )
        .map { it.first }
}

private fun Note.searchScore(query: String): Int? {
    val titleForms = title.searchForms()
    val contentForms = content.searchForms()
    val tagForms = tags.flatMap { it.name.searchForms() }
    return when {
        titleForms.any { it == query } -> 1000
        titleForms.any { it.contains(query) } -> 900
        tagForms.any { it == query } -> 820
        tagForms.any { it.contains(query) } -> 760
        contentForms.any { it.contains(query) } -> 620
        else -> null
    }
}

private fun String.searchForms(): Set<String> {
    val raw = toSearchToken()
    val fullPinyin = toPinyin(full = true).toSearchToken()
    val initials = toPinyin(full = false).toSearchToken()
    return setOf(raw, fullPinyin, initials).filter { it.isNotBlank() }.toSet()
}

private fun String.toSearchToken(): String = lowercase()
    .replace(Regex("[\\s,，、#。！？!?:：;；/\\-_.]+"), "")

private fun String.toPinyin(full: Boolean): String = buildString {
    this@toPinyin.forEach { char ->
        val pinyin = char.toPinyinSyllable()
        if (pinyin != null) append(if (full) pinyin else pinyin.first()) else append(char.lowercaseChar())
    }
}

private fun Char.toPinyinSyllable(): String? = when (this) {
    '王' -> "wang"
    '总' -> "zong"
    '客' -> "ke"
    '户' -> "hu"
    '屏' -> "ping"
    '幕' -> "mu"
    '校' -> "xiao"
    '色' -> "se"
    '记' -> "ji"
    '录' -> "lu"
    '硬' -> "ying"
    '件' -> "jian"
    '手' -> "shou"
    '柄' -> "bing"
    '测' -> "ce"
    '试' -> "shi"
    '便' -> "bian"
    '携' -> "xie"
    '到' -> "dao"
    '货' -> "huo"
    '清' -> "qing"
    '点' -> "dian"
    '生' -> "sheng"
    '活' -> "huo"
    '联' -> "lian"
    '系' -> "xi"
    '明' -> "ming"
    '天' -> "tian"
    '上' -> "shang"
    '午' -> "wu"
    '十' -> "shi"
    '确' -> "que"
    '认' -> "ren"
    '报' -> "bao"
    '价' -> "jia"
    '标' -> "biao"
    '题' -> "ti"
    '正' -> "zheng"
    '文' -> "wen"
    '签' -> "qian"
    '归' -> "gui"
    '档' -> "dang"
    '置' -> "zhi"
    '顶' -> "ding"
    '待' -> "dai"
    '办' -> "ban"
    '完' -> "wan"
    '成' -> "cheng"
    '删' -> "shan"
    '除' -> "chu"
    '新' -> "xin"
    '建' -> "jian"
    '想' -> "xiang"
    '法' -> "fa"
    '信' -> "xin"
    '息' -> "xi"
    '临' -> "lin"
    '时' -> "shi"
    else -> null
}

private fun defaultNoteComparator(): Comparator<Note> = compareByDescending<Note> { it.pinned }
    .thenByDescending { it.updatedAt }
    .thenByDescending { it.id }

private fun colorFromHex(hex: String, fallback: Color): Color = runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(fallback)

private fun String.isDarkHex(): Boolean = runCatching {
    val color = AndroidColor.parseColor(this)
    val r = AndroidColor.red(color)
    val g = AndroidColor.green(color)
    val b = AndroidColor.blue(color)
    (0.299 * r + 0.587 * g + 0.114 * b) < 110.0
}.getOrDefault(false)
