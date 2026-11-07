package com.umfluten.lighttimeclouds.widget

import android.graphics.Paint
import android.graphics.Rect
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.TemperatureDisplay
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.TemperatureKind
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Temperature scale outside the rings: upright labels of [OUTSIDE_TEMPERATURE_SIZE] (fraction of
 * the full radius), [OUTSIDE_TEMPERATURE_GAP] away from the outermost ring. The dial shrinks just
 * enough for the widest possible label ([OUTSIDE_TEMPERATURE_WIDEST]) to fit at every position.
 */
internal const val OUTSIDE_TEMPERATURE_SIZE = 0.075f
internal const val OUTSIDE_TEMPERATURE_GAP = 0.015f
internal const val OUTSIDE_TEMPERATURE_WIDEST = "\u221288°"

internal fun temperatureKindFor(settings: WidgetSettings): TemperatureKind? =
    if (!settings.showWeather) null else when (settings.temperatureDisplay) {
        TemperatureDisplay.NONE -> null
        TemperatureDisplay.AIR -> TemperatureKind.AIR
        TemperatureDisplay.APPARENT -> TemperatureKind.APPARENT
    }

/** Labels sit midway between the numerals, which are every 30° in both modes. */
internal val TEMPERATURE_ANGLES = List(12) { it * 30.0 + 15.0 }

/** Where and how large ([textSize], px) the upright temperature labels are drawn. */
internal sealed class TemperaturePlacement(val textSize: Float) {
    /** Centered on a circle of [radius] px, between the numerals. */
    class Between(val radius: Float, textSize: Float) : TemperaturePlacement(textSize)

    /** Just outside the circle of [edge] px (the outermost ring), each label as close as its width allows. */
    class Outside(val edge: Float, textSize: Float) : TemperaturePlacement(textSize)
}

internal fun temperaturePaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.textSize = textSize
    textAlign = Paint.Align.CENTER
}

/** Height of the digits, used to center labels with and without minus sign alike. */
private fun digitHeight(paint: Paint): Float = Rect().also { paint.getTextBounds("0", 0, 1, it) }.height().toFloat()

/**
 * How far an upright [label] reaches from its center towards the dial center when placed at
 * [angleDeg]: half its width sideways, half its height at top and bottom, mixed in between.
 */
internal fun labelExtent(paint: Paint, label: String, angleDeg: Double): Float {
    val rad = Math.toRadians(angleDeg)
    return (abs(sin(rad)) * paint.measureText(label) / 2 + abs(cos(rad)) * digitHeight(paint) / 2).toFloat()
}

/** Time span a temperature label stands for: [mid] is the time shown at the label's position. */
internal class LabelSpan(val angle: Double, val mid: Instant, val from: Instant, val to: Instant)

/**
 * For each label position (midway between two numerals), the time shown there and the span the
 * label averages: the numerals' spacing (1 h in 12h mode, 2 h in 24h mode) centered on that
 * time, cut to the shown window. The cut matters where the window's seam (oldest next to newest
 * time) lies between two numerals, as in the trailing mode: the label then covers only the
 * side of the seam it sits on, matching the ring below it.
 */
internal fun temperatureLabelSpans(settings: WidgetSettings, now: ZonedDateTime): List<LabelSpan> {
    val spacing = when (settings.displayMode) {
        DisplayMode.TWELVE_HOUR -> Duration.ofHours(1)
        DisplayMode.TWENTY_FOUR_HOUR -> Duration.ofHours(2)
    }
    val (windowStart, windowEnd) = shownWindow(settings, now)
    return TEMPERATURE_ANGLES.map { angle ->
        val mid = resolveInstantForAngle(angle, settings, now)
        val half = spacing.dividedBy(2)
        val from = maxOf(mid.minus(half), windowStart)
        val to = minOf(mid.plus(half), windowEnd)
        LabelSpan(angle, mid, from, to)
    }
}

/**
 * The rounded mean temperature of each [temperatureLabelSpans] span, midway between the
 * numerals. Like the weather ring, labels for past times are dimmed and all fade as the
 * forecast ages.
 */
internal fun Dial.drawTemperatures(forecast: WeatherForecast, kind: TemperatureKind, placement: TemperaturePlacement) {
    val freshness = forecast.freshnessAlpha(now.toInstant())
    if (freshness <= 0f) return
    val paint = temperaturePaint(placement.textSize)
    val halfHeight = digitHeight(paint) / 2f
    for (span in temperatureLabelSpans(settings, now)) {
        if (!span.to.isAfter(span.from)) continue
        val angle = span.angle
        val temperature = forecast.meanTemperature(span.from, span.to, kind) ?: continue
        val past = span.mid.isBefore(now.toInstant())
        paint.color = withAlphaFactor(chromeColor, if (past) freshness * WEATHER_PAST_ALPHA else freshness)
        val label = formatTemperature(temperature)
        val distance = when (placement) {
            is TemperaturePlacement.Between -> placement.radius
            is TemperaturePlacement.Outside -> placement.edge + labelExtent(paint, label, angle)
        }
        val (x, y) = pointOnDial(distance, angle)
        canvas.drawText(label, x, y + halfHeight, paint)
    }
}

/** Whole degrees with a real minus sign; "-0" becomes "0". */
internal fun formatTemperature(celsius: Double): String {
    val rounded = celsius.roundToInt()
    return if (rounded < 0) "\u2212${-rounded}°" else "$rounded°"
}
