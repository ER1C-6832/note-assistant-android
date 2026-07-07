package com.er1cmo.noteassistant

import android.net.Uri

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object Notes : AppRoute("notes")
    data object Detail : AppRoute("detail/{noteId}") {
        fun createRoute(noteId: Long): String = "detail/$noteId"
    }
    data object NoteColor : AppRoute("detail/{noteId}/color") {
        fun createRoute(noteId: Long): String = "detail/$noteId/color"
    }
    data object Editor : AppRoute("editor")
    data object EditorWithTag : AppRoute("editor/tag/{tag}") {
        fun createRoute(tag: String): String = "editor/tag/${Uri.encode(tag)}"
    }
    data object EditNote : AppRoute("editor/{noteId}") {
        fun createRoute(noteId: Long): String = "editor/$noteId"
    }
    data object Settings : AppRoute("settings")
}
