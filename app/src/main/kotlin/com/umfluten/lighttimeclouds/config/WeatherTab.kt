package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.WeatherRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.widget.WeatherSummaryText
import com.umfluten.lighttimeclouds.widget.formatTemperature
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/**
 * The cached forecast of the widget's place: a [WeatherChart] of the whole span, and above it the
 * values of the hour chosen there, at first the current one. Refreshes every minute.
 */
@Composable
internal fun WeatherTab(settings: WidgetSettings, padding: PaddingValues) {
    val place = settings.place
    val now = rememberMinuteClock(place.zone)
    val context = LocalContext.current
    val formats = rememberUiFormats()
    var forecast by remember(place) { mutableStateOf<WeatherForecast?>(null) }
    LaunchedEffect(place, now) {
        forecast = WeatherRepository(context).cached(place)
    }
    val rows = remember(forecast) { forecast?.let(::hourRows).orEmpty() }
    val resources = LocalResources.current
    val summary = remember(resources, forecast, now) { WeatherSummaryText.of(resources, forecast, now.toInstant()) }
    val currentIndex = rows.indexOfFirst { !now.toInstant().isBefore(it.time) && now.toInstant().isBefore(it.time.plus(HOUR)) }
    // Kept by its time, so the choice survives a new forecast as long as the hour is still in it.
    var chosenHour by rememberSaveable(place) { mutableStateOf<Long?>(null) }
    val selected = rows.indexOfFirst { it.time.epochSecond == chosenHour }.takeIf { it >= 0 } ?: currentIndex.coerceAtLeast(0)

    LazyColumn(contentPadding = padding) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
            }
        }
        if (rows.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.weather_no_forecast),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp),
                )
            }
        } else {
            item {
                val title = hourTitle(rows[selected], now.zone, formats)
                val values = hourValues(rows[selected], formats)
                val spoken = spokenHour(title, values)
                HourDetails(title, values, spoken)
                WeatherChart(rows, settings, now, formats, selected, spoken) { chosenHour = rows[it].time.epochSecond }
            }
        }
        item {
            val fetched = forecast?.fetchedAt?.atZone(now.zone)
            ListItem(
                headlineContent = { Text(stringResource(R.string.forecast_loaded)) },
                supportingContent = { Text(stringResource(R.string.weather_attribution)) },
                trailingContent = {
                    val label = when {
                        fetched == null -> stringResource(R.string.forecast_not_yet)
                        fetched.toLocalDate() == now.toLocalDate() -> formats.time(fetched)
                        else -> stringResource(R.string.date_time, formats.date(fetched), formats.time(fetched))
                    }
                    Text(label)
                },
            )
        }
    }
}

private val HOUR: Duration = Duration.ofHours(1)

/** One hour from [time] on: temperatures and cloud cover at [time], precipitation during the hour after it. */
internal data class HourRow(
    val time: Instant,
    val temperatureC: Double?,
    val apparentTemperatureC: Double?,
    val cloudCoverPercent: Double,
    val precipitation: WeatherCondition,
)

/**
 * Pairs each step with the next one: Open-Meteo gives temperatures and cloud cover as values at the
 * full hour, but precipitation as the sum of the hour *before* it, so the hour from 14:00 to 15:00
 * takes its precipitation from the 15:00 step.
 */
private fun hourRows(forecast: WeatherForecast): List<HourRow> =
    forecast.hours.zipWithNext()
        .filter { (start, end) -> Duration.between(start.time, end.time) == HOUR }
        .map { (start, end) ->
            HourRow(
                time = start.time,
                temperatureC = start.temperatureC,
                apparentTemperatureC = start.apparentTemperatureC,
                cloudCoverPercent = start.cloudCoverPercent,
                precipitation = WeatherCondition(
                    cloudCoverPercent = start.cloudCoverPercent,
                    precipitationMm = end.precipitationMm,
                    snowfallCm = end.snowfallCm,
                    precipitationProbabilityPercent = end.precipitationProbabilityPercent,
                    temperatureC = start.temperatureC,
                ),
            )
        }

/** One value of an hour, under its label. */
private class HourValue(val label: String, val value: String, val note: String = "", val weight: Float = 1f)

@Composable
private fun hourTitle(row: HourRow, zone: ZoneId, formats: UiFormats): String {
    val local = row.time.atZone(zone)
    return stringResource(R.string.date_time, formats.date(local), formats.timeOfDay(local.hour * 60 + local.minute))
}

@Composable
private fun hourValues(row: HourRow, formats: UiFormats): List<HourValue> = listOf(
    HourValue(stringResource(R.string.weather_column_air), row.temperatureC?.let(::formatTemperature) ?: NO_VALUE),
    HourValue(stringResource(R.string.weather_column_apparent), row.apparentTemperatureC?.let(::formatTemperature) ?: NO_VALUE),
    HourValue(stringResource(R.string.weather_column_clouds), stringResource(R.string.percent, row.cloudCoverPercent.roundToInt())),
    HourValue(
        stringResource(R.string.weather_column_precipitation),
        precipitationText(row.precipitation, formats),
        note = row.precipitation.precipitationProbabilityPercent?.takeIf { it > 0.0 }
            ?.let { stringResource(R.string.weather_probability, it.roundToInt()) }.orEmpty(),
        weight = PRECIPITATION_WEIGHT,
    ),
)

/** [title] and [values] as one sentence for a screen reader, leaving out what has no value. */
private fun spokenHour(title: String, values: List<HourValue>): String =
    (listOf(title) + values.filter { it.value != NO_VALUE }.map { "${it.label} ${it.value} ${it.note}".trim() }).joinToString(", ")

/**
 * [title] and [values], in one size whichever hour it is, so the chart below stays in place while
 * another one is chosen. A screen reader reads them as [spoken], in one go.
 */
@Composable
private fun HourDetails(title: String, values: List<HourValue>, spoken: String) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (value in values) LabeledValue(value.label, value.value, value.note, value.weight)
        }
    }
}

/** Amount in mm with one decimal, marked as snow where it mostly falls as snow; a dash for a dry hour. */
@Composable
private fun precipitationText(condition: WeatherCondition, formats: UiFormats): String {
    if (condition.precipitationMm < WeatherCondition.MIN_PRECIPITATION_MM) return NO_VALUE
    val amount = String.format(formats.locale, "%.1f", condition.precipitationMm)
    return stringResource(if (condition.isSnow) R.string.weather_amount_snow else R.string.weather_amount, amount)
}

private const val PRECIPITATION_WEIGHT = 1.5f
private const val NO_VALUE = "–"
