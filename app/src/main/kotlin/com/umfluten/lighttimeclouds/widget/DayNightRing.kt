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
import com.umfluten.lighttimeclouds.data.RingStyle
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
    // Sky style: per sample a radial gradient from the horizon colors to the upper sky (inner to
    // outer edge, or reversed per [WidgetSettings.skyHorizon]), stops spread evenly across the band.
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
        if (settings.ringStyle == RingStyle.SKY) {
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
        } else {
            paint.color = ringColor(altitude, settings)
        }
        canvas.drawArc(rect, (a0 - 90.0).toFloat(), (a1 - a0).toFloat() + 0.75f, false, paint)
    }
}

/** Gradient stops across the band in the sky style. */
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
 * Mid grey between the bright and the dark ring. WCAG contrast at the marks: about 3.5:1 on the
 * fixed colors by day and by night; on the sky about 2.4:1 at noon, 2.9:1 at sunrise and sunset,
 * 2.8:1 at midnight and 1.6:1 under a high full moon.
 * Used for sun marks and the moon hand/path, and by the config app's time list to match.
 */
internal val MARK_COLOR = Color.rgb(105, 105, 105)

private val GRADIENT_STOPS: List<Pair<Double, Int>> = listOf(
    -90.0 to Color.rgb(5, 8, 20),
    -18.0 to Color.rgb(10, 15, 35),
    -12.0 to Color.rgb(25, 35, 70),
    -6.0 to Color.rgb(70, 60, 110),
    -0.833 to Color.rgb(230, 140, 90),
    6.0 to Color.rgb(255, 210, 140),
    90.0 to Color.rgb(135, 206, 250),
)

private fun gradientColor(altitudeDeg: Double): Int {
    val clamped = altitudeDeg.coerceIn(GRADIENT_STOPS.first().first, GRADIENT_STOPS.last().first)
    for (i in 0 until GRADIENT_STOPS.size - 1) {
        val (a0, c0) = GRADIENT_STOPS[i]
        val (a1, c1) = GRADIENT_STOPS[i + 1]
        if (clamped in a0..a1) {
            val t = if (a1 > a0) (clamped - a0) / (a1 - a0) else 0.0
            return lerpColor(c0, c1, t)
        }
    }
    return GRADIENT_STOPS.last().second
}

internal fun lerpColor(c0: Int, c1: Int, t: Double): Int {
    val tf = t.toFloat().coerceIn(0f, 1f)
    fun ch(a: Int, b: Int) = (a + (b - a) * tf).toInt().coerceIn(0, 255)
    return Color.rgb(
        ch(Color.red(c0), Color.red(c1)),
        ch(Color.green(c0), Color.green(c1)),
        ch(Color.blue(c0), Color.blue(c1)),
    )
}

/**
 * One fixed color per sky phase, for [RingStyle.BANDS]: [gradientColor] at a representative
 * altitude inside each phase.
 */
private fun bandColor(state: SunMoonCalculator.SkyState): Int = gradientColor(
    when (state) {
        SunMoonCalculator.SkyState.NIGHT -> -18.0
        SunMoonCalculator.SkyState.ASTRONOMICAL_TWILIGHT -> -15.0
        SunMoonCalculator.SkyState.NAUTICAL_TWILIGHT -> -9.0
        SunMoonCalculator.SkyState.CIVIL_TWILIGHT -> -3.4
        SunMoonCalculator.SkyState.GOLDEN_HOUR -> 2.6
        SunMoonCalculator.SkyState.DAY -> 30.0
    },
)

/**
 * Ring color for a sun altitude under [settings] (ring style only - the color mode never tints
 * the ring); also used by the config app's weather chart.
 */
internal fun ringColor(altitudeDeg: Double, settings: WidgetSettings): Int = when (settings.ringStyle) {
    // A single representative color: the sky about halfway up the band.
    RingStyle.SKY -> skyColors(altitudeDeg, settings.skyTopElevationDeg.toDouble())[SKY_STOPS / 2]
    RingStyle.BANDS -> bandColor(SunMoonCalculator.classify(altitudeDeg))
}

/**
 * The ring's colors for a sun altitude under [settings], from the horizon up to
 * [topElevationDeg] (by default the far edge's elevation, whichever side of the ring that is):
 * the sky's gradient in the sky style, a single color with fixed colors. The config app's time
 * list shows them as a slice of the ring.
 */
internal fun ringColorsUpward(
    altitudeDeg: Double,
    settings: WidgetSettings,
    topElevationDeg: Double = settings.skyTopElevationDeg.toDouble(),
): IntArray = when (settings.ringStyle) {
    RingStyle.SKY -> skyColors(altitudeDeg, topElevationDeg)
    RingStyle.BANDS -> intArrayOf(bandColor(SunMoonCalculator.classify(altitudeDeg)))
}
