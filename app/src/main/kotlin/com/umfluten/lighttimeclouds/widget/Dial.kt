package com.umfluten.lighttimeclouds.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import com.umfluten.lighttimeclouds.astro.DialTimeMapper
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * One clock face being drawn: what nearly everything on it needs, so the drawing functions in this
 * package are extensions of it. [cx], [cy] and [radius] are in pixels; most sizes count as fractions
 * of [radius]. [dark] says the widget sits on a dark background, [chromeColor] colors numerals,
 * ticks and hands.
 */
internal class Dial(
    val context: Context,
    val canvas: Canvas,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val bands: RingBands,
    val layout: DialLayout,
    val settings: WidgetSettings,
    val now: ZonedDateTime,
    val dark: Boolean,
    val chromeColor: Int,
)

/**
 * Radial extent (fractions of the dial radius) of the day/night ring and the weather ring.
 * Everything else lies inside 0.75, so the inner dial layout does not depend on these.
 */
internal class RingBands(val sunInner: Float, val sunOuter: Float, val weatherInner: Float, val weatherOuter: Float)

private val BANDS_WEATHER_INSIDE = RingBands(sunInner = 0.82f, sunOuter = 1f, weatherInner = 0.75f, weatherOuter = 0.81f)

/** Weather outermost: the day/night ring keeps its width and moves inward by the weather ring's width. */
private val BANDS_WEATHER_OUTSIDE = RingBands(sunInner = 0.75f, sunOuter = 0.93f, weatherInner = 0.94f, weatherOuter = 1f)

internal fun ringBandsFor(settings: WidgetSettings): RingBands =
    if (settings.showWeather && settings.weatherRingPosition == WeatherRingPosition.OUTSIDE) {
        BANDS_WEATHER_OUTSIDE
    } else {
        BANDS_WEATHER_INSIDE
    }

/** Radii (fractions of the dial radius) and sizes of everything inside the ring(s). */
internal class DialLayout(
    val tickOuter: Float,
    val hourTickInner: Float,
    val minuteTickInner: Float,
    val temperatureRadius: Float,
    val temperatureSize: Float,
    val numeralRadius: Float,
    val numeralSize: Float,
    val hourHand: Float,
    val minuteHand: Float,
    val moonHorizon: Float,
    val moonAmplitude: Float,
)

internal val LAYOUT_PLAIN = DialLayout(
    tickOuter = 0.80f, hourTickInner = 0.74f, minuteTickInner = 0.77f,
    temperatureRadius = 0f, temperatureSize = 0f,
    numeralRadius = 0.62f, numeralSize = 0.16f,
    hourHand = 0.48f, minuteHand = 0.72f,
    moonHorizon = 0.30f, moonAmplitude = 0.18f,
)

/** Everything moves inward to make room for the weather ring. */
internal val LAYOUT_WEATHER = DialLayout(
    tickOuter = 0.735f, hourTickInner = 0.68f, minuteTickInner = 0.71f,
    temperatureRadius = 0f, temperatureSize = 0f,
    numeralRadius = 0.575f, numeralSize = 0.15f,
    hourHand = 0.46f, minuteHand = 0.66f,
    moonHorizon = 0.30f, moonAmplitude = 0.18f,
)

/** Temperatures midway between the numerals, just outside their circle; numerals and moon orbit move further in. */
internal val LAYOUT_TEMPERATURE = DialLayout(
    tickOuter = 0.735f, hourTickInner = 0.68f, minuteTickInner = 0.71f,
    temperatureRadius = 0.605f, temperatureSize = 0.082f,
    numeralRadius = 0.50f, numeralSize = 0.135f,
    hourHand = 0.40f, minuteHand = 0.62f,
    moonHorizon = 0.26f, moonAmplitude = 0.15f,
)

internal const val RING_SAMPLES = 360

internal const val OPAQUE = 0xFF shl 24

/** The day/night ring's area, to clip what is drawn in it. */
internal fun Dial.ringBandPath(inset: Float = 0f) = Path().apply {
    fillType = Path.FillType.EVEN_ODD
    addCircle(cx, cy, radius * bands.sunInner + inset, Path.Direction.CW)
    addCircle(cx, cy, radius * bands.sunOuter - inset, Path.Direction.CW)
}

/** The point [r] px from the center at dial [angleDeg] (0 = top, clockwise). */
internal fun Dial.pointOnDial(r: Float, angleDeg: Double): Pair<Float, Float> {
    val rad = Math.toRadians(angleDeg - 90.0)
    return cx + (cos(rad) * r).toFloat() to cy + (sin(rad) * r).toFloat()
}

/** A line along dial [angleDeg] from [inner] to [outer] px from the center: ticks, marks and hands. */
internal fun Dial.drawRadialLine(angleDeg: Double, inner: Float, outer: Float, paint: Paint) {
    val (x0, y0) = pointOnDial(inner, angleDeg)
    val (x1, y1) = pointOnDial(outer, angleDeg)
    canvas.drawLine(x0, y0, x1, y1, paint)
}

/** Dial angle at which [instant] appears on the ring right now, or null if it is outside the shown window. */
internal fun visibleAngleFor(instant: Instant, settings: WidgetSettings, now: ZonedDateTime): Double? {
    val angle = dialAngle(instant.atZone(now.zone), settings.displayMode)
    val shown = resolveInstantForAngle(angle, settings, now)
    return if (Duration.between(shown, instant).abs() < Duration.ofMinutes(2)) angle else null
}

/** Maps a dial angle to the real instant whose sun state should color that position: the one inside the shown window. */
internal fun resolveInstantForAngle(angleDegrees: Double, settings: WidgetSettings, now: ZonedDateTime): Instant =
    mapperFor(settings).resolve(now, angleDegrees).toInstant()

internal fun mapperFor(settings: WidgetSettings): DialTimeMapper {
    val dialHours = when (settings.displayMode) {
        DisplayMode.TWELVE_HOUR -> 12
        DisplayMode.TWENTY_FOUR_HOUR -> 24
    }
    return when (settings.switchStrategyType) {
        SwitchStrategyType.FIXED_TIMES -> {
            val minutes = when (settings.displayMode) {
                DisplayMode.TWELVE_HOUR -> listOf(settings.switchTime1Minutes, settings.switchTime2Minutes)
                DisplayMode.TWENTY_FOUR_HOUR -> listOf(settings.switchTime24hMinutes)
            }
            DialTimeMapper.FixedTimes(minutes.map { LocalTime.ofSecondOfDay((it * 60L).coerceIn(0, 86399)) }, dialHours)
        }
        SwitchStrategyType.TRAILING -> DialTimeMapper.Trailing(dialHours)
    }
}

/** The time span the dial currently shows: the active 12h or 24h window (an hour more or less across a DST change). */
internal fun shownWindow(settings: WidgetSettings, now: ZonedDateTime): Pair<Instant, Instant> {
    val mapper = mapperFor(settings)
    return mapper.windowStart(now).toInstant() to mapper.windowEnd(now).toInstant()
}

/** Dial angle (0..360, 0 = top) at which [time] appears on the dial. */
internal fun dialAngle(time: ZonedDateTime, mode: DisplayMode): Double = dialAngle(time.toLocalDateTime(), mode)

/** Dial angle (0..360, 0 = top) at which the wall-clock [time] appears on the dial. */
internal fun dialAngle(time: LocalDateTime, mode: DisplayMode): Double {
    val hours = time.hour + time.minute / 60.0 + time.second / 3600.0
    return when (mode) {
        DisplayMode.TWELVE_HOUR -> (hours % 12.0) * 30.0
        DisplayMode.TWENTY_FOUR_HOUR -> hours * 15.0
    }
}

/** Color of numerals, ticks and hands under [settings]; also used for the place name next to the clock. */
internal fun chromeColor(context: Context, settings: WidgetSettings): Int =
    chromeColor(context, chromeIsDark(context, settings.colorMode))

/** True when the widget sits on a dark background, so the chrome must be light. [ColorMode.AUTO] follows the system dark mode. */
internal fun chromeIsDark(context: Context, mode: ColorMode): Boolean = when (mode) {
    ColorMode.DARK -> true
    ColorMode.LIGHT -> false
    ColorMode.AUTO ->
        (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

/**
 * Color of numerals, ticks and hands: the Material You accent derived from the wallpaper
 * (Android 12+), in a light shade on dark backgrounds and a dark shade on light ones.
 * Older versions fall back to near-white/near-black.
 */
internal fun chromeColor(context: Context, dark: Boolean): Int =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        context.getColor(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_800)
    } else if (dark) {
        Color.rgb(235, 235, 240)
    } else {
        Color.rgb(25, 25, 30)
    }
