package com.er1cmo.noteassistant.assistant.tools.notes

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesCreateSafetyContractTest {
    @Test
    fun createToolFailsClosedForArchiveAsrAndDuplicateTitles() {
        val source = Files.readString(
            projectRoot().resolve(
                "assistant-tools/src/main/java/com/er1cmo/noteassistant/assistant/tools/notes/NotesCreateTool.kt",
            ),
        )
        assertTrue(source.contains("possible_archive_asr_confusion"))
        assertTrue(source.contains("duplicate_title_requires_explicit_create"))
        assertTrue(source.contains("allow_duplicate"))
        assertTrue(source.contains("confirm_create"))
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }
}
