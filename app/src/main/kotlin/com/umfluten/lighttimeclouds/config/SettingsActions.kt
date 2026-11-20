package com.umfluten.lighttimeclouds.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.SettingsFile
import java.time.LocalDate

/** Starts the system's file dialogs for exporting and importing the settings file. */
internal class SettingsFileActions(val export: () -> Unit, val import: () -> Unit)

/**
 * Exports this widget's settings with all places to a file of the user's choice, or imports such a
 * file, and reports how it went through [onResult]. The system's file dialogs grant access to just
 * that file, so no permission is needed. Must stay composed while a file dialog is open, or its
 * result is lost, so it lives above the settings topic that starts it.
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

/**
 * The last topic of the settings tab, to back up and reset: export or import the settings file, or put the settings back
 * to their defaults, which [onReset] asks about first.
 */
@Composable
internal fun ManageSettingsSection(actions: SettingsFileActions, onReset: () -> Unit, expanded: Boolean, onToggle: () -> Unit) {
    val export = stringResource(R.string.settings_file_export)
    val import = stringResource(R.string.settings_file_import)
    Section(
        stringResource(R.string.section_manage_settings),
        summary = summaryOf(export, import, stringResource(R.string.reset_confirm)),
        expanded = expanded,
        onToggle = onToggle,
    ) {
        FieldLabel(stringResource(R.string.section_settings_file))
        Hint(stringResource(R.string.settings_file_hint))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = actions.export, modifier = Modifier.weight(1f)) { Text(export) }
            OutlinedButton(onClick = actions.import, modifier = Modifier.weight(1f)) { Text(import) }
        }
        OutlinedButton(onClick = onReset, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reset_action)) }
    }
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
