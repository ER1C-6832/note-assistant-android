package com.er1cmo.noteassistant

sealed class AppRoute(val route: String) {
    data object Splash : AppRoute("splash")
    data object Notes : AppRoute("notes")
    data object Editor : AppRoute("editor")
    data object EditNote : AppRoute("editor/{noteId}") {
        fun createRoute(noteId: Long): String = "editor/$noteId"
    }
    data object Settings : AppRoute("settings")
}
