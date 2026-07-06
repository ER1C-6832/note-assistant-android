package com.er1cmo.noteassistant.notes.ui.list

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import com.er1cmo.noteassistant.notes.ui.components.NoteCard
import com.er1cmo.noteassistant.notes.ui.components.StatusPill

@Composable
fun NoteListRoute(
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit,
    viewModel: NoteListViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsState()
    NoteListScreen(
        state = state,
        onCreateClick = onCreateClick,
        onSettingsClick = onSettingsClick,
    )
}

@Composable
fun NoteListScreen(
    state: NoteListState,
    onCreateClick: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    Surface(color = Color(0xFFF3F6FB), modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 22.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("小智便签", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    Text("便签 App 与语音助手", style = MaterialTheme.typography.bodySmall, color = Color(0xFF697386))
                }
                StatusPill(text = state.assistantState.statusText)
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White, RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("○  搜索便签……", color = Color(0xFF6B7280), style = MaterialTheme.typography.bodyMedium)
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.notes, key = { it.id }) { note ->
                    NoteCard(note = note, onClick = {})
                }
                item { Spacer(Modifier.height(88.dp)) }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onSettingsClick, shape = RoundedCornerShape(16.dp)) {
                    Text("设置")
                }
                Spacer(modifier = Modifier.weight(1f))
                OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp)) {
                    Text("●  语音")
                }
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4F78FF)),
                    modifier = Modifier.padding(start = 10.dp),
                ) {
                    Text("+ 新建便签")
                }
            }
        }
    }
}
