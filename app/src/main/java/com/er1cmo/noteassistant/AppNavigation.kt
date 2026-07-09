package com.er1cmo.noteassistant

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.er1cmo.noteassistant.assistant.runtime.toolcall.ToolCallUiStatus
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
    val toolCallState by uiCommandViewModel.toolCallState.collectAsState()
    var pendingConfirmationDialog by remember { mutableStateOf<UiCommand.ShowConfirmation?>(null) }
    var lastAutoConfirmationId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(navController) {
        uiCommandViewModel.commands.collect { command ->
            when (command) {
                is UiCommand.OpenNote -> navController.navigate(AppRoute.Detail.createRoute(command.noteId))
                is UiCommand.ShowMessage -> Unit
                is UiCommand.ShowConfirmation -> {
                    if (command.confirmationId != lastAutoConfirmationId) {
                        pendingConfirmationDialog = command
                        lastAutoConfirmationId = command.confirmationId
                    }
                }
                is UiCommand.ShowSearch -> navController.navigateToNotesRoot()
                is UiCommand.ShowTag -> navController.navigateToNotesRoot()
                UiCommand.ShowNoteList -> navController.navigateToNotesRoot()
                UiCommand.ShowArchive -> navController.navigateToNotesRoot()
                UiCommand.ShowTrash -> navController.navigateToNotesRoot()
            }
        }
    }

    LaunchedEffect(toolCallState.status, toolCallState.confirmationId, toolCallState.updatedAtMillis) {
        val confirmationId = toolCallState.confirmationId.orEmpty()
        if (
            toolCallState.status == ToolCallUiStatus.RequiresConfirmation &&
            confirmationId.isNotBlank() &&
            confirmationId != lastAutoConfirmationId
        ) {
            pendingConfirmationDialog = UiCommand.ShowConfirmation(
                confirmationId = confirmationId,
                title = confirmationTitle(toolCallState.toolName),
                message = confirmationMessage(
                    toolName = toolCallState.toolName,
                    summary = toolCallState.confirmationSummary,
                    detail = toolCallState.detail,
                    fallback = toolCallState.message,
                ),
            )
            lastAutoConfirmationId = confirmationId
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
                lastAutoConfirmationId = confirmation.confirmationId
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
                        lastAutoConfirmationId = confirmation.confirmationId
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
                        lastAutoConfirmationId = confirmation.confirmationId
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

private fun confirmationTitle(toolName: String?): String = when (toolName) {
    "notes.delete" -> "确认删除便签？"
    "notes.replace_content" -> "确认覆盖正文？"
    "notes.restore_revision" -> "确认恢复历史版本？"
    "notes.clear_done" -> "确认清理已完成？"
    "tags.bind" -> "确认替换标签？"
    "tags.delete" -> "确认删除标签？"
    "tags.rename" -> "确认重命名标签？"
    else -> "需要确认操作"
}

private fun confirmationMessage(
    toolName: String?,
    summary: String?,
    detail: String?,
    fallback: String,
): String {
    val primary = listOf(summary, detail, fallback)
        .firstOrNull { !it.isNullOrBlank() }
        .orEmpty()
    return when (toolName) {
        "notes.delete" -> primary.ifBlank { "这个操作会把便签移入最近删除。确认继续吗？" }
        "notes.replace_content" -> primary.ifBlank { "这个操作会覆盖便签正文。确认继续吗？" }
        "notes.restore_revision" -> primary.ifBlank { "这个操作会把便签恢复到历史版本。确认继续吗？" }
        "notes.clear_done" -> primary.ifBlank { "这个操作会批量处理已完成待办。确认继续吗？" }
        "tags.bind" -> primary.ifBlank { "这个操作会替换便签标签。确认继续吗？" }
        "tags.delete" -> primary.ifBlank { "这个操作会删除标签并影响关联便签。确认继续吗？" }
        "tags.rename" -> primary.ifBlank { "这个操作会重命名标签。确认继续吗？" }
        else -> primary.ifBlank { "请确认是否执行这个高风险操作。" }
    }
}
