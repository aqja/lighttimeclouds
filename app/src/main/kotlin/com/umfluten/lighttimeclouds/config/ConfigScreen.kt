package com.umfluten.lighttimeclouds.config

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.TapAction
import com.umfluten.lighttimeclouds.data.TemperatureDisplay
import com.umfluten.lighttimeclouds.data.TemperaturePosition
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetPrefsRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.OpenMeteo
import com.umfluten.lighttimeclouds.widget.WidgetIds

/**
 * Entry point of the config UI: edits [initialWidgetId] directly, or - when opened from the
 * launcher icon - the template (no widget placed), the only widget, or one picked from a list.
 */
@Composable
fun ConfigRoot(initialWidgetId: Int, onFinishedWithResult: (Int) -> Unit) {
    val context = LocalContext.current
    ConfigTheme {
        if (initialWidgetId != WidgetIds.NO_WIDGET_ID) {
            WidgetEditorScreen(widgetId = initialWidgetId, onDone = { onFinishedWithResult(initialWidgetId) })
        } else {
            var chosenWidgetId by remember { mutableStateOf<Int?>(null) }
            val activeIds = remember { WidgetIds.activeWidgetIds(context).toList() }
            val editId = chosenWidgetId ?: when (activeIds.size) {
                0 -> WidgetSettings.DEFAULT_WIDGET_ID
                1 -> activeIds.first()
                else -> null
            }
            if (editId != null) {
                WidgetEditorScreen(widgetId = editId, onDone = { onFinishedWithResult(editId) })
            } else {
                WidgetPickerScreen(widgetIds = activeIds, onPicked = { chosenWidgetId = it })
            }
        }
    }
}

@Composable
private fun WidgetPickerScreen(widgetIds: List<Int>, onPicked: (Int) -> Unit) {
    val context = LocalContext.current
    var settingsById by remember { mutableStateOf<Map<Int, WidgetSettings>>(emptyMap()) }
    LaunchedEffect(widgetIds) {
        val repository = WidgetPrefsRepository(context)
        settingsById = widgetIds.associateWith { repository.load(it) }
    }
    ConfigScaffold(subtitle = "Welches Widget möchtest du bearbeiten?") { padding ->
        LazyColumn(contentPadding = padding) {
            items(widgetIds) { id ->
                val settings = settingsById[id]
                ListItem(
                    headlineContent = { Text(settings?.place?.name ?: "Widget #$id") },
                    supportingContent = settings?.let { s ->
                        {
                            val mode = if (s.displayMode == DisplayMode.TWELVE_HOUR) "12 h" else "24 h"
                            Text("${s.place.zone.id} · $mode")
                        }
                    },
                    modifier = Modifier.clickable { onPicked(id) },
                )
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun WidgetEditorScreen(widgetId: Int, onDone: () -> Unit) {
    val application = LocalContext.current.applicationContext as Application
    val viewModel: ConfigViewModel = viewModel(factory = ConfigViewModel.Factory(application, widgetId))
    val loaded by viewModel.settings.collectAsState()
    val settings = loaded
    val subtitle = if (widgetId == WidgetSettings.DEFAULT_WIDGET_ID) "Vorlage für neue Widgets" else "Widget-Einstellungen"

    if (settings == null) {
        ConfigScaffold(subtitle = subtitle) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    ConfigScaffold(
        subtitle = subtitle,
        tabs = {
            PrimaryTabRow(selectedTabIndex = selectedTab) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("Einstellungen") })
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("Zeiten") })
            }
        },
        bottomBar = {
            if (selectedTab == 0) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.save(onDone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text("Speichern")
                    }
                }
            }
        },
    ) { padding ->
        if (selectedTab == 1) {
            TimesTab(settings, padding)
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                SettingsContent(widgetId, settings, viewModel)
            }
        }
    }
}

@Composable
private fun SettingsContent(widgetId: Int, settings: WidgetSettings, viewModel: ConfigViewModel) {
    if (widgetId == WidgetSettings.DEFAULT_WIDGET_ID) {
        Text(
            "Noch kein Widget auf dem Startbildschirm. Diese Einstellungen gelten für neu platzierte Widgets.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LocationSection(settings, viewModel)

    Section("Zifferblatt") {
        SegmentedChoice(
            options = listOf(DisplayMode.TWELVE_HOUR to "12 Stunden", DisplayMode.TWENTY_FOUR_HOUR to "24 Stunden"),
            selected = settings.displayMode,
            onSelect = { viewModel.update { s -> s.copy(displayMode = it) } },
        )
        Text("Tag/Nacht-Ring", style = MaterialTheme.typography.labelLarge)
        SegmentedChoice(
            options = listOf(RingStyle.SKY to "Himmel", RingStyle.GRADIENT to "Verlauf", RingStyle.BANDS to "Feste Farben"),
            selected = settings.ringStyle,
            onSelect = { viewModel.update { s -> s.copy(ringStyle = it) } },
        )
        if (settings.ringStyle == RingStyle.SKY) {
            Text("Horizont im Ring", style = MaterialTheme.typography.labelLarge)
            SegmentedChoice(
                options = listOf(SkyHorizon.INNER to "Innen", SkyHorizon.OUTER to "Außen"),
                selected = settings.skyHorizon,
                onSelect = { viewModel.update { s -> s.copy(skyHorizon = it) } },
            )
        }
        SwitchRow(
            label = "Minutenstriche",
            checked = settings.showMinuteTicks,
            onCheckedChange = { viewModel.update { s -> s.copy(showMinuteTicks = it) } },
        )
        SwitchRow(
            label = "Sonnenmittag und -mitternacht",
            checked = settings.showSolarMarks,
            onCheckedChange = { viewModel.update { s -> s.copy(showSolarMarks = it) } },
        )
        SwitchRow(
            label = "Sonnenauf- und -untergang",
            checked = settings.showSunriseSunsetMarks,
            onCheckedChange = { viewModel.update { s -> s.copy(showSunriseSunsetMarks = it) } },
        )
        SwitchRow(
            label = "Mond mit Bahn",
            checked = settings.showMoon,
            onCheckedChange = { viewModel.update { s -> s.copy(showMoon = it) } },
        )
        WeatherSetting(settings, viewModel)
    }

    if (settings.displayMode == DisplayMode.TWELVE_HOUR) {
        DayNightSwitchSection(settings, viewModel)
    }

    Section("Farbmodus") {
        Text(
            "Ziffern und Zeiger in den Systemfarben deines Hintergrundbilds, für einen hellen oder dunklen Hintergrund.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SegmentedChoice(
            options = listOf(
                ColorMode.AUTO to "Automatisch",
                ColorMode.LIGHT to "Hell",
                ColorMode.DARK to "Dunkel",
            ),
            selected = settings.colorMode,
            onSelect = { viewModel.update { s -> s.copy(colorMode = it) } },
        )
        if (settings.colorMode == ColorMode.AUTO) {
            Text(
                "Folgt dem Dunkelmodus des Systems.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    Section("Antippen öffnet") {
        SegmentedChoice(
            options = listOf(TapAction.SYSTEM_CLOCK to "Uhr-App", TapAction.OWN_APP to "Diese App"),
            selected = settings.tapAction,
            onSelect = { viewModel.update { s -> s.copy(tapAction = it) } },
        )
    }
}

/**
 * Weather ring switch. Some systems let the user withhold network access per app, so switching the
 * ring on asks for it where needed; while it is missing, a hint links to the app's system settings.
 */
@Composable
private fun WeatherSetting(settings: WidgetSettings, viewModel: ConfigViewModel) {
    val context = LocalContext.current
    var networkAllowed by remember { mutableStateOf(hasNetworkPermission(context)) }
    // Re-checked on return from the system settings.
    LifecycleResumeEffect(Unit) {
        networkAllowed = hasNetworkPermission(context)
        onPauseOrDispose {}
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        networkAllowed = granted
    }

    SwitchRow(
        label = "Wetter (Bewölkung und Niederschlag)",
        checked = settings.showWeather,
        onCheckedChange = { on ->
            viewModel.update { s -> s.copy(showWeather = on) }
            if (on && !networkAllowed) permissionLauncher.launch(Manifest.permission.INTERNET)
        },
    )
    if (!settings.showWeather) return
    Text(
        "Zusätzlicher Ring: grau je nach Bewölkung, bei klarem Himmel durchsichtig. " +
            "Niederschlag in Blau (Schnee violett), je dunkler, desto mehr; je deckender, desto wahrscheinlicher. " +
            "Die Vorhersage wird etwa stündlich aus dem Internet geladen; dazu werden die Koordinaten des Orts " +
            "an open-meteo.com übertragen. " +
            OpenMeteo.ATTRIBUTION + ".",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Text("Position des Wetterrings", style = MaterialTheme.typography.labelLarge)
    SegmentedChoice(
        options = listOf(
            WeatherRingPosition.INSIDE to "Innerhalb",
            WeatherRingPosition.OUTSIDE to "Ganz außen",
        ),
        selected = settings.weatherRingPosition,
        onSelect = { viewModel.update { s -> s.copy(weatherRingPosition = it) } },
    )
    Text("Temperatur je Stunde", style = MaterialTheme.typography.labelLarge)
    SegmentedChoice(
        options = listOf(
            TemperatureDisplay.NONE to "Aus",
            TemperatureDisplay.AIR to "Luft",
            TemperatureDisplay.APPARENT to "Gefühlt",
        ),
        selected = settings.temperatureDisplay,
        onSelect = { viewModel.update { s -> s.copy(temperatureDisplay = it) } },
    )
    if (settings.temperatureDisplay != TemperatureDisplay.NONE) {
        SegmentedChoice(
            options = listOf(
                TemperaturePosition.BETWEEN_NUMERALS to "Zwischen Ziffern",
                TemperaturePosition.OUTSIDE to "Außen",
            ),
            selected = settings.temperaturePosition,
            onSelect = { viewModel.update { s -> s.copy(temperaturePosition = it) } },
        )
    }
    if (!networkAllowed) {
        Text(
            "Die App hat keinen Netzwerkzugriff, daher kann keine Vorhersage geladen werden.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text("Berechtigungen öffnen") }
    }
}

private fun hasNetworkPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

@Composable
private fun DayNightSwitchSection(settings: WidgetSettings, viewModel: ConfigViewModel) {
    Section("Tag/Nacht-Umschaltung") {
        SegmentedChoice(
            options = listOf(
                SwitchStrategyType.FIXED_TIMES to "Feste Zeiten",
                SwitchStrategyType.TRAILING_OFFSET to "Mitlaufend",
            ),
            selected = settings.switchStrategyType,
            onSelect = { viewModel.update { s -> s.copy(switchStrategyType = it) } },
        )
        when (settings.switchStrategyType) {
            SwitchStrategyType.FIXED_TIMES -> {
                SwitchTimeRow(
                    label = "Umschaltzeit 1",
                    minutesOfDay = settings.switchTime1Minutes,
                    onPicked = { m -> viewModel.update { s -> s.copy(switchTime1Minutes = m) } },
                )
                SwitchTimeRow(
                    label = "Umschaltzeit 2",
                    minutesOfDay = settings.switchTime2Minutes,
                    onPicked = { m -> viewModel.update { s -> s.copy(switchTime2Minutes = m) } },
                )
            }
            SwitchStrategyType.TRAILING_OFFSET -> {
                Text("Vorlauf: ${settings.trailingOffsetMinutes} Minuten", style = MaterialTheme.typography.bodyLarge)
                Slider(
                    value = settings.trailingOffsetMinutes.toFloat(),
                    onValueChange = { v -> viewModel.update { s -> s.copy(trailingOffsetMinutes = v.toInt()) } },
                    valueRange = 0f..180f,
                    steps = 35,
                )
            }
        }
    }
}

/** Shows a switch time; tapping it opens a 24h time picker dialog. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchTimeRow(label: String, minutesOfDay: Int, onPicked: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(formatMinutes(minutesOfDay), style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable { showPicker = true },
    )
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = minutesOfDay / 60,
            initialMinute = minutesOfDay % 60,
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text("Abbrechen") }
            },
        )
    }
}

private fun formatMinutes(minutesOfDay: Int): String {
    val h = (minutesOfDay / 60).coerceIn(0, 23)
    val m = (minutesOfDay % 60).coerceIn(0, 59)
    return "%02d:%02d".format(h, m)
}
