package com.er1cmo.noteassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.er1cmo.noteassistant.assistant.bridge.UiCommand
import com.er1cmo.noteassistant.assistantui.AssistantEntryOverlay
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
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = currentBackStackEntry?.destination?.route
    var pendingConfirmationDialog by remember { mutableStateOf<UiCommand.ShowConfirmation?>(null) }
    val handledConfirmationIds = remember { mutableStateListOf<String>() }

    fun hasHandledConfirmation(confirmationId: String): Boolean {
        return confirmationId.isBlank() || handledConfirmationIds.contains(confirmationId)
    }

    fun markConfirmationHandled(confirmationId: String) {
        if (confirmationId.isBlank() || handledConfirmationIds.contains(confirmationId)) return
        handledConfirmationIds.add(confirmationId)
        while (handledConfirmationIds.size > MAX_HANDLED_CONFIRMATIONS) {
            handledConfirmationIds.removeAt(0)
        }
    }

    fun showConfirmationOnce(command: UiCommand.ShowConfirmation) {
        if (hasHandledConfirmation(command.confirmationId)) return
        pendingConfirmationDialog = command
        markConfirmationHandled(command.confirmationId)
    }

    LaunchedEffect(uiCommandViewModel) {
        uiCommandViewModel.commands.collect { command ->
            when (command) {
                is UiCommand.OpenNote -> navController.navigate(AppRoute.Detail.createRoute(command.noteId))
                is UiCommand.ShowMessage -> Unit
                is UiCommand.ShowConfirmation -> showConfirmationOnce(command)
                is UiCommand.ShowSearch -> navController.navigateToNotesRoot()
                is UiCommand.ShowTag -> navController.navigateToNotesRoot()
                UiCommand.ShowNoteList -> navController.navigateToNotesRoot()
                UiCommand.ShowArchive -> navController.navigateToNotesRoot()
                UiCommand.ShowTrash -> navController.navigateToNotesRoot()
            }
        }
    }

    LaunchedEffect(uiCommandViewModel) {
        uiCommandViewModel.confirmationRequests.collect { request ->
            showConfirmationOnce(
                UiCommand.ShowConfirmation(
                    confirmationId = request.confirmationId,
                    title = request.title,
                    message = request.message,
                ),
            )
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
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

        if (currentRoute != AppRoute.Splash.route) {
            AssistantEntryOverlay(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 18.dp),
            )
        }
    }

    pendingConfirmationDialog?.let { confirmation ->
        AlertDialog(
            onDismissRequest = {
                markConfirmationHandled(confirmation.confirmationId)
                pendingConfirmationDialog = null
            },
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
                        markConfirmationHandled(confirmation.confirmationId)
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
                        markConfirmationHandled(confirmation.confirmationId)
                        uiCommandViewModel.rejectPendingCommand(confirmation.confirmationId)
                        pendingConfirmationDialog = null
                    },
                ) {
                    Text("拒绝")
                }
            },
        )
    }
}

private fun NavController.navigateToNotesRoot() {
    navigate(AppRoute.Notes.route) {
        launchSingleTop = true
        restoreState = true
        popUpTo(AppRoute.Notes.route) {
            inclusive = false
            saveState = true
        }
    }
}

private const val MAX_HANDLED_CONFIRMATIONS = 24
