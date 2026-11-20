package com.umfluten.lighttimeclouds.config

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R

/** The app bar's menu on every screen: where the app comes from, what it is, and how to support it. */
@Composable
internal fun AppMenu() {
    var expanded by remember { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    fun open(url: String) {
        expanded = false
        uriHandler.openIfPossible(url)
    }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_docs)) }, onClick = { open(REPO_URL) })
            DropdownMenuItem(
                text = { Text(stringResource(R.string.menu_about)) },
                onClick = {
                    expanded = false
                    showAbout = true
                },
            )
            DropdownMenuItem(text = { Text(stringResource(R.string.menu_support)) }, onClick = { open(SUPPORT_URL) })
        }
    }
    if (showAbout) {
        AboutDialog(onLicenses = { uriHandler.openIfPossible(LICENSES_URL) }, onDismiss = { showAbout = false })
    }
}

/** Name, version, license and the sources the app builds on. */
@Composable
private fun AboutDialog(onLicenses: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version = remember { versionName(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.app_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(R.string.about_version, version),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(stringResource(R.string.about_text))
                Text(
                    stringResource(R.string.about_credits, stringResource(R.string.weather_attribution)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_ok)) } },
        dismissButton = { TextButton(onClick = onLicenses) { Text(stringResource(R.string.about_licenses)) } },
    )
}

/** Opens [url] in the browser; without one installed, nothing can show the page, so nothing happens. */
private fun UriHandler.openIfPossible(url: String) {
    try {
        openUri(url)
    } catch (_: IllegalArgumentException) {
        // No app handles web links.
    }
}

private fun versionName(context: Context): String {
    val packageManager = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(context.packageName, 0)
    }
    return info.versionName.orEmpty()
}

private const val REPO_URL = "https://github.com/aqja/lighttimeclouds"

/** The licenses of ported and adopted parts of other projects. */
private const val LICENSES_URL = "$REPO_URL/blob/main/THIRD_PARTY_NOTICES.md"

/** The tip page on Liberapay; a plain link, so the app loads nothing from there itself. */
private const val SUPPORT_URL = "https://liberapay.com/aqja/donate"
