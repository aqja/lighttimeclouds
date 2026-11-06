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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.MoonStyle
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.SummaryPosition
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.TapAction
import com.umfluten.lighttimeclouds.data.TemperatureDisplay
import com.umfluten.lighttimeclouds.data.TemperaturePosition
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.SkyProjection
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import com.umfluten.lighttimeclouds.widget.WidgetIds
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

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
        settingsById = widgetIds.associateWith { ClockWidgetProvider.effectiveSettings(context, it) }
    }
    ConfigScaffold(subtitle = stringResource(R.string.picker_subtitle)) { padding ->
        LazyColumn(contentPadding = padding) {
            items(widgetIds) { id ->
                val settings = settingsById[id]
                ListItem(
                    headlineContent = { Text(settings?.place?.name ?: stringResource(R.string.picker_widget_fallback, id)) },
                    supportingContent = settings?.let { s ->
                        {
                            val mode = stringResource(
                                if (s.displayMode == DisplayMode.TWELVE_HOUR) R.string.picker_display_mode_12h else R.string.picker_display_mode_24h,
                            )
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
    val subtitle = stringResource(
        if (widgetId == WidgetSettings.DEFAULT_WIDGET_ID) R.string.editor_subtitle_template else R.string.editor_subtitle_widget,
    )

    if (settings == null) {
        ConfigScaffold(subtitle = subtitle) { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }

    // Tabs and pager share one state, so a tab can be chosen by tapping it or by swiping. The weather
    // tab only exists while the weather ring is on, since only then is a forecast downloaded. What
    // the place looks like today comes first, so the app opens on it; the widget's setup follows.
    val tabs = buildList {
        if (settings.showWeather) add(R.string.tab_weather)
        add(R.string.tab_times)
        add(R.string.tab_settings)
        add(R.string.tab_preview)
    }
    // Page count and page keys read the same state, so the pager never sees a count and keys from
    // two different tab lists while the weather tab comes or goes.
    val currentTabs by rememberUpdatedState(tabs)
    val pagerState = rememberPagerState { currentTabs.size }
    val currentTab = tabs.getOrNull(pagerState.currentPage)
    val scope = rememberCoroutineScope()
    ConfigScaffold(
        subtitle = subtitle,
        tabs = {
            PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } },
                        // Long titles shrink to one line rather than wrap, now that four tabs share the width.
                        text = {
                            Text(
                                stringResource(title),
                                maxLines = 1,
                                autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = LocalTextStyle.current.fontSize),
                            )
                        },
                    )
                }
            }
        },
        bottomBar = {
            if (currentTab == R.string.tab_settings) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.save(onDone) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }
        },
    ) { padding ->
        // Keyed by tab, so switching the weather ring on or off (which adds or removes the first tab)
        // keeps the settings tab in view instead of shifting to its neighbor. All tabs stay composed:
        // otherwise a tab is built only when it slides into view, which stalls a frame mid-swipe.
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            beyondViewportPageCount = MAX_TABS - 1,
            key = { page -> currentTabs[page] },
            verticalAlignment = Alignment.Top,
        ) { page ->
            when (currentTabs[page]) {
                R.string.tab_preview -> PreviewTab(settings, padding)
                R.string.tab_times -> TimesTab(settings, padding)
                R.string.tab_weather -> WeatherTab(settings, padding)
                else -> Column(
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
}

@Composable
private fun SettingsContent(widgetId: Int, settings: WidgetSettings, viewModel: ConfigViewModel) {
    if (widgetId == WidgetSettings.DEFAULT_WIDGET_ID) {
        Text(
            stringResource(R.string.template_hint),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }

    LocationSection(settings, viewModel)

    Section(stringResource(R.string.section_dial)) {
        SegmentedChoice(
            options = listOf(DisplayMode.TWELVE_HOUR to R.string.display_mode_12h, DisplayMode.TWENTY_FOUR_HOUR to R.string.display_mode_24h),
            selected = settings.displayMode,
            onSelect = { viewModel.update { s -> s.copy(displayMode = it) } },
        )
        RingSwitchSetting(settings, viewModel)
        FieldLabel(stringResource(R.string.ring_style_label))
        SegmentedChoice(
            options = listOf(
                RingStyle.SKY to R.string.ring_style_sky,
                RingStyle.BANDS to R.string.ring_style_bands,
            ),
            selected = settings.ringStyle,
            onSelect = { viewModel.update { s -> s.copy(ringStyle = it) } },
        )
        SwitchRow(
            label = stringResource(R.string.show_sun_in_ring),
            checked = settings.showSunInRing,
            onCheckedChange = { viewModel.update { s -> s.copy(showSunInRing = it) } },
        )
        SwitchRow(
            label = stringResource(R.string.show_moon_in_ring),
            checked = settings.showMoonInRing,
            onCheckedChange = { viewModel.update { s -> s.copy(showMoonInRing = it) } },
        )
        val bodiesInRing = settings.showSunInRing || settings.showMoonInRing
        if (bodiesInRing) {
            Hint(stringResource(R.string.bodies_in_ring_hint))
        }
        if (settings.showMoonInRing) {
            SwitchRow(
                label = stringResource(R.string.show_moon_path_in_ring),
                checked = settings.showMoonPathInRing,
                onCheckedChange = { viewModel.update { s -> s.copy(showMoonPathInRing = it) } },
            )
        }
        if (settings.ringStyle == RingStyle.SKY || bodiesInRing) {
            FieldLabel(stringResource(R.string.sky_horizon_label))
            SegmentedChoice(
                options = listOf(SkyHorizon.INNER to R.string.sky_horizon_inner, SkyHorizon.OUTER to R.string.sky_horizon_outer),
                selected = settings.skyHorizon,
                onSelect = { viewModel.update { s -> s.copy(skyHorizon = it) } },
            )
            SkyTopElevationSlider(settings.skyTopElevationDeg) { viewModel.update { s -> s.copy(skyTopElevationDeg = it) } }
        }
        SwitchRow(
            label = stringResource(R.string.show_minute_ticks),
            checked = settings.showMinuteTicks,
            onCheckedChange = { viewModel.update { s -> s.copy(showMinuteTicks = it) } },
        )
        SwitchRow(
            label = stringResource(R.string.show_solar_marks),
            checked = settings.showSolarMarks,
            onCheckedChange = { viewModel.update { s -> s.copy(showSolarMarks = it) } },
        )
        SwitchRow(
            label = stringResource(R.string.show_sunrise_sunset_marks),
            checked = settings.showSunriseSunsetMarks,
            onCheckedChange = { viewModel.update { s -> s.copy(showSunriseSunsetMarks = it) } },
        )
        SwitchRow(
            label = stringResource(R.string.show_moon),
            checked = settings.showMoon,
            onCheckedChange = { viewModel.update { s -> s.copy(showMoon = it) } },
        )
        if (settings.showMoon) {
            SegmentedChoice(
                options = listOf(MoonStyle.ON_HOUR_HAND to R.string.moon_style_on_hour_hand, MoonStyle.SEPARATE_HAND to R.string.moon_style_separate_hand),
                selected = settings.moonStyle,
                onSelect = { viewModel.update { s -> s.copy(moonStyle = it) } },
            )
            Hint(
                stringResource(
                    when (settings.moonStyle) {
                        MoonStyle.ON_HOUR_HAND -> R.string.moon_style_on_hour_hand_hint
                        MoonStyle.SEPARATE_HAND -> R.string.moon_style_separate_hand_hint
                    },
                ),
            )
            SwitchRow(
                label = stringResource(R.string.show_moon_compass),
                checked = settings.showMoonCompass,
                onCheckedChange = { viewModel.update { s -> s.copy(showMoonCompass = it) } },
            )
        }
        SwitchRow(
            label = stringResource(R.string.show_next_alarm),
            checked = settings.showNextAlarm,
            onCheckedChange = { viewModel.update { s -> s.copy(showNextAlarm = it) } },
        )
        if (settings.showNextAlarm) {
            Hint(stringResource(R.string.next_alarm_hint))
        }
        WeatherSetting(settings, viewModel)
    }

    Section(stringResource(R.string.section_color_mode)) {
        Hint(stringResource(R.string.color_mode_hint))
        SegmentedChoice(
            options = listOf(
                ColorMode.AUTO to R.string.color_mode_auto,
                ColorMode.LIGHT to R.string.color_mode_light,
                ColorMode.DARK to R.string.color_mode_dark,
            ),
            selected = settings.colorMode,
            onSelect = { viewModel.update { s -> s.copy(colorMode = it) } },
        )
        if (settings.colorMode == ColorMode.AUTO) {
            Hint(stringResource(R.string.color_mode_auto_hint))
        }
    }

    Section(stringResource(R.string.section_tap_action)) {
        SegmentedChoice(
            options = listOf(TapAction.SYSTEM_CLOCK to R.string.tap_action_system_clock, TapAction.OWN_APP to R.string.tap_action_own_app),
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
        label = stringResource(R.string.show_weather),
        checked = settings.showWeather,
        onCheckedChange = { on ->
            viewModel.update { s -> s.copy(showWeather = on) }
            if (on && !networkAllowed) permissionLauncher.launch(Manifest.permission.INTERNET)
        },
    )
    if (!settings.showWeather) return
    Hint(stringResource(R.string.weather_hint, stringResource(R.string.weather_attribution)))
    FieldLabel(stringResource(R.string.weather_ring_position_label))
    SegmentedChoice(
        options = listOf(
            WeatherRingPosition.INSIDE to R.string.weather_ring_inside,
            WeatherRingPosition.OUTSIDE to R.string.weather_ring_outside,
        ),
        selected = settings.weatherRingPosition,
        onSelect = { viewModel.update { s -> s.copy(weatherRingPosition = it) } },
    )
    FieldLabel(stringResource(R.string.temperature_label))
    SegmentedChoice(
        options = listOf(
            TemperatureDisplay.NONE to R.string.temperature_none,
            TemperatureDisplay.AIR to R.string.temperature_air,
            TemperatureDisplay.APPARENT to R.string.temperature_apparent,
        ),
        selected = settings.temperatureDisplay,
        onSelect = { viewModel.update { s -> s.copy(temperatureDisplay = it) } },
    )
    if (settings.temperatureDisplay != TemperatureDisplay.NONE) {
        SegmentedChoice(
            options = listOf(
                TemperaturePosition.BETWEEN_NUMERALS to R.string.temperature_between_numerals,
                TemperaturePosition.OUTSIDE to R.string.temperature_outside,
            ),
            selected = settings.temperaturePosition,
            onSelect = { viewModel.update { s -> s.copy(temperaturePosition = it) } },
        )
    }
    FieldLabel(stringResource(R.string.summary_position_label))
    SegmentedChoice(
        options = listOf(
            SummaryPosition.NONE to R.string.label_position_none,
            SummaryPosition.LEFT to R.string.summary_position_left,
            SummaryPosition.RIGHT to R.string.summary_position_right,
            SummaryPosition.BELOW to R.string.label_position_below,
        ),
        selected = settings.summaryPosition,
        onSelect = { viewModel.update { s -> s.copy(summaryPosition = it) } },
    )
    Hint(stringResource(R.string.summary_position_hint))
    if (!networkAllowed) {
        Text(
            stringResource(R.string.no_network_access),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
        TextButton(onClick = {
            context.startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
            )
        }) { Text(stringResource(R.string.open_permissions)) }
    }
}

private fun hasNetworkPermission(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.INTERNET) == PackageManager.PERMISSION_GRANTED

/**
 * Where the ring's shown window begins: at fixed times (two on the 12h dial, one on the 24h dial) or
 * at the hand; and how far before its end the ring fades out.
 */
@Composable
private fun RingSwitchSetting(settings: WidgetSettings, viewModel: ConfigViewModel) {
    val twelveHour = settings.displayMode == DisplayMode.TWELVE_HOUR
    FieldLabel(stringResource(if (twelveHour) R.string.day_night_switch_label else R.string.day_change_label))
    SegmentedChoice(
        options = listOf(
            SwitchStrategyType.FIXED_TIMES to R.string.switch_fixed_times,
            SwitchStrategyType.TRAILING to R.string.switch_trailing,
        ),
        selected = settings.switchStrategyType,
        onSelect = { viewModel.update { s -> s.copy(switchStrategyType = it) } },
    )
    when (settings.switchStrategyType) {
        SwitchStrategyType.FIXED_TIMES -> if (twelveHour) {
            SwitchTimeRow(
                label = stringResource(R.string.switch_time_1),
                minutesOfDay = settings.switchTime1Minutes,
                onPicked = { m -> viewModel.update { s -> s.copy(switchTime1Minutes = m) } },
            )
            SwitchTimeRow(
                label = stringResource(R.string.switch_time_2),
                minutesOfDay = settings.switchTime2Minutes,
                onPicked = { m -> viewModel.update { s -> s.copy(switchTime2Minutes = m) } },
            )
        } else {
            SwitchTimeRow(
                label = stringResource(R.string.switch_time),
                minutesOfDay = settings.switchTime24hMinutes,
                onPicked = { m -> viewModel.update { s -> s.copy(switchTime24hMinutes = m) } },
            )
        }
        SwitchStrategyType.TRAILING -> Unit
    }
    val fade = settings.ringEndFadeDeg
    LabeledSlider(
        label = stringResource(R.string.ring_end_fade_label),
        value = if (fade == 0) stringResource(R.string.label_position_none) else stringResource(R.string.ring_end_fade_value, fade),
        position = fade.toFloat(),
        range = 0f..WidgetSettings.MAX_RING_END_FADE_DEG.toFloat(),
        steps = WidgetSettings.MAX_RING_END_FADE_DEG / RING_END_FADE_STEP - 1,
        onChange = { viewModel.update { s -> s.copy(ringEndFadeDeg = it.roundToInt()) } },
    )
    Hint(stringResource(R.string.ring_end_fade_hint))
}

/** Shows a switch time; tapping it opens a time picker dialog in the device's 12/24-hour format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwitchTimeRow(label: String, minutesOfDay: Int, onPicked: (Int) -> Unit) {
    var showPicker by remember { mutableStateOf(false) }
    val formats = rememberUiFormats()
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(formats.timeOfDay(minutesOfDay), style = MaterialTheme.typography.titleMedium) },
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = Modifier.clickable { showPicker = true },
    )
    if (showPicker) {
        val state = rememberTimePickerState(
            initialHour = minutesOfDay / 60,
            initialMinute = minutesOfDay % 60,
            is24Hour = formats.is24Hour,
        )
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onPicked(state.hour * 60 + state.minute)
                    showPicker = false
                }) { Text(stringResource(R.string.action_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showPicker = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

/** Slider for the elevation (degrees) shown at the far edge of the ring. */
@Composable
internal fun SkyTopElevationSlider(elevationDeg: Int, onChange: (Int) -> Unit) {
    LabeledSlider(
        label = stringResource(R.string.sky_top_elevation_label),
        value = stringResource(R.string.sky_top_elevation_value, elevationDeg),
        position = elevationDeg.toFloat(),
        range = SKY_TOP_ELEVATION_MIN.toFloat()..SKY_TOP_ELEVATION_MAX.toFloat(),
        steps = (SKY_TOP_ELEVATION_MAX - SKY_TOP_ELEVATION_MIN) / SKY_TOP_ELEVATION_STEP - 1,
        onChange = { onChange(it.roundToInt()) },
    )
}

private const val SKY_TOP_ELEVATION_MIN = 10
private const val SKY_TOP_ELEVATION_MAX = SkyProjection.MAX_TOP_ELEVATION_DEG.toInt()
private const val SKY_TOP_ELEVATION_STEP = 5
private const val RING_END_FADE_STEP = 5

/** Weather, times, settings and preview. */
private const val MAX_TABS = 4
