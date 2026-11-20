package com.umfluten.lighttimeclouds.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.SettingsFile
import java.time.LocalDate

/** The dialogs the settings menu opens. */
internal enum class SettingsDialog { NONE, SETTINGS_FILE, RESET }

/** The app bar's menu on the settings tab: what is rarely needed, kept out of the list of settings. */
@Composable
internal fun SettingsMenu(onOpen: (SettingsDialog) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.section_settings_file)) },
                onClick = {
                    expanded = false
                    onOpen(SettingsDialog.SETTINGS_FILE)
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.reset_action)) },
                onClick = {
                    expanded = false
                    onOpen(SettingsDialog.RESET)
                },
            )
        }
    }
}

/** Starts the system's file dialogs for exporting and importing the settings file. */
internal class SettingsFileActions(val export: () -> Unit, val import: () -> Unit)

/**
 * Exports this widget's settings with all places to a file of the user's choice, or imports such a
 * file, and reports how it went through [onResult]. The system's file dialogs grant access to just
 * that file, so no permission is needed. Must stay composed while a file dialog is open, or its
 * result is lost, so it lives outside the dialog that starts it.
 */
@Composable
internal fun rememberSettingsFileActions(widgetId: Int, viewModel: ConfigViewModel, onResult: (String) -> Unit): SettingsFileActions {
    val resources = LocalResources.current
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_TYPE)) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri) { written ->
                onResult(resources.getString(if (written) R.string.settings_file_exported else R.string.settings_file_export_failed))
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri) { places ->
                onResult(
                    if (places != null) {
                        resources.getQuantityString(R.plurals.settings_file_imported, places, places)
                    } else {
                        resources.getString(R.string.settings_file_import_failed)
                    },
                )
            }
        }
    }
    return SettingsFileActions(
        export = { exporter.launch(SettingsFile.fileName(LocalDate.now(), widgetId)) },
        import = { importer.launch(IMPORT_TYPES) },
    )
}

/** What the settings file holds, with buttons to export or import it. */
@Composable
internal fun SettingsFileDialog(actions: SettingsFileActions, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.section_settings_file)) },
        text = { Text(stringResource(R.string.settings_file_hint)) },
        // Two buttons only, so they fit on one line in every language; back or a tap outside cancels.
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                actions.export()
            }) { Text(stringResource(R.string.settings_file_export)) }
        },
        dismissButton = {
            TextButton(onClick = {
                onDismiss()
                actions.import()
            }) { Text(stringResource(R.string.settings_file_import)) }
        },
    )
}

/** Asks before putting every setting but the place back to its default; saving keeps it. */
@Composable
internal fun ResetDialog(onReset: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reset_action)) },
        text = { Text(stringResource(R.string.reset_hint)) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                onReset()
            }) { Text(stringResource(R.string.reset_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

private const val JSON_TYPE = "application/json"

/** Not every file provider knows the type of a .json file. */
private val IMPORT_TYPES = arrayOf(JSON_TYPE, "text/*", "application/octet-stream")
