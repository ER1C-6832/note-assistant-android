package com.er1cmo.noteassistant.notes.domain.command

import javax.inject.Inject

class NoteRiskPolicy @Inject constructor() {
    fun classify(input: CommandRiskInput): RiskLevel {
        val baseRisk = when (input.toolName) {
            ToolName.NotesSearch,
            ToolName.NotesListRecent,
            ToolName.TagsSearch,
            ToolName.UiOpenNote,
            -> RiskLevel.Low

            ToolName.NotesCreate,
            ToolName.NotesAppend,
            ToolName.NotesUpdateTitle,
            ToolName.NotesToggleDone,
            ToolName.NotesPin,
            ToolName.NotesArchive,
            ToolName.NotesRestore,
            ToolName.TagsCreate,
            -> RiskLevel.Medium

            ToolName.TagsBind -> when (input.tagBindMode) {
                TagBindMode.Replace -> RiskLevel.High
                TagBindMode.Add,
                TagBindMode.Remove,
                null,
                -> RiskLevel.Medium
            }

            ToolName.TagsRename -> if (input.linkedNoteCount > 0) RiskLevel.High else RiskLevel.Medium

            ToolName.NotesDelete,
            ToolName.NotesReplaceContent,
            ToolName.NotesRestoreRevision,
            ToolName.NotesClearDone,
            ToolName.TagsDelete,
            -> RiskLevel.High

            ToolName.Unsupported -> RiskLevel.High
        }
        return if (baseRisk == RiskLevel.Medium && input.affectedNoteCount > MEDIUM_BATCH_THRESHOLD) {
            RiskLevel.High
        } else {
            baseRisk
        }
    }

    fun requiresConfirmation(input: CommandRiskInput): Boolean = classify(input) == RiskLevel.High

    companion object {
        const val MEDIUM_BATCH_THRESHOLD = 5
    }
}
