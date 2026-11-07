package com.umfluten.lighttimeclouds.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import androidx.core.graphics.withClip
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.MoonTable
import com.umfluten.lighttimeclouds.sky.MoonlitSky
import com.umfluten.lighttimeclouds.sky.SkyModel
import com.umfluten.lighttimeclouds.sky.SkyProjection
import com.umfluten.lighttimeclouds.sky.SunTable
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * The day/night ring read as the sky: [horizon] and [top] are the radii (px) of the horizon edge
 * and the far edge, which stands for [topElevation] degrees above the horizon, with the
 * elevations in between spread as by [SkyProjection], like the sky colors.
 */
private class SkyBand(val horizon: Float, val top: Float, val topElevation: Double) {
    /**
     * Distance from the center for [altitudeDeg], not limited to the band, but at most [overshoot]
     * band widths beyond either edge.
     */
    fun radiusFor(altitudeDeg: Double, overshoot: Double = Double.POSITIVE_INFINITY): Float {
        val fraction = SkyProjection.fraction(altitudeDeg, topElevation).coerceIn(-overshoot, 1 + overshoot)
        return horizon + (top - horizon) * fraction.toFloat()
    }

    /** [distance] px into the band from the far edge. */
    fun fromTop(distance: Float): Float = top + if (top > horizon) -distance else distance

    /** [r], but at least [margin] px inside the far edge: whatever lies beyond it runs along that edge. */
    fun capAtTop(r: Float, margin: Float): Float =
        if (top > horizon) min(r, top - margin) else max(r, top + margin)
}

private fun Dial.skyBandFor(): SkyBand {
    val inner = radius * bands.sunInner
    val outer = radius * bands.sunOuter
    val top = settings.skyTopElevationDeg.toDouble().coerceIn(1.0, SkyProjection.MAX_TOP_ELEVATION_DEG)
    return when (settings.skyHorizon) {
        SkyHorizon.INNER -> SkyBand(inner, outer, top)
        SkyHorizon.OUTER -> SkyBand(outer, inner, top)
    }
}

private const val SKY_PATH_SAMPLES = 240

/** Sizes (fractions of the dial radius) of the edge dots and the moon path line in the ring. */
private const val SKY_DOT_SIZE = 0.02f
private const val SKY_PATH_WIDTH = 0.008f

/**
 * Sun and moon in the day/night ring, per settings, read as a sky: the angle is the current
 * time, the distance from the horizon edge the current altitude, up to
 * [WidgetSettings.skyTopElevationDeg] at the far edge. A disc at its true size (the moon's lit
 * part only) while within that range, clipped by the band's edges like a body setting behind
 * the horizon; above it a small dot at the far edge. Below the horizon neither shows: the hour
 * hand already points there, and the ring's colors tell whether the sun is up. The moon's
 * course over the shown time span is [drawMoonPathInRing].
 */
internal fun Dial.drawSkyBodies() {
    val sky = skyBandFor()
    val latitude = settings.place.latitude
    val longitude = settings.place.longitude
    val sunAltitude = { instant: Instant -> SunMoonCalculator.sunAltitudeDegrees(instant, latitude, longitude) }
    val moonAltitude = { instant: Instant -> SunMoonCalculator.moonAltitudeDegrees(instant, latitude, longitude) }

    val band = ringBandPath()
    val instant = now.toInstant()
    val angle = dialAngle(now, settings.displayMode)
    val dotRadius = radius * SKY_DOT_SIZE

    /** Where a body at [altitude] (not below the horizon) goes: radius, and whether a disc or a dot at the far edge. */
    fun placement(altitude: Double): Pair<Float, Boolean> =
        if (altitude > sky.topElevation) sky.fromTop(dotRadius) to false else sky.radiusFor(altitude) to true

    val moon = if (settings.showMoonInRing) moonAltitude(instant).takeIf { it >= 0.0 }?.let(::placement) else null
    val sun = if (settings.showSunInRing) sunAltitude(instant).takeIf { it >= 0.0 }?.let(::placement) else null
    // Two dots at the far edge would hide each other: set them apart along the edge.
    val apart = moon != null && sun != null && !moon.second && !sun.second
    fun angleFor(r: Float, side: Float): Double =
        if (apart) angle + Math.toDegrees((side * dotRadius * 1.15f / r).toDouble()) else angle

    moon?.let { (r, disc) ->
        val fraction = SunMoonCalculator.moonIllumination(instant).fraction
        val altitude = moonAltitude(instant)
        val color = MoonTable.disc(altitude, sunAltitude(instant), fraction) or OPAQUE
        drawBodyAureole(sky, angle, altitude, MoonlitSky.aureole(altitude, sunAltitude(instant), fraction))
        val (x, y) = pointOnDial(r, angleFor(r, -1f))
        if (disc) {
            val pxPerDegree = pixelsPerDegree(r, instant, SunMoonCalculator::moonPosition)
            val discRadius = (SunMoonCalculator.moonSemiDiameterDegrees(instant) * pxPerDegree).toFloat()
            val limb = SunMoonCalculator.moonBrightLimbAngle(instant, latitude, longitude)
            val lit = moonLitPath(x, y, discRadius, fraction, brightLimbOnDial(limb, angle, settings.skyHorizon))
            canvas.withClip(band) { drawPath(lit, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }) }
        } else {
            drawSkyDot(canvas, x, y, dotRadius, color)
        }
    }
    sun?.let { (r, disc) ->
        val altitude = sunAltitude(instant)
        drawBodyAureole(sky, angle, altitude, SunTable.aureole(altitude))
        val (x, y) = pointOnDial(r, angleFor(r, 1f))
        if (disc) {
            val pxPerDegree = pixelsPerDegree(r, instant, SunMoonCalculator::sunPosition)
            canvas.withClip(band) { drawSunDisc(this, x, y, pxPerDegree, altitude) }
        } else {
            drawSkyDot(canvas, x, y, dotRadius, SunTable.disc(altitude) or OPAQUE)
        }
    }
}

/**
 * The aureole [glow] of a body at [altitudeDeg] around where it stands at dial [angleDeg], even
 * beyond the far edge: it then fades out of the band as the body climbs, instead of vanishing
 * when the disc turns into a dot.
 */
private fun Dial.drawBodyAureole(sky: SkyBand, angleDeg: Double, altitudeDeg: Double, glow: IntArray) {
    val trueRadius = sky.radiusFor(altitudeDeg)
    // Its size at the scale of the sky's projection across the ring (altitude), which is not
    // linear: over the aureole's extent toward the horizon, or the smaller of both sides
    // while the body is within the band.
    val extent = SunTable.AUREOLE_EXTENT_DEG
    val towardHorizon = abs(trueRadius - sky.radiusFor(altitudeDeg - extent)) / extent
    val upward = abs(sky.radiusFor(altitudeDeg + extent) - trueRadius) / extent
    val acrossRing = (if (altitudeDeg >= sky.topElevation) towardHorizon else min(towardHorizon, upward)).toFloat()
    val (x, y) = pointOnDial(trueRadius, angleDeg)
    // Only where the ring is opaque: "screen" blending would raise the coverage of its
    // smoothed edges and make them glow.
    canvas.withClip(ringBandPath(inset = 1f)) {
        drawAureole(this, x, y, acrossRing, glow)
    }
}

/**
 * With [WidgetSettings.showMoonPathInRing], the moon's altitude over the whole shown time span as a
 * thin line in [MARK_COLOR], read like the moon in the ring (see [drawSkyBodies]). It is broken
 * where the shown window has its seam (oldest next to newest time). It leaves the band where the
 * moon sets, capped so it stays nearby for the clip to cut, and runs along the far edge, just
 * inside it, while the moon stands higher than that edge shows. The sun needs none: the ring's
 * colors and the sunrise/sunset marks already tell its course.
 */
internal fun Dial.drawMoonPathInRing() {
    val sky = skyBandFor()
    val window = shownWindow(settings, now).let { (start, end) -> Duration.between(start, end) }
    val maxStep = window.dividedBy(SKY_PATH_SAMPLES.toLong()).multipliedBy(2)
    val strokeWidth = radius * SKY_PATH_WIDTH
    val path = Path()
    var previous: Instant? = null
    for (i in 0..SKY_PATH_SAMPLES) {
        val angle = 360.0 * i / SKY_PATH_SAMPLES
        val instant = resolveInstantForAngle(angle, settings, now)
        val altitude = SunMoonCalculator.moonAltitudeDegrees(instant, settings.place.latitude, settings.place.longitude)
        val r = sky.capAtTop(sky.radiusFor(altitude, overshoot = 0.5), strokeWidth / 2f)
        val (x, y) = pointOnDial(r, angle)
        val step = previous?.let { Duration.between(it, instant) }
        if (step != null && !step.isNegative && step <= maxStep) path.lineTo(x, y) else path.moveTo(x, y)
        previous = instant
    }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // The grey of the sun marks: visible on the bright day sky and the dark night alike.
        color = MARK_COLOR
        style = Paint.Style.STROKE
        this.strokeWidth = strokeWidth
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.withClip(ringBandPath()) { drawPath(path, paint) }
}

/** A filled circle: the dots of bodies beyond the far edge. */
private fun drawSkyDot(canvas: Canvas, x: Float, y: Float, r: Float, color: Int) {
    canvas.drawCircle(x, y, r, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color })
}

/**
 * Pixels per degree of sky at a body [r] px from the center at [instant]: its true size on the
 * ring along the time axis. The body moves along its daily path by the angle between its
 * [position]s a few minutes apart, and the dial turns by its own angle in that time; across the
 * ring (altitude) the scale depends on the sky's projection and is far smaller, so the disc is
 * drawn round at the scale along the ring.
 */
private fun Dial.pixelsPerDegree(r: Float, instant: Instant, position: (Instant, Double, Double) -> SunMoonCalculator.HorizontalPosition): Float {
    val a = position(instant, settings.place.latitude, settings.place.longitude)
    val b = position(instant.plus(BODY_SPEED_INTERVAL), settings.place.latitude, settings.place.longitude)
    val skyDegrees = angularDistance(a.altitudeRad, a.azimuthRad, b.altitudeRad, b.azimuthRad)
    val dialDegrees = BODY_SPEED_INTERVAL.toMinutes() / 60.0 * when (settings.displayMode) {
        DisplayMode.TWELVE_HOUR -> 30.0
        DisplayMode.TWENTY_FOUR_HOUR -> 15.0
    }
    return (r * Math.toRadians(dialDegrees) / skyDegrees.coerceAtLeast(1e-6)).toFloat()
}

private val BODY_SPEED_INTERVAL: Duration = Duration.ofMinutes(10)

/**
 * [brightLimbAngle] (from the zenith, counterclockwise, see
 * [SunMoonCalculator.moonBrightLimbAngle]) on a body in the ring at [dialAngleDeg]: the zenith
 * there points to the far edge, not to the top of the screen. Counted from the top of the
 * screen, counterclockwise, like [drawMoonPhase].
 */
internal fun brightLimbOnDial(brightLimbAngle: Double, dialAngleDeg: Double, horizon: SkyHorizon): Double {
    val zenith = -Math.toRadians(dialAngleDeg) + if (horizon == SkyHorizon.OUTER) PI else 0.0
    return (brightLimbAngle + zenith).mod(2 * PI)
}

/** Angle (degrees) between two directions given by altitude and azimuth (radians). */
private fun angularDistance(alt1: Double, az1: Double, alt2: Double, az2: Double): Double =
    Math.toDegrees(acos((sin(alt1) * sin(alt2) + cos(alt1) * cos(alt2) * cos(az1 - az2)).coerceIn(-1.0, 1.0)))

/**
 * An aureole around ([x], [y]): the sky around a body as the eye sees it, the colors [glow] at
 * [SunTable.ANGLES_DEG] laid over the ring in "screen" blending, at [pxPerDegree] of sky. Round
 * at the scale across the ring (altitude): along the ring, the time axis is stretched several
 * times as much and would draw it as a long, thin streak. Each color is as opaque as its
 * brightest channel: over the ring that blends the same as opaque, while where the ring has
 * faded out at its end the dark rim of the glow does not cover the transparent background.
 */
private fun drawAureole(canvas: Canvas, x: Float, y: Float, pxPerDegree: Float, glow: IntArray) {
    val extent = (SunTable.AUREOLE_EXTENT_DEG * pxPerDegree).toFloat()
    if (extent < 0.5f) return
    val angles = SunTable.ANGLES_DEG
    val colors = IntArray(glow.size) { lightAsAlpha(glow[it]) }
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = RadialGradient(
            x, y, extent,
            intArrayOf(colors.first()) + colors,
            floatArrayOf(0f) + FloatArray(angles.size) { (angles[it] / SunTable.AUREOLE_EXTENT_DEG).toFloat() },
            Shader.TileMode.CLAMP,
        )
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    }
    canvas.drawCircle(x, y, extent, paint)
}

/** The opaque [color] (0xRRGGBB) as a translucent color of the same premultiplied channels. */
private fun lightAsAlpha(color: Int): Int {
    val r = color shr 16 and 255
    val g = color shr 8 and 255
    val b = color and 255
    val a = maxOf(r, g, b)
    if (a == 0) return Color.TRANSPARENT
    return Color.argb(a, r * 255 / a, g * 255 / a, b * 255 / a)
}

/**
 * The sun's disc at [altitudeDeg] centered on ([x], [y]) at [pxPerDegree] along the ring, at its
 * true size and round, in the color the eye sees it in ([SunTable.disc]).
 */
private fun drawSunDisc(canvas: Canvas, x: Float, y: Float, pxPerDegree: Float, altitudeDeg: Double) {
    val discRadius = (SkyModel.SUN_SEMI_DIAMETER_DEG * pxPerDegree).toFloat()
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SunTable.disc(altitudeDeg) or OPAQUE }
    canvas.drawCircle(x, y, discRadius, paint)
}
