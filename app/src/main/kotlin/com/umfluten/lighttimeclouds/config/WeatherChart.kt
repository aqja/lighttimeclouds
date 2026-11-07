package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.widget.cloudColor
import com.umfluten.lighttimeclouds.widget.formatTemperature
import com.umfluten.lighttimeclouds.widget.precipitationColor
import com.umfluten.lighttimeclouds.widget.ringColor
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The whole forecast at a glance, on one time axis: a strip in the ring's sky colors marking day and
 * night, air and apparent temperature as two lines, and below them cloud cover hanging from the top
 * and precipitation rising from the bottom, colored as on the weather ring. Thin lines separate the
 * days, a stronger one marks [now].
 */
@Composable
internal fun WeatherChart(rows: List<HourRow>, settings: WidgetSettings, now: ZonedDateTime, formats: UiFormats) {
    if (rows.isEmpty()) return
    val start = rows.first().time
    val end = rows.last().time.plus(Duration.ofHours(1))
    val dark = isSystemInDarkTheme()
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val airColor = MaterialTheme.colorScheme.primary
    val apparentColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val nowColor = MaterialTheme.colorScheme.onSurface
    val place = settings.place

    val temperatures = rows.flatMap { listOfNotNull(it.temperatureC, it.apparentTemperatureC) }
    val (tMin, tMax, tStep) = temperatureScale(temperatures)
    val maxPrecipitation = ceil(maxOf(MIN_PRECIPITATION_SCALE_MM, rows.maxOf { it.precipitation.precipitationMm }))
    val precipitationScaleLabel = stringResource(R.string.weather_amount, maxPrecipitation.toInt().toString())

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .padding(horizontal = 16.dp),
    ) {
        val left = GUTTER.toPx()
        val width = size.width - left
        fun x(instant: Instant): Float =
            left + width * Duration.between(start, instant).toMillis() / Duration.between(start, end).toMillis()

        val skyTop = 0f
        val skyBottom = SKY_STRIP.toPx()
        val tempTop = skyBottom + GAP.toPx()
        val tempBottom = tempTop + TEMPERATURE_PANEL.toPx()
        val weatherTop = tempBottom + GAP.toPx() * 2
        val weatherBottom = weatherTop + WEATHER_PANEL.toPx()
        val axisTop = weatherBottom + 2.dp.toPx()
        fun y(celsius: Double): Float = (tempBottom - (celsius - tMin) / (tMax - tMin) * (tempBottom - tempTop)).toFloat()

        // Sky strip, sampled every 20 minutes.
        var t = start
        while (t.isBefore(end)) {
            val next = t.plus(SKY_SAMPLE)
            val altitude = SunMoonCalculator.sunAltitudeDegrees(t.plus(SKY_SAMPLE.dividedBy(2)), place.latitude, place.longitude)
            drawRect(
                Color(ringColor(altitude, settings)),
                topLeft = Offset(x(t), skyTop),
                size = Size(x(next) - x(t) + 0.5f, skyBottom - skyTop),
            )
            t = next
        }

        // Temperature grid with labels in the gutter.
        var grid = tMin
        while (grid <= tMax + 1e-6) {
            val gy = y(grid)
            drawLine(gridColor, Offset(left, gy), Offset(size.width, gy), strokeWidth = 1f)
            val label = measurer.measure(formatTemperature(grid), labelStyle)
            drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), gy - label.size.height / 2f))
            grid += tStep
        }

        // Clouds hang from the top of the weather panel, precipitation rises from its bottom.
        val panelHeight = weatherBottom - weatherTop
        for (row in rows) {
            // Edges on whole pixels, so neighboring translucent bars neither overlap nor leave gaps.
            val x0 = x(row.time).roundToInt().toFloat()
            val x1 = x(row.time.plus(Duration.ofHours(1))).roundToInt().toFloat()
            drawRect(
                Color(cloudColor(row.precipitation, dark)),
                topLeft = Offset(x0, weatherTop),
                size = Size(x1 - x0, panelHeight * 0.5f),
            )
            val condition = row.precipitation
            if (condition.hasPrecipitation) {
                // A mere chance of precipitation still gets a sliver, so it shows like on the ring.
                val fraction = maxOf(condition.precipitationMm / maxPrecipitation, 0.04).coerceAtMost(1.0).toFloat()
                val barHeight = panelHeight * fraction
                drawRect(
                    Color(precipitationColor(condition)),
                    topLeft = Offset(x0, weatherBottom - barHeight),
                    size = Size(x1 - x0, barHeight),
                )
            }
        }
        drawLine(gridColor, Offset(left, weatherBottom), Offset(size.width, weatherBottom), strokeWidth = 1f)
        // Scale of the precipitation bars: the full panel height.
        val precipitationLabel = measurer.measure(precipitationScaleLabel, labelStyle)
        drawText(precipitationLabel, topLeft = Offset(left - precipitationLabel.size.width - 4.dp.toPx(), weatherTop))

        // Day boundaries with the weekday centered in each day.
        var midnight = start.atZone(now.zone).toLocalDate().atStartOfDay(now.zone)
        while (midnight.toInstant().isBefore(end)) {
            val next = midnight.plusDays(1)
            val dayStart = maxOf(midnight.toInstant(), start)
            val dayEnd = minOf(next.toInstant(), end)
            if (!midnight.toInstant().isBefore(start)) {
                drawLine(gridColor, Offset(x(dayStart), skyTop), Offset(x(dayStart), weatherBottom), strokeWidth = 1.5f)
            }
            val label = measurer.measure(formats.weekday(midnight), labelStyle)
            val center = (x(dayStart) + x(dayEnd)) / 2f
            if (x(dayEnd) - x(dayStart) > label.size.width + 4.dp.toPx()) {
                drawText(label, topLeft = Offset(center - label.size.width / 2f, axisTop))
            }
            midnight = next
        }

        temperatureLine(rows.map { it.time to it.apparentTemperatureC }, ::x, ::y, apparentColor, dashed = true)
        temperatureLine(rows.map { it.time to it.temperatureC }, ::x, ::y, airColor, dashed = false)

        val nowX = x(now.toInstant())
        if (nowX in left..size.width) {
            drawLine(nowColor, Offset(nowX, skyTop), Offset(nowX, weatherBottom), strokeWidth = 2.dp.toPx())
        }
    }

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(stringResource(R.string.weather_column_air), labelStyle) { drawLegendLine(airColor, dashed = false) }
        LegendItem(stringResource(R.string.weather_column_apparent), labelStyle) { drawLegendLine(apparentColor, dashed = true) }
        LegendItem(stringResource(R.string.weather_column_clouds), labelStyle) {
            drawRect(Color(cloudColor(FULL_CLOUD, dark)))
        }
        LegendItem(stringResource(R.string.weather_column_precipitation), labelStyle) {
            drawRect(Color(precipitationColor(TYPICAL_RAIN)))
        }
        LegendItem(stringResource(R.string.weather_legend_now), labelStyle) { drawLegendLine(nowColor, dashed = false, vertical = true) }
    }
}

/** Polyline through the hourly values, broken where a value is missing. */
private fun DrawScope.temperatureLine(
    points: List<Pair<Instant, Double?>>,
    x: (Instant) -> Float,
    y: (Double) -> Float,
    color: Color,
    dashed: Boolean,
) {
    val path = Path()
    var penDown = false
    for ((time, value) in points) {
        if (value == null) {
            penDown = false
            continue
        }
        if (penDown) path.lineTo(x(time), y(value)) else path.moveTo(x(time), y(value))
        penDown = true
    }
    drawPath(
        path,
        color,
        style = Stroke(
            width = 2.dp.toPx(),
            pathEffect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null,
        ),
    )
}

private fun DrawScope.drawLegendLine(color: Color, dashed: Boolean, vertical: Boolean = false) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
    if (vertical) {
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
    } else {
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx(), pathEffect = effect)
    }
}

@Composable
private fun LegendItem(label: String, style: TextStyle, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp), onDraw = swatch)
        Text(label, style = style)
    }
}

/**
 * Axis range and grid step for [values]: whole grid steps around them, at least [MIN_TEMPERATURE_SPAN]
 * wide so a calm day does not look dramatic; 5° steps, 10° for wide ranges.
 */
private fun temperatureScale(values: List<Double>): Triple<Double, Double, Double> {
    if (values.isEmpty()) return Triple(0.0, MIN_TEMPERATURE_SPAN, 5.0)
    val low = values.min()
    val high = values.max()
    val step = if (high - low > 30.0) 10.0 else 5.0
    var min = floor(low / step) * step
    var max = ceil(high / step) * step
    while (max - min < MIN_TEMPERATURE_SPAN) {
        min -= step / 2
        max += step / 2
    }
    min = floor(min / step) * step
    max = ceil(max / step) * step
    return Triple(min, max, step)
}

private val GUTTER = 36.dp
private val SKY_STRIP = 8.dp
private val GAP = 6.dp
private val TEMPERATURE_PANEL = 150.dp
private val WEATHER_PANEL = 50.dp
private val AXIS_LABELS = 20.dp
private val CHART_HEIGHT = SKY_STRIP + GAP + TEMPERATURE_PANEL + GAP * 2 + WEATHER_PANEL + AXIS_LABELS
private val SKY_SAMPLE: Duration = Duration.ofMinutes(20)
private const val MIN_TEMPERATURE_SPAN = 10.0

/** The precipitation bars span at least this much, so a drizzle does not fill the panel. */
private const val MIN_PRECIPITATION_SCALE_MM = 2.0

private val FULL_CLOUD = WeatherCondition(100.0, 0.0, 0.0)
private val TYPICAL_RAIN = WeatherCondition(0.0, 1.0, 0.0, 80.0)
