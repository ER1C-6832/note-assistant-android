package com.er1cmo.noteassistant

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.er1cmo.noteassistant.notes.ui.editor.NoteEditorRoute
import com.er1cmo.noteassistant.notes.ui.list.NoteListRoute
import com.er1cmo.noteassistant.notes.ui.settings.SettingsRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoute.Notes.route) {
        composable(AppRoute.Notes.route) {
            NoteListRoute(
                onCreateClick = { navController.navigate(AppRoute.Editor.route) },
                onSettingsClick = { navController.navigate(AppRoute.Settings.route) },
            )
        }
        composable(AppRoute.Editor.route) {
            NoteEditorRoute(onBackClick = { navController.popBackStack() })
        }
        composable(AppRoute.Settings.route) {
            SettingsRoute(onBackClick = { navController.popBackStack() })
        }
    }
}
