package com.er1cmo.noteassistant.assistant.tools.notes

import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistant.bridge.UiCommandBus
import com.er1cmo.noteassistant.assistant.mcpbase.ToolArgumentParser
import com.er1cmo.noteassistant.notes.domain.model.Note
import com.er1cmo.noteassistant.notes.domain.model.NoteType
import com.er1cmo.noteassistant.notes.domain.usecase.NoteUseCases
import javax.inject.Inject

class NotesListTodosTool @Inject constructor(
    noteUseCases: NoteUseCases,
    uiCommandBus: UiCommandBus,
) : AbstractNoteListTool(noteUseCases, uiCommandBus) {
    override val name: String = "notes.list_todos"
    override val listKind: String = "todos"
    override val title: String = "列出全部待办（包括未完成和已完成），并同步显示待办列表"
    override suspend fun loadNotes(): List<Note> = activeNotes().filter { it.type == NoteType.Todo }
    override fun buildUiCommand(notes: List<Note>, parser: ToolArgumentParser): UiCommand = UiCommand.ShowTodos
    override fun uiEffectName(): String = "show_todos"
    override fun spokenLabel(): String = "全部待办"
}
