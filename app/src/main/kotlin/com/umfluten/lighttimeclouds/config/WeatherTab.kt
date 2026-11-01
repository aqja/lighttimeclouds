package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.WeatherRepository
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import com.umfluten.lighttimeclouds.widget.ClockFaceRenderer
import com.umfluten.lighttimeclouds.widget.WeatherSummaryText
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.roundToInt

/**
 * The cached forecast of the widget's place: a [WeatherChart] of the whole span, and on request a
 * table with one row per hour (air and apparent temperature side by side, then cloud cover and
 * precipitation; past hours dimmed, the current one highlighted). Refreshes every minute.
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
    var showTable by rememberSaveable { mutableStateOf(false) }

    LazyColumn(contentPadding = padding) {
        item {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, style = MaterialTheme.typography.titleMedium)
                summary?.let { Text(it, style = MaterialTheme.typography.bodyLarge) }
                Hint(stringResource(R.string.weather_intro))
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
            item { WeatherChart(rows, settings, now, formats) }
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
        if (rows.isEmpty()) return@LazyColumn
        item {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text(stringResource(R.string.weather_hourly_values)) },
                trailingContent = {
                    Icon(
                        Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        modifier = Modifier.rotate(if (showTable) 180f else 0f),
                    )
                },
                modifier = Modifier.clickable { showTable = !showTable },
            )
        }
        if (!showTable) return@LazyColumn
        item {
            HeaderRow()
            HorizontalDivider()
        }
        items(rows.size) { index ->
            val row = rows[index]
            val local = row.time.atZone(now.zone)
            val previousDate = rows.getOrNull(index - 1)?.time?.atZone(now.zone)?.toLocalDate()
            if (previousDate != local.toLocalDate()) {
                Text(
                    formats.date(local),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            HourRow(row, local, formats, isCurrent = index == currentIndex, isPast = index < currentIndex)
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

@Composable
private fun HeaderRow() {
    val style = MaterialTheme.typography.labelMedium
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    TableRow(Modifier.padding(vertical = 8.dp)) {
        Cell(stringResource(R.string.weather_column_time), style, color, align = TextAlign.Start, weight = TIME_WEIGHT)
        Cell(stringResource(R.string.weather_column_air), style, color)
        Cell(stringResource(R.string.weather_column_apparent), style, color)
        Cell(stringResource(R.string.weather_column_clouds), style, color)
        Cell(stringResource(R.string.weather_column_precipitation), style, color, weight = PRECIPITATION_WEIGHT)
    }
}

@Composable
private fun HourRow(row: HourRow, local: ZonedDateTime, formats: UiFormats, isCurrent: Boolean, isPast: Boolean) {
    val color = timelineColor(highlighted = isCurrent, past = isPast)
    val style = MaterialTheme.typography.bodyLarge.let { if (isCurrent) it.copy(fontWeight = FontWeight.Bold) else it }
    val background = if (isCurrent) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surface
    TableRow(Modifier.background(background).padding(vertical = 10.dp)) {
        Cell(formats.timeOfDay(local.hour * 60 + local.minute), style, color, align = TextAlign.Start, weight = TIME_WEIGHT)
        Cell(row.temperatureC?.let(ClockFaceRenderer::formatTemperature) ?: "–", style, color)
        Cell(row.apparentTemperatureC?.let(ClockFaceRenderer::formatTemperature) ?: "–", style, color)
        Cell(stringResource(R.string.percent, row.cloudCoverPercent.roundToInt()), style, color)
        Column(Modifier.weight(PRECIPITATION_WEIGHT), horizontalAlignment = Alignment.End) {
            Text(precipitationText(row.precipitation, formats), style = style, color = color)
            row.precipitation.precipitationProbabilityPercent?.takeIf { it > 0.0 }?.let {
                Text(
                    stringResource(R.string.weather_probability, it.roundToInt()),
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                )
            }
        }
    }
}

/** Amount in mm with one decimal, marked as snow where it mostly falls as snow; a dash for a dry hour. */
@Composable
private fun precipitationText(condition: WeatherCondition, formats: UiFormats): String {
    if (condition.precipitationMm < WeatherCondition.MIN_PRECIPITATION_MM) return "–"
    val amount = String.format(formats.locale, "%.1f", condition.precipitationMm)
    return stringResource(if (condition.isSnow) R.string.weather_amount_snow else R.string.weather_amount, amount)
}

@Composable
private fun TableRow(modifier: Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

@Composable
private fun RowScope.Cell(
    text: String,
    style: TextStyle,
    color: Color,
    align: TextAlign = TextAlign.End,
    weight: Float = 1f,
) {
    Text(text, style = style, color = color, textAlign = align, modifier = Modifier.weight(weight))
}

private const val TIME_WEIGHT = 1.2f
private const val PRECIPITATION_WEIGHT = 1.5f
