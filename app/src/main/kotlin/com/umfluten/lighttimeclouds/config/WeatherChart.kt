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
import androidx.compose.ui.graphics.Shadow
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherCondition
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
 * at the bottom, clouds hang from its top, precipitation rises from the horizon, and air and
 * apparent temperature run across it as two lines. The chart shows [WINDOW] at a time; swiping
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
    val skyLabelStyle = MaterialTheme.typography.labelSmall.copy(color = SKY_INK.copy(alpha = 0.85f), shadow = Shadow(HALO.copy(alpha = 0.8f), blurRadius = 4f))
    val gridColor = MaterialTheme.colorScheme.outlineVariant
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val thumbColor = MaterialTheme.colorScheme.primary

    val temperatures = rows.flatMap { listOfNotNull(it.temperatureC, it.apparentTemperatureC) }
    val (tMin, tMax, tStep) = temperatureScale(temperatures)
    val maxPrecipitation = ceil(maxOf(MIN_PRECIPITATION_SCALE_MM, rows.maxOf { it.precipitation.precipitationMm }))
    val precipitationScaleLabel = stringResource(R.string.weather_amount, maxPrecipitation.toInt().toString())
    val sky = remember(start, end, settings) { skySamples(rows, start, end, settings) }

    // The window's left edge in epoch seconds, kept by time so it survives a new forecast; NaN until
    // the user moves it, and the window starts shortly before now.
    var chosenWindowStart by rememberSaveable(settings.place) { mutableDoubleStateOf(Double.NaN) }
    val firstStart = seconds(start)
    val lastStart = maxOf(firstStart, seconds(end) - WINDOW.seconds)
    val defaultStart = seconds(now.toInstant().truncatedTo(ChronoUnit.HOURS)) - LEAD.seconds
    fun windowStart(): Double =
        (if (chosenWindowStart.isNaN()) defaultStart else chosenWindowStart).coerceIn(firstStart, lastStart)

    val gutter = with(LocalDensity.current) { GUTTER.toPx() }
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
            .onSizeChanged { plotWidth = (it.width - gutter).coerceAtLeast(1f) }
            .scrollable(scrollState, Orientation.Horizontal)
            .pointerInput(rows, gutter) {
                detectTapGestures { tap ->
                    if (tap.x < gutter || tap.y > SKY_HEIGHT.toPx() + AXIS_TICK.toPx()) return@detectTapGestures
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
                val cloudBrushes = sky.map { cloudBrush(it, skyBottom) }
                val precipitationLabel = measurer.measure(precipitationScaleLabel, skyLabelStyle)
                onDrawBehind {
                    val from = windowStart()
                    val perPx = WINDOW.seconds / (size.width - left)
                    fun x(instant: Instant): Float = (left + (seconds(instant) - from) / perPx).toFloat()
                    val tempTop = TEMPERATURE_MARGIN.toPx()
                    val tempBottom = skyBottom - TEMPERATURE_MARGIN.toPx()
                    fun y(celsius: Double): Float = (tempBottom - (celsius - tMin) / (tMax - tMin) * (tempBottom - tempTop)).toFloat()
                    val precipitationTop = skyBottom * (1f - PRECIPITATION_SHARE)
                    val visible = Instant.ofEpochSecond(from.toLong() - SKY_SAMPLE.seconds)..
                        Instant.ofEpochSecond((from + WINDOW.seconds).toLong() + HOUR.seconds)

                    clipRect(left, 0f, size.width, skyBottom) {
                        sky.forEachIndexed { i, sample ->
                            if (sample.time !in visible) return@forEachIndexed
                            // Edges on whole pixels, so neighboring translucent clouds neither overlap nor leave gaps.
                            val x0 = x(sample.time).roundToInt().toFloat()
                            val x1 = x(sample.time.plus(SKY_SAMPLE)).roundToInt().toFloat()
                            drawRect(skyBrushes[i], Offset(x0, 0f), Size(x1 - x0, skyBottom))
                            drawRect(cloudBrushes[i], Offset(x0, 0f), Size(x1 - x0, skyBottom))
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
                        // Scale of the precipitation bars, fixed at the right edge.
                        drawLine(
                            SKY_INK.copy(alpha = 0.35f),
                            Offset(left, precipitationTop), Offset(size.width, precipitationTop),
                            strokeWidth = 1f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(2.dp.toPx(), 3.dp.toPx())),
                        )
                        drawText(
                            precipitationLabel,
                            topLeft = Offset(
                                size.width - precipitationLabel.size.width - 4.dp.toPx(),
                                precipitationTop - precipitationLabel.size.height,
                            ),
                        )

                        // Temperature grid.
                        var grid = tMin
                        while (grid <= tMax + 1e-6) {
                            drawLine(SKY_INK.copy(alpha = 0.18f), Offset(left, y(grid)), Offset(size.width, y(grid)), strokeWidth = 1f)
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
                    clipRect(left, skyBottom, size.width, size.height) {
                        var tick = from.toLong() / 3600 * 3600
                        while (tick <= from + WINDOW.seconds + 3600) {
                            val local = Instant.ofEpochSecond(tick).atZone(now.zone)
                            val tx = x(local.toInstant())
                            val length = if (local.hour % 3 == 0) AXIS_TICK.toPx() else AXIS_TICK.toPx() / 2f
                            drawLine(gridColor, Offset(tx, skyBottom), Offset(tx, skyBottom + length), strokeWidth = 1.5f)
                            if (local.hour % 6 == 0) {
                                val label = measurer.measure(formats.timeOfDay(local.hour * 60), labelStyle)
                                val lx = tx - label.size.width / 2f
                                if (lx >= left && lx + label.size.width <= size.width) {
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
                    val trackWidth = size.width - left
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
            drawRect(Brush.verticalGradient(listOf(CLOUD_DAY, CLOUD_DAY.copy(alpha = 0f))))
        }
        LegendItem(stringResource(R.string.weather_column_precipitation), labelStyle) {
            drawRect(Color(precipitationColor(TYPICAL_RAIN)), topLeft = Offset(0f, size.height / 3f))
        }
        LegendItem(stringResource(R.string.weather_legend_now), labelStyle) { drawLegendLine(SKY_INK, dashed = false, vertical = true) }
    }
}

/** The index of the hour in [rows] that [instant] falls into, the last one before it if [rows] has a gap there. */
internal fun hourIndexAt(rows: List<HourRow>, instant: Instant): Int =
    rows.indexOfLast { !it.time.isAfter(instant) }.coerceAtLeast(0)

/**
 * Cloud cover in percent at [instant], between the hourly values of [rows] (each at its full hour);
 * before the first and after the last the nearest value.
 */
internal fun cloudCoverAt(rows: List<HourRow>, instant: Instant): Double {
    val after = rows.indexOfFirst { it.time.isAfter(instant) }
    if (after == 0) return rows.first().cloudCoverPercent
    if (after < 0) return rows.last().cloudCoverPercent
    val a = rows[after - 1]
    val b = rows[after]
    val share = Duration.between(a.time, instant).toMillis().toDouble() / Duration.between(a.time, b.time).toMillis()
    return a.cloudCoverPercent + (b.cloudCoverPercent - a.cloudCoverPercent) * share
}

/** The sky from [time] for [SKY_SAMPLE]: its colors top first, the sun's altitude and the cloud cover. */
private class SkySample(val time: Instant, val sky: List<Color>, val sunAltitude: Double, val cloudCover: Double)

private fun skySamples(rows: List<HourRow>, start: Instant, end: Instant, settings: WidgetSettings): List<SkySample> {
    val place = settings.place
    // The moon's phase hardly changes over the forecast.
    val moonFraction = moonFractionFor(settings, start)
    return generateSequence(start) { it.plus(SKY_SAMPLE) }.takeWhile { it.isBefore(end) }.map { t ->
        val mid = t.plus(SKY_SAMPLE.dividedBy(2))
        SkySample(
            time = t,
            sky = skyColorsAt(mid, settings, moonFraction).map(::Color).reversed(),
            sunAltitude = SunMoonCalculator.sunAltitudeDegrees(mid, place.latitude, place.longitude),
            cloudCover = cloudCoverAt(rows, mid),
        )
    }.toList()
}

/**
 * Clouds hanging from the top of a sky [height] high: the more cover, the deeper and denser; light
 * grey by day, darker by night, so they stand out from either sky.
 */
private fun cloudBrush(sample: SkySample, height: Float): Brush {
    val cover = (sample.cloudCover / 100.0).coerceIn(0.0, 1.0).toFloat()
    if (cover < 0.01f) return Brush.verticalGradient(listOf(Color.Transparent, Color.Transparent))
    val daylight = ((sample.sunAltitude + 12.0) / 18.0).coerceIn(0.0, 1.0).toFloat()
    val color = lerp(CLOUD_NIGHT, CLOUD_DAY, daylight)
    val alpha = CLOUD_MIN_ALPHA + (CLOUD_MAX_ALPHA - CLOUD_MIN_ALPHA) * cover
    val depth = height * (CLOUD_MIN_DEPTH + (CLOUD_MAX_DEPTH - CLOUD_MIN_DEPTH) * cover)
    return Brush.verticalGradient(
        0f to color.copy(alpha = alpha),
        0.45f to color.copy(alpha = alpha * 0.75f),
        1f to color.copy(alpha = 0f),
        startY = 0f,
        endY = depth,
    )
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
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())) else null
    drawPath(path, HALO, style = Stroke(width = 4.dp.toPx(), pathEffect = effect, cap = StrokeCap.Round))
    drawPath(path, color, style = Stroke(width = 2.dp.toPx(), pathEffect = effect, cap = StrokeCap.Round))
}

private fun DrawScope.drawLegendLine(color: Color, dashed: Boolean, vertical: Boolean = false) {
    val effect = if (dashed) PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())) else null
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

/** The span the chart's width shows. */
private val WINDOW: Duration = Duration.ofHours(24)

/** How long before the current hour the window starts at first. */
private val LEAD: Duration = Duration.ofHours(2)

private val GUTTER = 36.dp
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
private const val PRECIPITATION_SHARE = 0.3f

/** The precipitation bars span at least this much, so a drizzle does not fill the panel. */
private const val MIN_PRECIPITATION_SCALE_MM = 2.0

/** Lines and marks on the sky, light on its dark halo. */
private val SKY_INK = Color.White
private val HALO = Color.Black.copy(alpha = 0.35f)
private val AIR_COLOR = Color.White
private val APPARENT_COLOR = Color(0xFFFFD27A)
private val LEGEND_SKY = Color(0xFF34507A)

private val CLOUD_DAY = Color(0xFFEEF0F4)
private val CLOUD_NIGHT = Color(0xFF5A606C)
private const val CLOUD_MIN_ALPHA = 0.35f
private const val CLOUD_MAX_ALPHA = 0.92f
private const val CLOUD_MIN_DEPTH = 0.15f
private const val CLOUD_MAX_DEPTH = 0.6f

private val TYPICAL_RAIN = WeatherCondition(0.0, 1.0, 0.0, 80.0)
