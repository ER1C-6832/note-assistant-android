package com.er1cmo.noteassistant

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.notes.ui.detail.NoteColorPickerRoute
import com.er1cmo.noteassistant.notes.ui.detail.NoteDetailRoute
import com.er1cmo.noteassistant.notes.ui.editor.NoteEditorRoute
import com.er1cmo.noteassistant.notes.ui.list.NoteListRoute
import com.er1cmo.noteassistant.notes.ui.settings.SettingsRoute
import com.er1cmo.noteassistant.notes.ui.splash.SplashRoute

@Composable
fun AppNavigation(
    uiCommandViewModel: UiCommandViewModel = hiltViewModel(),
) {
    val navController = rememberNavController()
    var pendingConfirmationDialog by remember { mutableStateOf<UiCommand.ShowConfirmation?>(null) }

    LaunchedEffect(navController) {
        uiCommandViewModel.commands.collect { command ->
            when (command) {
                is UiCommand.OpenNote -> navController.navigate(AppRoute.Detail.createRoute(command.noteId))
                is UiCommand.ShowMessage -> Unit
                is UiCommand.ShowConfirmation -> pendingConfirmationDialog = command
            }
        }
    }

    pendingConfirmationDialog?.let { confirmation ->
        AlertDialog(
            onDismissRequest = { pendingConfirmationDialog = null },
            title = { Text(confirmation.title) },
            text = {
                Text(
                    buildString {
                        appendLine(confirmation.message)
                        appendLine()
                        append("confirmation_id=")
                        append(confirmation.confirmationId)
                    },
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        uiCommandViewModel.confirmPendingCommand(confirmation.confirmationId)
                        pendingConfirmationDialog = null
                    },
                ) {
                    Text("确认执行")
                }
            },
            dismissButton = {
                Button(
                    onClick = {
                        uiCommandViewModel.rejectPendingCommand(confirmation.confirmationId)
                        pendingConfirmationDialog = null
                    },
                ) {
                    Text("拒绝")
                }
            },
        )
    }

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
