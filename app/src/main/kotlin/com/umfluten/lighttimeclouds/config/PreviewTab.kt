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
import androidx.compose.material3.Slider
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.WeatherRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.widget.ClockFaceRenderer
import com.umfluten.lighttimeclouds.widget.ClockWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.Year
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/** Everything one preview frame is rendered from; equal frames are not rendered twice. */
private data class PreviewFrame(
    val settings: WidgetSettings,
    val now: ZonedDateTime,
    val weather: WeatherForecast?,
    val nextAlarm: Instant?,
)

/**
 * The widget as drawn with the current (possibly not yet saved) settings, with sliders for latitude,
 * longitude, day of the year and time of day. The sliders only affect the preview; the time stays
 * in the time zone of the widget's place. Values start at the place and the current time, and
 * start over when another place is chosen.
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
    fun reset() {
        val now = ZonedDateTime.now(place.zone)
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
    val frame by rememberUpdatedState(
        PreviewFrame(
            settings.copy(place = place.copy(latitude = latitude.toDouble(), longitude = longitude.toDouble())),
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
        Text(
            stringResource(R.string.preview_intro),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val sizeDp = minOf(maxWidth, 320.dp)
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
            Box(Modifier.size(sizeDp)) {
                bitmap?.let { Image(it, contentDescription = stringResource(R.string.widget_content_description)) }
            }
        }

        PreviewSlider(
            label = stringResource(R.string.place_latitude),
            value = formats.degrees(latitude.toDouble()),
            position = latitude,
            range = -90f..90f,
            onChange = { latitude = it },
        )
        PreviewSlider(
            label = stringResource(R.string.place_longitude),
            value = formats.degrees(longitude.toDouble()),
            position = longitude,
            range = -180f..180f,
            onChange = { longitude = it },
        )
        PreviewSlider(
            label = stringResource(R.string.preview_date),
            value = formats.date(now),
            position = dayOfYear.toFloat(),
            range = 1f..year.length().toFloat(),
            onChange = { dayOfYear = it.roundToInt() },
        )
        PreviewSlider(
            label = stringResource(R.string.preview_time),
            value = formats.time(now),
            position = minuteOfDay.toFloat(),
            range = 0f..(24 * 60 - 1).toFloat(),
            onChange = { minuteOfDay = it.roundToInt() },
        )
        TextButton(onClick = ::reset, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.preview_reset))
        }
    }
}

/** A slider with its label on the left and the current [value] on the right above it. */
@Composable
private fun PreviewSlider(
    label: String,
    value: String,
    position: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.bodyLarge)
        }
        Slider(value = position, onValueChange = onChange, valueRange = range)
    }
}
