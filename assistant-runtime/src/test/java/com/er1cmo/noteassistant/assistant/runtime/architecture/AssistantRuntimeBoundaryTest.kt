package com.er1cmo.noteassistant.assistant.runtime.architecture

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class AssistantRuntimeBoundaryTest {
    @Test
    fun runtimeDoesNotImportForbiddenNotesImplementationBoundaries() {
        val sourceRoot = File("src/main/java")
        val forbidden = listOf(
            "NoteCommandService",
            "notes.data",
            "androidx.room",
            "RoomDatabase",
            "@Dao",
            "NoteRepositoryImpl",
        )
        val offenders = sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                val relative = file.relativeTo(sourceRoot).path
                val text = file.readText()
                forbidden.mapNotNull { token ->
                    if (text.contains(token)) "$relative -> $token" else null
                }
            }
            .toList()

        assertFalse("assistant-runtime must stay protocol-only; offenders=$offenders", offenders.isNotEmpty())
    }
}
