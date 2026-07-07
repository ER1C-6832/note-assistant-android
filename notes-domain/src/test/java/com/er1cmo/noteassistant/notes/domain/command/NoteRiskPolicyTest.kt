package com.er1cmo.noteassistant.notes.domain.command

import org.junit.Assert.assertEquals
import org.junit.Test

class NoteRiskPolicyTest {
    private val policy = NoteRiskPolicy()

    @Test
    fun lowRiskReadToolsAreLow() {
        assertEquals(RiskLevel.Low, policy.classify(CommandRiskInput(toolName = ToolName.NotesSearch)))
        assertEquals(RiskLevel.Low, policy.classify(CommandRiskInput(toolName = ToolName.NotesListRecent)))
        assertEquals(RiskLevel.Low, policy.classify(CommandRiskInput(toolName = ToolName.TagsSearch)))
        assertEquals(RiskLevel.Low, policy.classify(CommandRiskInput(toolName = ToolName.UiOpenNote)))
    }

    @Test
    fun mediumSingleNoteMutationsAreMedium() {
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.NotesCreate, affectedNoteCount = 1)))
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.NotesAppend, affectedNoteCount = 1)))
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.NotesPin, affectedNoteCount = 1)))
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.NotesArchive, affectedNoteCount = 1)))
    }

    @Test
    fun highRiskToolsAreHigh() {
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.NotesDelete, affectedNoteCount = 1)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.NotesReplaceContent, affectedNoteCount = 1)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.NotesRestoreRevision, affectedNoteCount = 1)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.TagsDelete, affectedTagCount = 1)))
    }

    @Test
    fun tagsBindReplaceIsHighButAddRemoveAreMedium() {
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.TagsBind, affectedNoteCount = 1, tagBindMode = TagBindMode.Add)))
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.TagsBind, affectedNoteCount = 1, tagBindMode = TagBindMode.Remove)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.TagsBind, affectedNoteCount = 1, tagBindMode = TagBindMode.Replace)))
    }

    @Test
    fun mediumBatchAboveThresholdEscalatesToHigh() {
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.NotesArchive, affectedNoteCount = 5)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.NotesArchive, affectedNoteCount = 6)))
    }

    @Test
    fun tagsRenameWithLinkedNotesEscalatesToHigh() {
        assertEquals(RiskLevel.Medium, policy.classify(CommandRiskInput(toolName = ToolName.TagsRename, linkedNoteCount = 0)))
        assertEquals(RiskLevel.High, policy.classify(CommandRiskInput(toolName = ToolName.TagsRename, linkedNoteCount = 1)))
    }
}
