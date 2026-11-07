package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.SummaryPosition
import com.umfluten.lighttimeclouds.data.WeatherRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.data.roundCoordinate
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.widget.ClockFaceRenderer
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import com.umfluten.lighttimeclouds.widget.WeatherSummaryText
import com.umfluten.lighttimeclouds.widget.chromeColor
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZonedDateTime
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/** Everything one preview frame is rendered from; equal frames are not rendered twice. */
private data class PreviewFrame(
    val settings: WidgetSettings,
    val now: ZonedDateTime,
    val weather: WeatherForecast?,
    val nextAlarm: Instant?,
)

/**
 * The widget as drawn with the current (possibly not yet saved) settings, with sliders for latitude,
 * longitude, day of the year, time of day and, while the ring shows the sky, the elevation at the
 * ring's far edge. The sliders only affect the preview; the time stays in the time zone of the
 * widget's place. Values start at the place, the current time and the set elevation, and start
 * over when another place (or elevation) is chosen in the settings.
 */
@Composable
internal fun PreviewTab(settings: WidgetSettings, padding: PaddingValues) {
    val context = LocalContext.current
    val place = settings.place
    val formats = rememberUiFormats()
    val year = remember(place.zone) { Year.now(place.zone) }

    var latitude by rememberSaveable(place) { mutableFloatStateOf(place.latitude.toFloat()) }
    var longitude by rememberSaveable(place) { mutableFloatStateOf(place.longitude.toFloat()) }
    var dayOfYear by rememberSaveable(place) { mutableIntStateOf(ZonedDateTime.now(place.zone).dayOfYear) }
    var minuteOfDay by rememberSaveable(place) {
        mutableIntStateOf(ZonedDateTime.now(place.zone).let { it.hour * 60 + it.minute })
    }
    var skyTopElevation by rememberSaveable(place, settings.skyTopElevationDeg) {
        mutableIntStateOf(settings.skyTopElevationDeg)
    }
    fun reset() {
        val now = ZonedDateTime.now(place.zone)
        skyTopElevation = settings.skyTopElevationDeg
        latitude = place.latitude.toFloat()
        longitude = place.longitude.toFloat()
        dayOfYear = now.dayOfYear
        minuteOfDay = now.hour * 60 + now.minute
    }

    val now = LocalDate.ofYearDay(year.value, dayOfYear.coerceAtMost(year.length()))
        .atTime(minuteOfDay / 60, minuteOfDay % 60)
        .atZone(place.zone)

    var weather by remember(place) { mutableStateOf<WeatherForecast?>(null) }
    LaunchedEffect(place, settings.showWeather) {
        weather = if (settings.showWeather) WeatherRepository(context).cached(place) else null
    }
    val nextAlarm = remember(settings.showNextAlarm) {
        if (settings.showNextAlarm) ClockWidgetProvider.nextAlarm(context) else null
    }
    val summary = if (settings.showWeather && settings.summaryPosition != SummaryPosition.NONE) {
        WeatherSummaryText.of(LocalResources.current, weather, now.toInstant())
    } else {
        null
    }
    val frame by rememberUpdatedState(
        PreviewFrame(
            settings.copy(
                place = place.copy(latitude = roundCoordinate(latitude.toDouble()), longitude = roundCoordinate(longitude.toDouble())),
                skyTopElevationDeg = skyTopElevation,
            ),
            now,
            weather,
            nextAlarm,
        ),
    )

    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Hint(stringResource(R.string.preview_intro))
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            // Side text takes up to 40% of the width, as in the widget.
            val sideText = summary != null && settings.summaryPosition in setOf(SummaryPosition.LEFT, SummaryPosition.RIGHT)
            val sizeDp = minOf(if (sideText) maxWidth * 0.6f else maxWidth, 320.dp)
            val sizePx = with(LocalDensity.current) { sizeDp.roundToPx() }
            var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }
            // Renders off the main thread; while dragging, frames that arrive during a render are
            // dropped in favor of the latest one.
            LaunchedEffect(sizePx) {
                snapshotFlow { frame }.conflate().collect { f ->
                    bitmap = withContext(Dispatchers.Default) {
                        ClockFaceRenderer.render(context, sizePx, f.settings, f.weather, f.now, f.nextAlarm).asImageBitmap()
                    }
                }
            }
            val clock = @Composable {
                Box(Modifier.size(sizeDp)) {
                    bitmap?.let { Image(it, contentDescription = stringResource(R.string.widget_content_description)) }
                }
            }
            val summaryColor = Color(chromeColor(context, settings))
            when {
                summary == null -> clock()
                settings.summaryPosition == SummaryPosition.BELOW -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    clock()
                    Text(summary, color = summaryColor, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                }
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    val left = settings.summaryPosition == SummaryPosition.LEFT
                    val text = @Composable {
                        Text(
                            summary,
                            color = summaryColor,
                            textAlign = if (left) TextAlign.End else TextAlign.Start,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        )
                    }
                    if (left) text()
                    clock()
                    if (!left) text()
                }
            }
        }

        LabeledSlider(
            label = stringResource(R.string.place_latitude),
            value = formats.degrees(latitude.toDouble()),
            position = latitude,
            range = -90f..90f,
            onChange = { latitude = it },
        )
        LabeledSlider(
            label = stringResource(R.string.place_longitude),
            value = formats.degrees(longitude.toDouble()),
            position = longitude,
            range = -180f..180f,
            onChange = { longitude = it },
        )
        LabeledSlider(
            label = stringResource(R.string.preview_date),
            value = formats.date(now),
            position = dayOfYear.toFloat(),
            range = 1f..year.length().toFloat(),
            onChange = { dayOfYear = it.roundToInt() },
        )
        LabeledSlider(
            label = stringResource(R.string.preview_time),
            value = formats.time(now),
            position = minuteOfDay.toFloat(),
            range = 0f..(24 * 60 - 1).toFloat(),
            onChange = { minuteOfDay = it.roundToInt() },
        )
        SkyTopElevationSlider(skyTopElevation) { skyTopElevation = it }
        TextButton(onClick = ::reset, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.preview_reset))
        }
    }
}
