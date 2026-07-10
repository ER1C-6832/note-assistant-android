package com.er1cmo.noteassistant

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.junit.Assert.assertTrue
import org.junit.Test

class Phase4UiCommandRegressionTest {
    @Test
    fun navigationClearsOneShotListCommandWhenLeavingHome() {
        val source = Files.readString(projectRoot().resolve("app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt"))
        assertTrue(source.contains("currentRoute != AppRoute.Notes.route"))
        val screen = Files.readString(projectRoot().resolve("notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt"))
        assertTrue(source.contains("noteListCommand = null"))
        assertTrue(source.contains("onExternalCommandConsumed"))
        assertTrue(screen.contains("onExternalCommandConsumed(it.sequence)"))
    }

    @Test
    fun todoAndDoneCommandsReachListUi() {
        val navigation = Files.readString(projectRoot().resolve("app/src/main/java/com/er1cmo/noteassistant/AppNavigation.kt"))
        val screen = Files.readString(projectRoot().resolve("notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt"))
        assertTrue(navigation.contains("UiCommand.ShowTodos"))
        assertTrue(navigation.contains("UiCommand.ShowDone"))
        assertTrue(screen.contains("NoteListExternalCommand.ShowTodos"))
        assertTrue(screen.contains("NoteListExternalCommand.ShowDone"))
    }

    @Test
    fun todoFilterIncludesCompletedAndIncompleteTodos() {
        val screen = Files.readString(projectRoot().resolve("notes-ui/src/main/java/com/er1cmo/noteassistant/notes/ui/list/NoteListScreen.kt"))
        assertTrue(screen.contains("selectedFilter == FILTER_TODO -> state.notes.filter { it.type == NoteType.Todo }"))
    }

    private fun projectRoot(): Path {
        var current = Paths.get("").toAbsolutePath()
        while (current.parent != null && !Files.exists(current.resolve("settings.gradle.kts"))) {
            current = current.parent
        }
        return current
    }
}
