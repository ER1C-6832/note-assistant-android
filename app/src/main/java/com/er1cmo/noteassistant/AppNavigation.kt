package com.er1cmo.noteassistant

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.er1cmo.noteassistant.notes.ui.detail.NoteColorPickerRoute
import com.er1cmo.noteassistant.notes.ui.detail.NoteDetailRoute
import com.er1cmo.noteassistant.notes.ui.editor.NoteEditorRoute
import com.er1cmo.noteassistant.notes.ui.list.NoteListRoute
import com.er1cmo.noteassistant.notes.ui.settings.SettingsRoute
import com.er1cmo.noteassistant.notes.ui.splash.SplashRoute

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = AppRoute.Splash.route) {
        composable(AppRoute.Splash.route) {
            SplashRoute(
                onSplashFinished = {
                    navController.navigate(AppRoute.Notes.route) {
                        popUpTo(AppRoute.Splash.route) { inclusive = true }
                    }
                },
            )
        }
        composable(AppRoute.Notes.route) {
            NoteListRoute(
                onCreateClick = { initialTag ->
                    if (initialTag.isNullOrBlank()) {
                        navController.navigate(AppRoute.Editor.route)
                    } else {
                        navController.navigate(AppRoute.EditorWithTag.createRoute(initialTag))
                    }
                },
                onNoteClick = { noteId -> navController.navigate(AppRoute.Detail.createRoute(noteId)) },
                onSettingsClick = { navController.navigate(AppRoute.Settings.route) },
            )
        }
        composable(
            route = AppRoute.Detail.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) {
            NoteDetailRoute(
                onBackClick = { navController.popBackStack() },
                onColorClick = { noteId -> navController.navigate(AppRoute.NoteColor.createRoute(noteId)) },
            )
        }
        composable(
            route = AppRoute.NoteColor.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) {
            NoteColorPickerRoute(onBackClick = { navController.popBackStack() })
        }
        composable(AppRoute.Editor.route) {
            NoteEditorRoute(onBackClick = { navController.popBackStack() })
        }
        composable(
            route = AppRoute.EditorWithTag.route,
            arguments = listOf(navArgument("tag") { type = NavType.StringType }),
        ) {
            NoteEditorRoute(onBackClick = { navController.popBackStack() })
        }
        composable(
            route = AppRoute.EditNote.route,
            arguments = listOf(navArgument("noteId") { type = NavType.LongType }),
        ) {
            NoteEditorRoute(onBackClick = { navController.popBackStack() })
        }
        composable(AppRoute.Settings.route) {
            SettingsRoute(onBackClick = { navController.popBackStack() })
        }
    }
}
