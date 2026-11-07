package com.umfluten.lighttimeclouds.config

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.SettingsFile
import java.time.LocalDate

/** How the last export or import went. */
private class FileResult(val message: String, val failed: Boolean)

/**
 * Exports this widget's settings with all places to a file of the user's choice, or imports such a
 * file. The system's file dialogs grant access to just that file, so no permission is needed.
 */
@Composable
internal fun SettingsFileSection(widgetId: Int, viewModel: ConfigViewModel) {
    val resources = LocalResources.current
    var result by remember { mutableStateOf<FileResult?>(null) }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(JSON_TYPE)) { uri ->
        if (uri != null) {
            viewModel.exportTo(uri) { written ->
                result = if (written) {
                    FileResult(resources.getString(R.string.settings_file_exported), failed = false)
                } else {
                    FileResult(resources.getString(R.string.settings_file_export_failed), failed = true)
                }
            }
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            viewModel.importFrom(uri) { places ->
                result = if (places != null) {
                    FileResult(resources.getQuantityString(R.plurals.settings_file_imported, places, places), failed = false)
                } else {
                    FileResult(resources.getString(R.string.settings_file_import_failed), failed = true)
                }
            }
        }
    }

    Section(stringResource(R.string.section_settings_file)) {
        Hint(stringResource(R.string.settings_file_hint))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { exporter.launch(SettingsFile.fileName(LocalDate.now(), widgetId)) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_file_export))
            }
            OutlinedButton(onClick = { importer.launch(IMPORT_TYPES) }, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_file_import))
            }
        }
        result?.let {
            Text(
                it.message,
                style = MaterialTheme.typography.bodySmall,
                color = if (it.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val JSON_TYPE = "application/json"

/** Not every file provider knows the type of a .json file. */
private val IMPORT_TYPES = arrayOf(JSON_TYPE, "text/*", "application/octet-stream")
