package com.umfluten.lighttimeclouds.widget

import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.MoonlitSky
import com.umfluten.lighttimeclouds.sky.SkyGradientTable
import com.umfluten.lighttimeclouds.sky.SkyProjection
import java.time.Duration
import kotlin.math.min

/**
 * Draws [content] (the ring and what lies in it) so that it fades out to transparent over the
 * last [WidgetSettings.ringEndFadeDeg] dial degrees before the shown window's seam, marking
 * where the window ends. The fade never starts before the current time, so the part of the
 * ring at the hand always shows in full.
 */
internal fun Dial.withRingEndFade(content: () -> Unit) {
    val mapper = mapperFor(settings)
    val local = now.toLocalDateTime()
    val windowStart = mapper.localWindowStart(local)
    // Wall-clock time left until the seam, in dial degrees, like the hour hand counts it.
    val untilSeam = Duration.between(local, windowStart.plusHours(mapper.dialHours.toLong())).seconds /
        (mapper.dialHours * 3600.0) * 360.0
    val fade = min(settings.ringEndFadeDeg.toDouble(), untilSeam)
    if (fade <= 0.0) {
        content()
        return
    }
    val layer = canvas.saveLayer(null, null)
    content()
    val seamAngle = dialAngle(windowStart, settings.displayMode)
    val mask = Paint().apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        // Positions count clockwise from the seam, where the window starts, round to the seam
        // again, where it ends. The sweep starts at 3 o'clock, dial angles at 12 o'clock.
        shader = SweepGradient(
            cx, cy,
            intArrayOf(Color.BLACK, Color.BLACK, Color.TRANSPARENT),
            floatArrayOf(0f, (1.0 - fade / 360.0).toFloat(), 1f),
        ).apply { setLocalMatrix(Matrix().apply { setRotate((seamAngle - 90.0).toFloat(), cx, cy) }) }
    }
    canvas.drawPaint(mask)
    canvas.restoreToCount(layer)
}

internal fun Dial.drawDayNightRing() {
    val ringOuter = radius * bands.sunOuter
    val ringInner = radius * bands.sunInner
    val ringMid = (ringOuter + ringInner) / 2f
    val rect = RectF(cx - ringMid, cy - ringMid, cx + ringMid, cy + ringMid)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ringOuter - ringInner
    }
    // Per sample a radial gradient from the horizon colors to the upper sky (inner to outer edge,
    // or reversed per [WidgetSettings.skyHorizon]), stops spread evenly across the band.
    val skyPositions = FloatArray(SKY_STOPS) { k ->
        (ringInner + (ringOuter - ringInner) * k / (SKY_STOPS - 1)) / ringOuter
    }
    val latitude = settings.place.latitude
    val longitude = settings.place.longitude
    // The moon lights the sky it stands in; its phase hardly changes across the ring.
    val moonFraction = if (settings.showMoonInRing) SunMoonCalculator.moonIllumination(now.toInstant()).fraction else null
    for (i in 0 until RING_SAMPLES) {
        val a0 = 360.0 * i / RING_SAMPLES
        val a1 = 360.0 * (i + 1) / RING_SAMPLES
        val mid = (a0 + a1) / 2.0
        val instant = resolveInstantForAngle(mid, settings, now)
        val altitude = SunMoonCalculator.sunAltitudeDegrees(instant, latitude, longitude)
        val top = settings.skyTopElevationDeg.toDouble()
        // With the sun up, moonlight adds nothing the eye could see.
        val sky = if (moonFraction != null && altitude < 0.0) {
            val moonAltitude = SunMoonCalculator.moonAltitudeDegrees(instant, latitude, longitude)
            moonlitSkyColors(altitude, moonAltitude, moonFraction, top)
        } else {
            skyColors(altitude, top)
        }
        val colors = if (settings.skyHorizon == SkyHorizon.OUTER) sky.reversedArray() else sky
        paint.shader = RadialGradient(cx, cy, ringOuter, colors, skyPositions, Shader.TileMode.CLAMP)
        canvas.drawArc(rect, (a0 - 90.0).toFloat(), (a1 - a0).toFloat() + 0.75f, false, paint)
    }
}

/** Gradient stops across the band. */
private const val SKY_STOPS = 24

/**
 * [SKY_STOPS] sky colors for the sun at [altitudeDeg] as opaque ARGB, from the horizon up to
 * [topElevationDeg] above it, evenly spaced in [SkyProjection]: the physical model as the eye
 * sees it ([SkyGradientTable]), from daylight through the glow and the blue of twilight to the
 * night sky's own faint glow.
 */
internal fun skyColors(altitudeDeg: Double, topElevationDeg: Double): IntArray =
    SkyGradientTable.colors(altitudeDeg, topElevationDeg, SKY_STOPS).map { it or OPAQUE }.toIntArray()

/** [skyColors] with the sky also lit by the moon at [moonAltitudeDeg], lit by the share [fraction] ([MoonlitSky]). */
private fun moonlitSkyColors(sunAltitudeDeg: Double, moonAltitudeDeg: Double, fraction: Double, topElevationDeg: Double): IntArray =
    MoonlitSky.colors(sunAltitudeDeg, moonAltitudeDeg, fraction, topElevationDeg, SKY_STOPS).map { it or OPAQUE }.toIntArray()

/**
 * Marks solar noon/midnight and sunrise/sunset across the ring, per settings. All strokes use
 * [MARK_COLOR]. In 12h mode every dial position stands for two times 12h apart, so only
 * events inside the currently shown 12h window are drawn.
 */
internal fun Dial.drawSolarMarks() {
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = MARK_COLOR
        strokeWidth = radius * 0.012f
        strokeCap = Paint.Cap.BUTT
    }
    val inner = radius * bands.sunInner
    val outer = radius * bands.sunOuter
    for (dayOffset in -1L..1L) {
        val times = SunMoonCalculator.sunTimes(now.plusDays(dayOffset).toInstant(), settings.place.latitude, settings.place.longitude)
        val events = buildList {
            if (settings.showSolarMarks) {
                add(times.solarNoon)
                add(times.solarMidnight)
            }
            if (settings.showSunriseSunsetMarks) {
                // Null during polar day/night, when the sun never crosses the horizon.
                add(times.sunrise)
                add(times.sunset)
            }
        }
        for (instant in events.filterNotNull()) {
            val angle = visibleAngleFor(instant, settings, now) ?: continue
            drawRadialLine(angle, inner, outer, paint)
        }
    }
}

/**
 * Mid grey between the bright and the dark sky, with about the same WCAG contrast to both at the
 * marks: 2.5:1 at noon, 2.6:1 at midnight, 3.1:1 at sunrise and sunset, 1.5:1 under a high full moon.
 * Used for sun marks and the moon hand/path, and by the config app's time list to match.
 */
internal val MARK_COLOR = Color.rgb(100, 100, 100)

/** A single color for a sun altitude under [settings], the sky about halfway up the band, for the config app's weather chart. */
internal fun ringColor(altitudeDeg: Double, settings: WidgetSettings): Int =
    skyColors(altitudeDeg, settings.skyTopElevationDeg.toDouble())[SKY_STOPS / 2]
