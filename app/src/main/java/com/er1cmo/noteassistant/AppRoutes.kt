package com.er1cmo.noteassistant

sealed class AppRoute(val route: String) {
    data object Notes : AppRoute("notes")
    data object Editor : AppRoute("editor")
    data object Settings : AppRoute("settings")
}
