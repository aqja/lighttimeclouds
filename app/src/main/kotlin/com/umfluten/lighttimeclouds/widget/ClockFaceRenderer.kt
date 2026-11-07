package com.umfluten.lighttimeclouds.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import androidx.core.graphics.createBitmap
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.MoonStyle
import com.umfluten.lighttimeclouds.data.TemperaturePosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import java.time.Instant
import java.time.ZonedDateTime

/** Renders the whole clock face (rings, sun marks, ticks, moon, hands) into a square [Bitmap]. */
object ClockFaceRenderer {

    /**
     * Draws the clock for [now], which defaults to the current time in the time zone of the widget's
     * place. With [WidgetSettings.showWeather] the dial makes room for the weather ring (and the
     * hourly temperatures, if enabled), which show [weather] where it covers the shown time span
     * (nothing while there is no forecast yet). With [WidgetSettings.showNextAlarm], [nextAlarm] is
     * marked if it falls into the shown time span.
     */
    fun render(
        context: Context,
        sizePx: Int,
        settings: WidgetSettings,
        weather: WeatherForecast? = null,
        now: ZonedDateTime = ZonedDateTime.now(settings.place.zone),
        nextAlarm: Instant? = null,
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val fullRadius = size / 2f * 0.96f
        val dark = chromeIsDark(context, settings.colorMode)
        val temperatureKind = temperatureKindFor(settings)
        val temperaturesOutside = temperatureKind != null && settings.temperaturePosition == TemperaturePosition.OUTSIDE
        val outsidePaint = temperaturePaint(fullRadius * OUTSIDE_TEMPERATURE_SIZE)
        val gap = fullRadius * OUTSIDE_TEMPERATURE_GAP
        // Everything but an outside temperature scale is drawn within this radius.
        val radius = if (temperaturesOutside) {
            size / 2f - gap - 2f * TEMPERATURE_ANGLES.maxOf { labelExtent(outsidePaint, OUTSIDE_TEMPERATURE_WIDEST, it) }
        } else {
            fullRadius
        }
        val layout = when {
            temperatureKind != null && !temperaturesOutside -> LAYOUT_TEMPERATURE
            settings.showWeather -> LAYOUT_WEATHER
            else -> LAYOUT_PLAIN
        }
        val dial = Dial(
            context, Canvas(bitmap), cx = size / 2f, cy = size / 2f, radius, ringBandsFor(settings), layout, settings, now,
            dark, chromeColor(context, dark),
        )

        with(dial) {
            withRingEndFade {
                drawDayNightRing()
                if (settings.showSolarMarks || settings.showSunriseSunsetMarks) drawSolarMarks()
                if (settings.showMoonInRing && settings.showMoonPathInRing) drawMoonPathInRing()
            }
            if (settings.showSunInRing || settings.showMoonInRing) drawSkyBodies()
            if (settings.showWeather && weather != null) drawWeatherRing(weather)
            drawTicksAndNumerals()
            if (settings.showNextAlarm && nextAlarm != null) drawAlarmTick(nextAlarm)
            if (temperatureKind != null && weather != null) {
                drawTemperatures(
                    weather,
                    temperatureKind,
                    placement = if (temperaturesOutside) {
                        TemperaturePlacement.Outside(radius + gap, outsidePaint.textSize)
                    } else {
                        TemperaturePlacement.Between(radius * layout.temperatureRadius, radius * layout.temperatureSize)
                    },
                )
            }
            if (settings.showMoon) drawMoon()
            drawHands()
            drawCenterPin()
        }
        return bitmap
    }
}

private fun Dial.drawTicksAndNumerals() {
    val tickPaint = hourTickPaint(chromeColor)
    val textPaint = numeralPaint(chromeColor)
    val tickOuter = radius * layout.tickOuter
    val tickInner = radius * layout.hourTickInner
    val numeralRadius = radius * layout.numeralRadius
    val bounds = Rect()

    val labels: List<Pair<Double, String>> = when (settings.displayMode) {
        DisplayMode.TWELVE_HOUR -> (1..12).map { h -> (h * 30.0) to h.toString() }
        DisplayMode.TWENTY_FOUR_HOUR -> (0 until 24 step 2).map { h -> (h * 15.0) to h.toString() }
    }

    for ((angleDeg, label) in labels) {
        drawRadialLine(angleDeg, tickInner, tickOuter, tickPaint)
        textPaint.getTextBounds(label, 0, label.length, bounds)
        val (x, y) = pointOnDial(numeralRadius, angleDeg)
        canvas.drawText(label, x, y + bounds.height() / 2f, textPaint)
    }

    if (settings.showMinuteTicks) {
        drawMinuteTicks()
    }
}

private fun Dial.numeralPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    textSize = radius * layout.numeralSize
    textAlign = Paint.Align.CENTER
}

/**
 * How close to the center (px) the numerals reach at [angleDeg], taking each as wide as two
 * digits; something reaching only up to here stays clear of them.
 */
internal fun Dial.numeralsInnerEdge(angleDeg: Double): Float =
    radius * layout.numeralRadius - labelExtent(numeralPaint(Color.BLACK), "00", angleDeg)

/** Signal red, clearly apart from the chrome and the weather colors on light and dark backgrounds. */
private val ALARM_COLOR = Color.rgb(225, 45, 45)

/** Stroke of the hour ticks; the alarm tick uses the same, only in [ALARM_COLOR]. */
private fun Dial.hourTickPaint(color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color
    strokeWidth = radius * 0.02f
    strokeCap = Paint.Cap.ROUND
}

/** A tick identical to an hour tick except for its red color, at [alarm] if it lies in the shown time span. */
private fun Dial.drawAlarmTick(alarm: Instant) {
    val angle = visibleAngleFor(alarm, settings, now) ?: return
    drawRadialLine(angle, radius * layout.hourTickInner, radius * layout.tickOuter, hourTickPaint(ALARM_COLOR))
}

/** 60 short ticks for the minute hand; every fifth is skipped since hour ticks (both modes) sit on multiples of 30°. */
private fun Dial.drawMinuteTicks() {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        strokeWidth = radius * 0.01f
        strokeCap = Paint.Cap.ROUND
    }
    val outer = radius * layout.tickOuter
    val inner = radius * layout.minuteTickInner
    for (minute in 0 until 60) {
        if (minute % 5 == 0) continue
        drawRadialLine(minute * 6.0, inner, outer, paint)
    }
}

private fun Dial.drawHands() {
    val handTime = now.withSecond(0)
    val hourAngle = dialAngle(handTime, settings.displayMode)
    val minuteAngle = now.minute * 6.0

    val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        strokeWidth = radius * 0.035f
        strokeCap = Paint.Cap.ROUND
    }
    val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        strokeWidth = radius * 0.022f
        strokeCap = Paint.Cap.ROUND
    }

    drawRadialLine(hourAngle, 0f, radius * layout.hourHand, hourPaint)
    if (settings.showMoon && settings.moonStyle == MoonStyle.ON_HOUR_HAND) {
        drawMoonOnHourHand(handTime)
    }
    drawRadialLine(minuteAngle, 0f, radius * layout.minuteHand, minutePaint)
}

private fun Dial.drawCenterPin() {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chromeColor; style = Paint.Style.FILL }
    canvas.drawCircle(cx, cy, radius * 0.03f, paint)
}
