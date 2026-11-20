package com.umfluten.lighttimeclouds.config

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.widget.SkyPalette
import com.umfluten.lighttimeclouds.widget.formatTemperature
import com.umfluten.lighttimeclouds.widget.moonFractionFor
import com.umfluten.lighttimeclouds.widget.precipitationColor
import com.umfluten.lighttimeclouds.widget.skyColorsAt
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * The forecast drawn into the sky it describes: the background is the ring's sky over time, horizon
 * at the bottom. Its height holds three bands: clouds hang from the top and precipitation rises from
 * the horizon, an hour per bar, and between them the wind spreads around the middle as a violin,
 * mean wind filled, gusts outlined. Air and apparent temperature run across it all as two lines. The chart shows [WINDOW] at a time; swiping
 * sideways moves it through the forecast, starting a little before [now], which a line marks.
 * The hour at index [selected] is highlighted; tapping another hands its index to [onSelect].
 * A screen reader gets the chart as a slider over the hours, which says [spokenHour] for the
 * selected one, and the window follows the slider.
 */
@Composable
internal fun WeatherChart(
    rows: List<HourRow>,
    settings: WidgetSettings,
    now: ZonedDateTime,
    formats: UiFormats,
    selected: Int,
    spokenHour: String,
    onSelect: (Int) -> Unit,
) {
    if (rows.isEmpty()) return
    val start = rows.first().time
    val end = rows.last().time.plus(HOUR)
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    val temperatures = rows.flatMap { listOfNotNull(it.temperatureC, it.apparentTemperatureC) }
    val (tMin, tMax, tStep) = temperatureScale(temperatures)
    val maxPrecipitation = ceil(maxOf(MIN_PRECIPITATION_SCALE_MM, rows.maxOf { it.precipitation.precipitationMm }))
    val precipitationScaleLabel = stringResource(R.string.weather_amount, maxPrecipitation.toInt().toString())
    val fullCoverLabel = stringResource(R.string.percent, 100)
    val halfCoverLabel = stringResource(R.string.percent, 50)
    val winds = rows.flatMap { listOfNotNull(it.windSpeedKmh, it.windGustsKmh) }
    val maxWind = maxOf(MIN_WIND_SCALE_KMH, ceil((winds.maxOrNull() ?: 0.0) / 10.0) * 10.0)
    val windScaleLabel = maxWind.toInt().toString()
    val windUnitLabel = stringResource(R.string.weather_speed_unit)
    val sky = remember(start, end, settings) { skySamples(start, end, settings) }

    // The window's left edge in epoch seconds, kept by time so it survives a new forecast; NaN until
    // the user moves it, and the window starts shortly before now.
    var chosenWindowStart by rememberSaveable(settings.place) { mutableDoubleStateOf(Double.NaN) }
    val firstStart = seconds(start)
    val lastStart = maxOf(firstStart, seconds(end) - WINDOW.seconds)
    val defaultStart = seconds(now.toInstant().truncatedTo(ChronoUnit.HOURS)) - LEAD.seconds
    fun windowStart(): Double =
        (if (chosenWindowStart.isNaN()) defaultStart else chosenWindowStart).coerceIn(firstStart, lastStart)

    val gutter = with(LocalDensity.current) { GUTTER.toPx() }
    val rightGutter = with(LocalDensity.current) { RIGHT_GUTTER.toPx() }
    var plotWidth by remember { mutableFloatStateOf(1f) }
    val scrollState = rememberScrollableState { delta ->
        val perPx = WINDOW.seconds / plotWidth
        val from = windowStart()
        val to = (from - delta * perPx).coerceIn(firstStart, lastStart)
        chosenWindowStart = to
        ((from - to) / perPx).toFloat()
    }
    val currentOnSelect by rememberUpdatedState(onSelect)

    // A slider step or a new choice outside the window brings the window to it.
    LaunchedEffect(selected, rows) {
        val hourStart = seconds(rows[selected].time)
        val from = windowStart()
        if (hourStart < from || hourStart + HOUR.seconds > from + WINDOW.seconds) {
            chosenWindowStart = (hourStart + HOUR.seconds / 2.0 - WINDOW.seconds / 2.0).coerceIn(firstStart, lastStart)
        }
    }
    val sliderLabel = stringResource(R.string.weather_chart_hour)

    Spacer(
        Modifier
            .fillMaxWidth()
            .height(CHART_HEIGHT)
            .padding(horizontal = 16.dp)
            .onSizeChanged { plotWidth = (it.width - gutter - rightGutter).coerceAtLeast(1f) }
            .scrollable(scrollState, Orientation.Horizontal)
            .pointerInput(rows, gutter, rightGutter) {
                detectTapGestures { tap ->
                    val outside = tap.x < gutter || tap.x > size.width - rightGutter || tap.y > SKY_HEIGHT.toPx() + AXIS_TICK.toPx()
                    if (outside) return@detectTapGestures
                    val at = windowStart() + (tap.x - gutter) / plotWidth * WINDOW.seconds
                    currentOnSelect(hourIndexAt(rows, Instant.ofEpochMilli((at * 1000).toLong())))
                }
            }
            .semantics(mergeDescendants = true) {
                contentDescription = sliderLabel
                stateDescription = spokenHour
                // One step per hour: the steps between the first and the last.
                progressBarRangeInfo = ProgressBarRangeInfo(
                    selected.toFloat(),
                    0f..rows.lastIndex.toFloat(),
                    steps = (rows.size - 2).coerceAtLeast(0),
                )
                setProgress { target ->
                    currentOnSelect(target.roundToInt().coerceIn(rows.indices))
                    true
                }
            }
            .drawWithCache {
                val left = gutter
                val skyBottom = SKY_HEIGHT.toPx()
                // Vertical gradients do not depend on x, so each sample's brushes serve wherever it is drawn.
                val skyBrushes = sky.map { Brush.verticalGradient(it.sky, 0f, skyBottom) }
                // Right axis: depth of the clouds by cover, the wind's scale on either side of the middle
                // with its unit there, height of the precipitation bars. Where two scales share a line,
                // each label stands on the side of its band.
                val rightLabels = listOf(
                    AxisLabel(measurer.measure(halfCoverLabel, labelStyle), CLOUD_SHARE / 2f, 0f),
                    AxisLabel(measurer.measure(fullCoverLabel, labelStyle), CLOUD_SHARE, -1f),
                    AxisLabel(measurer.measure(windScaleLabel, labelStyle), WIND_CENTER - WIND_SHARE, 1f),
                    AxisLabel(measurer.measure(windUnitLabel, labelStyle), WIND_CENTER, 0f),
                    AxisLabel(measurer.measure(windScaleLabel, labelStyle), WIND_CENTER + WIND_SHARE, -1f),
                    AxisLabel(measurer.measure(precipitationScaleLabel, labelStyle), 1f - PRECIPITATION_SHARE, 1f),
                )
                onDrawBehind {
                    val right = size.width - rightGutter
                    val from = windowStart()
                    val perPx = WINDOW.seconds / (right - left)
                    fun x(instant: Instant): Float = (left + (seconds(instant) - from) / perPx).toFloat()
                    val tempTop = TEMPERATURE_MARGIN.toPx()
                    val tempBottom = skyBottom - TEMPERATURE_MARGIN.toPx()
                    fun y(celsius: Double): Float = (tempBottom - (celsius - tMin) / (tMax - tMin) * (tempBottom - tempTop)).toFloat()
                    val precipitationTop = skyBottom * (1f - PRECIPITATION_SHARE)
                    val visible = Instant.ofEpochSecond(from.toLong() - SKY_SAMPLE.seconds)..
                        Instant.ofEpochSecond((from + WINDOW.seconds).toLong() + HOUR.seconds)

                    clipRect(left, 0f, right, skyBottom) {
                        sky.forEachIndexed { i, sample ->
                            if (sample.time !in visible) return@forEachIndexed
                            val x0 = x(sample.time).roundToInt().toFloat()
                            val x1 = x(sample.time.plus(SKY_SAMPLE)).roundToInt().toFloat()
                            drawRect(skyBrushes[i], Offset(x0, 0f), Size(x1 - x0, skyBottom))
                        }

                        // Clouds hang from the top, an hour per bar: the more cover, the deeper and darker.
                        for (row in rows) {
                            if (row.time !in visible) continue
                            val cover = (row.cloudCoverPercent / 100.0).coerceIn(0.0, 1.0).toFloat()
                            if (cover < 0.01f) continue
                            // Edges on whole pixels, so neighboring translucent bars neither overlap nor leave gaps.
                            val x0 = x(row.time).roundToInt().toFloat()
                            val x1 = x(row.time.plus(HOUR)).roundToInt().toFloat()
                            val depth = skyBottom * CLOUD_SHARE * cover
                            val color = cloudColor(cover)
                            drawRect(color, Offset(x0, 0f), Size(x1 - x0, depth))
                            // A firmer lower edge, like the precipitation's upper one.
                            drawLine(color.copy(alpha = 1f), Offset(x0, depth), Offset(x1, depth), strokeWidth = 1.5.dp.toPx())
                        }

                        // Precipitation rises from the horizon, a full panel being [maxPrecipitation].
                        for (row in rows) {
                            if (row.time !in visible) continue
                            val condition = row.precipitation
                            if (!condition.hasPrecipitation) continue
                            val x0 = x(row.time).roundToInt().toFloat()
                            val x1 = x(row.time.plus(HOUR)).roundToInt().toFloat()
                            // A mere chance of precipitation still gets a sliver, so it shows like on the ring.
                            val fraction = maxOf(condition.precipitationMm / maxPrecipitation, 0.04).coerceAtMost(1.0).toFloat()
                            val barTop = skyBottom - (skyBottom - precipitationTop) * fraction
                            val color = Color(precipitationColor(condition))
                            drawRect(color, Offset(x0, barTop), Size(x1 - x0, skyBottom - barTop))
                            // A firmer top edge, so even an unlikely shower stands out from a blue sky.
                            drawLine(
                                color.copy(alpha = maxOf(color.alpha, 0.7f)),
                                Offset(x0, barTop), Offset(x1, barTop),
                                strokeWidth = 1.5.dp.toPx(),
                            )
                        }
                        // The wind around the middle: mean wind filled, gusts outlined, scaled to [maxWind].
                        val windCenter = skyBottom * WIND_CENTER
                        val windReach = skyBottom * WIND_SHARE
                        drawLine(
                            SKY_INK.copy(alpha = 0.3f),
                            Offset(left, windCenter), Offset(right, windCenter),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                        )
                        val meanWind = rows.map { x(it.time) to it.windSpeedKmh?.let { v -> (v / maxWind).toFloat() * windReach } }
                        // A gust is the strongest of its hour, so it stands in the middle of it.
                        val gusts = rows.map { x(it.time.plus(HALF_HOUR)) to it.windGustsKmh?.let { v -> (v / maxWind).toFloat() * windReach } }
                        for (path in violinPaths(gusts, windCenter)) {
                            drawPath(path, HALO, style = Stroke(width = 2.5.dp.toPx()))
                            drawPath(path, WIND_COLOR, style = Stroke(width = 1.dp.toPx()))
                        }
                        for (path in violinPaths(meanWind, windCenter)) drawPath(path, WIND_COLOR.copy(alpha = WIND_FILL_ALPHA))

                        // Scale of the precipitation bars, labeled on the right axis.
                        drawLine(
                            SKY_INK.copy(alpha = 0.35f),
                            Offset(left, precipitationTop), Offset(right, precipitationTop),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                        )

                        // Temperature grid.
                        var grid = tMin
                        while (grid <= tMax + 1e-6) {
                            drawLine(SKY_INK.copy(alpha = 0.18f), Offset(left, y(grid)), Offset(right, y(grid)), strokeWidth = 1f)
                            grid += tStep
                        }

                        // Every six hours a faint line, at midnight a stronger one.
                        var mark = from.toLong() / 3600 * 3600
                        while (mark <= from + WINDOW.seconds) {
                            val local = Instant.ofEpochSecond(mark).atZone(now.zone)
                            val mx = x(local.toInstant())
                            if (local.hour == 0) {
                                drawLine(SKY_INK.copy(alpha = 0.45f), Offset(mx, 0f), Offset(mx, skyBottom), strokeWidth = 1.5f)
                            } else if (local.hour % 6 == 0) {
                                drawLine(SKY_INK.copy(alpha = 0.15f), Offset(mx, 0f), Offset(mx, skyBottom), strokeWidth = 1f)
                            }
                            mark += 3600
                        }

                        val hour = rows[selected]
                        val hourStart = x(hour.time)
                        val hourEnd = x(hour.time.plus(HOUR))
                        drawRect(SKY_INK.copy(alpha = SELECTED_HOUR_ALPHA), Offset(hourStart, 0f), Size(hourEnd - hourStart, skyBottom))
                        // Edges on a halo, so the hour also stands out where the sky is bright.
                        for (edge in listOf(hourStart, hourEnd)) {
                            drawLine(HALO, Offset(edge, 0f), Offset(edge, skyBottom), strokeWidth = 2.5.dp.toPx())
                            drawLine(SKY_INK.copy(alpha = 0.8f), Offset(edge, 0f), Offset(edge, skyBottom), strokeWidth = 1.dp.toPx())
                        }

                        temperatureLine(rows.map { it.time to it.apparentTemperatureC }, ::x, ::y, APPARENT_COLOR, dashed = true)
                        temperatureLine(rows.map { it.time to it.temperatureC }, ::x, ::y, AIR_COLOR, dashed = false)

                        val nowX = x(now.toInstant())
                        drawLine(HALO, Offset(nowX, 0f), Offset(nowX, skyBottom), strokeWidth = 4.dp.toPx())
                        drawLine(SKY_INK, Offset(nowX, 0f), Offset(nowX, skyBottom), strokeWidth = 2.dp.toPx())

                        for ((value, color) in listOf(hour.apparentTemperatureC to APPARENT_COLOR, hour.temperatureC to AIR_COLOR)) {
                            if (value == null) continue
                            drawCircle(HALO, SELECTED_POINT.toPx() + 1.5.dp.toPx(), Offset(hourStart, y(value)))
                            drawCircle(color, SELECTED_POINT.toPx(), Offset(hourStart, y(value)))
                        }
                    }

                    for ((label, share, side) in rightLabels) {
                        val ly = skyBottom * share
                        drawLine(gridColor, Offset(right, ly), Offset(right + RIGHT_TICK.toPx(), ly), strokeWidth = 1.5f)
                        val top = ly - label.size.height / 2f + side * label.size.height / 2f
                        drawText(label, topLeft = Offset(right + RIGHT_TICK.toPx() + 2.dp.toPx(), top))
                    }

                    // Temperature labels in the gutter, which stays in place.
                    var grid = tMin
                    while (grid <= tMax + 1e-6) {
                        val label = measurer.measure(formatTemperature(grid), labelStyle)
                        drawText(label, topLeft = Offset(left - label.size.width - 4.dp.toPx(), y(grid) - label.size.height / 2f))
                        grid += tStep
                    }

                    // Time axis: a tick per hour, longer every three; times every six hours, then the days.
                    val hourLabelsTop = skyBottom + AXIS_TICK.toPx() + 1.dp.toPx()
                    val dayLabelsTop = hourLabelsTop + AXIS_ROW.toPx()
                    clipRect(left, skyBottom, right, size.height) {
                        var tick = from.toLong() / 3600 * 3600
                        while (tick <= from + WINDOW.seconds + 3600) {
                            val local = Instant.ofEpochSecond(tick).atZone(now.zone)
                            val tx = x(local.toInstant())
                            val length = if (local.hour % 3 == 0) AXIS_TICK.toPx() else AXIS_TICK.toPx() / 2f
                            drawLine(gridColor, Offset(tx, skyBottom), Offset(tx, skyBottom + length), strokeWidth = 1.5f)
                            if (local.hour % 6 == 0) {
                                val label = measurer.measure(formats.timeOfDay(local.hour * 60), labelStyle)
                                val lx = tx - label.size.width / 2f
                                if (lx >= left && lx + label.size.width <= right) {
                                    drawText(label, topLeft = Offset(lx, hourLabelsTop))
                                }
                            }
                            tick += 3600
                        }

                        // The day's name in the middle of its visible part, so it follows along while swiping.
                        val windowEnd = Instant.ofEpochMilli(((from + WINDOW.seconds) * 1000).toLong())
                        val windowBegin = Instant.ofEpochMilli((from * 1000).toLong())
                        var midnight = windowBegin.atZone(now.zone).toLocalDate().atStartOfDay(now.zone)
                        while (midnight.toInstant().isBefore(windowEnd)) {
                            val next = midnight.plusDays(1)
                            val x0 = x(maxOf(midnight.toInstant(), windowBegin))
                            val x1 = x(minOf(next.toInstant(), windowEnd))
                            val room = x1 - x0 - 8.dp.toPx()
                            val label = listOf(formats.date(midnight), formats.weekday(midnight))
                                .map { measurer.measure(it, labelStyle) }
                                .firstOrNull { it.size.width <= room }
                            if (label != null) {
                                val center = ((x0 + x1) / 2f).coerceIn(x0 + label.size.width / 2f, x1 - label.size.width / 2f)
                                drawText(label, topLeft = Offset(center - label.size.width / 2f, dayLabelsTop))
                            }
                            midnight = next
                        }
                    }

                    // Where the window lies in the whole forecast.
                    val trackTop = size.height - TRACK.toPx()
                    val trackWidth = right - left
                    val span = seconds(end) - firstStart
                    val radius = CornerRadius(TRACK.toPx() / 2f)
                    drawRoundRect(trackColor, Offset(left, trackTop), Size(trackWidth, TRACK.toPx()), radius)
                    val thumbStart = ((from - firstStart) / span * trackWidth).toFloat()
                    val thumbWidth = (minOf(1.0, WINDOW.seconds / span) * trackWidth).toFloat()
                    drawRoundRect(thumbColor, Offset(left + thumbStart, trackTop), Size(thumbWidth, TRACK.toPx()), radius)
                }
            },
    )

    FlowRow(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        LegendItem(stringResource(R.string.weather_column_air), labelStyle) { drawLegendLine(AIR_COLOR, dashed = false) }
        LegendItem(stringResource(R.string.weather_column_apparent), labelStyle) { drawLegendLine(APPARENT_COLOR, dashed = true) }
        LegendItem(stringResource(R.string.weather_column_clouds), labelStyle) {
            drawRect(cloudColor(0.8f), size = Size(size.width, size.height * 2f / 3f))
        }
        LegendItem(stringResource(R.string.weather_column_precipitation), labelStyle) {
            drawRect(Color(precipitationColor(TYPICAL_RAIN)), topLeft = Offset(0f, size.height / 3f))
        }
        LegendItem(stringResource(R.string.weather_column_wind), labelStyle) {
            val half = size.height / 2f
            val swatch = listOf(0f to half * 0.3f, size.width / 2f to half * 0.8f, size.width to half * 0.3f)
            for (path in violinPaths(swatch, half)) drawPath(path, WIND_COLOR.copy(alpha = WIND_FILL_ALPHA))
            for (path in violinPaths(swatch.map { (x, w) -> x to w + 1.dp.toPx() }, half)) {
                drawPath(path, WIND_COLOR, style = Stroke(width = 1.dp.toPx()))
            }
        }
        LegendItem(stringResource(R.string.weather_legend_now), labelStyle) { drawLegendLine(SKY_INK, dashed = false, vertical = true) }
    }
}

/** The index of the hour in [rows] that [instant] falls into, the last one before it if [rows] has a gap there. */
internal fun hourIndexAt(rows: List<HourRow>, instant: Instant): Int =
    rows.indexOfLast { !it.time.isAfter(instant) }.coerceAtLeast(0)

/** The sky from [time] for [SKY_SAMPLE], its colors top first. */
private class SkySample(val time: Instant, val sky: List<Color>)

private fun skySamples(start: Instant, end: Instant, settings: WidgetSettings): List<SkySample> {
    // The moon's phase hardly changes over the forecast.
    val moonFraction = moonFractionFor(settings, start)
    return generateSequence(start) { it.plus(SKY_SAMPLE) }.takeWhile { it.isBefore(end) }.map { t ->
        SkySample(t, skyColorsAt(t.plus(SKY_SAMPLE.dividedBy(2)), settings, moonFraction).map(::Color).reversed())
    }.toList()
}

/** Clouds of [cover] (0–1): nearly white and see-through when few, dark grey and nearly opaque when overcast. */
private fun cloudColor(cover: Float): Color =
    lerp(CLOUD_FEW, CLOUD_OVERCAST, cover).copy(alpha = CLOUD_MIN_ALPHA + (CLOUD_MAX_ALPHA - CLOUD_MIN_ALPHA) * cover)

/** A label on the right axis at [share] of the sky's height, centered on it ([side] 0), above (−1) or below (1). */
private data class AxisLabel(val layout: TextLayoutResult, val share: Float, val side: Float)

/**
 * Closed outlines symmetric around [center]: through [points] of x and half width (null where a value
 * is missing, which breaks the outline), smoothed as a Catmull-Rom spline that never crosses [center].
 */
internal fun violinPaths(points: List<Pair<Float, Float?>>, center: Float): List<Path> {
    val runs = mutableListOf<MutableList<Offset>>()
    var run: MutableList<Offset>? = null
    for ((x, half) in points) {
        if (half == null) {
            run = null
            continue
        }
        if (run == null) run = mutableListOf<Offset>().also(runs::add)
        run += Offset(x, half)
    }
    return runs.map { pts ->
        Path().apply {
            moveTo(pts.first().x, center - pts.first().y)
            smoothThrough(pts) { center - it }
            lineTo(pts.last().x, center + pts.last().y)
            smoothThrough(pts.asReversed()) { center + it }
            close()
        }
    }
}

/** Continues the path from the first of [pts] through the rest, y given by [y] of each half width. */
private fun Path.smoothThrough(pts: List<Offset>, y: (Float) -> Float) {
    for (i in 0 until pts.lastIndex) {
        val p0 = pts[maxOf(i - 1, 0)]
        val p1 = pts[i]
        val p2 = pts[i + 1]
        val p3 = pts[minOf(i + 2, pts.lastIndex)]
        // Control widths kept at zero or more, so the outline never swings across the middle.
        val w1 = maxOf(0f, p1.y + (p2.y - p0.y) / 6f)
        val w2 = maxOf(0f, p2.y - (p3.y - p1.y) / 6f)
        cubicTo(p1.x + (p2.x - p0.x) / 6f, y(w1), p2.x - (p3.x - p1.x) / 6f, y(w2), p2.x, y(p2.y))
    }
}

/** Polyline through the hourly values, broken where a value is missing, on a dark halo so it reads on any sky. */
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
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx())) else null
    drawPath(path, HALO, style = Stroke(width = 4.dp.toPx(), pathEffect = effect, cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = effect, cap = StrokeCap.Round))
}

private fun DrawScope.drawLegendLine(color: Color, dashed: Boolean, vertical: Boolean = false) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())) else null
    if (vertical) {
        drawLine(color, Offset(size.width / 2f, 0f), Offset(size.width / 2f, size.height), strokeWidth = 2.dp.toPx())
    } else {
        drawLine(color, Offset(0f, size.height / 2f), Offset(size.width, size.height / 2f), strokeWidth = 2.dp.toPx(), pathEffect = effect)
    }
}

/** A legend entry, its [swatch] on a patch of evening sky, as it shows in the chart. */
@Composable
private fun LegendItem(label: String, style: TextStyle, swatch: DrawScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.size(width = 16.dp, height = 10.dp)) {
            drawRect(LEGEND_SKY)
            swatch()
        }
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

private fun seconds(instant: Instant): Double = instant.epochSecond + instant.nano / 1e9

private val HOUR: Duration = Duration.ofHours(1)
private val HALF_HOUR: Duration = Duration.ofMinutes(30)

/** The span the chart's width shows. */
private val WINDOW: Duration = Duration.ofHours(24)

/** How long before the current hour the window starts at first. */
private val LEAD: Duration = Duration.ofHours(2)

private val GUTTER = 36.dp
private val RIGHT_GUTTER = 36.dp
private val RIGHT_TICK = 3.dp
private val SKY_HEIGHT = 210.dp
private val TEMPERATURE_MARGIN = 14.dp
private val AXIS_TICK = 5.dp
private val AXIS_ROW = 16.dp
private val TRACK = 3.dp
private val CHART_HEIGHT = SKY_HEIGHT + AXIS_TICK + 1.dp + AXIS_ROW * 2 + 6.dp + TRACK
private val SKY_SAMPLE: Duration = Duration.ofMinutes(5)
private val SELECTED_POINT = 4.dp
private const val SELECTED_HOUR_ALPHA = 0.22f
private const val MIN_TEMPERATURE_SPAN = 10.0

/** The share of the sky's height a full precipitation bar takes. */
private const val PRECIPITATION_SHARE = 1f / 3f

/** Where the wind's middle lies, as a share of the sky's height from the top, and how far it reaches either way. */
private const val WIND_CENTER = 0.5f
private const val WIND_SHARE = 1f / 6f

/** The wind's scale spans at least this much, so a breeze does not look like a gale. */
private const val MIN_WIND_SCALE_KMH = 40.0
private val WIND_COLOR = Color.White
private const val WIND_FILL_ALPHA = 0.3f

/** The precipitation bars span at least this much, so a drizzle does not fill the panel. */
private const val MIN_PRECIPITATION_SCALE_MM = 2.0

/** Lines and marks on the sky, light on its dark halo. */
private val SKY_INK = Color.White
private val HALO = Color.Black.copy(alpha = 0.35f)
private val AIR_COLOR = Color(SkyPalette.GLOW_GOLD)
private val APPARENT_COLOR = Color(SkyPalette.HORIZON_GOLD)
private val LEGEND_SKY = Color(0xFF34507A)

/** The share of the sky's height clouds of full cover take. */
private const val CLOUD_SHARE = 1f / 3f
private val CLOUD_FEW = Color(SkyPalette.CLOUD_WHITE)
private val CLOUD_OVERCAST = Color(SkyPalette.CLOUD_GREY)
private const val CLOUD_MIN_ALPHA = 0.5f
private const val CLOUD_MAX_ALPHA = 0.8f

private val TYPICAL_RAIN = WeatherCondition(0.0, 1.0, 0.0, 80.0)
