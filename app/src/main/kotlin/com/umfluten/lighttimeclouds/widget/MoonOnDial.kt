package com.umfluten.lighttimeclouds.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.MoonStyle
import com.umfluten.lighttimeclouds.data.WidgetSettings
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

private const val MOON_PATH_SAMPLES = 144

/** Radius of the moon's phase disc, as a fraction of the dial radius. */
private const val MOON_SIZE = 0.058f

/** With [MoonStyle.ON_HOUR_HAND]: radius of the phase disc, width of its frame, and how much hand stays visible beyond it. */
private const val MOON_ON_HAND_SIZE = 0.045f
private const val MOON_ON_HAND_FRAME = 0.008f
private const val MOON_ON_HAND_TIP = 0.025f

/**
 * Daily moon path, plus the moon hand and phase disc with [MoonStyle.SEPARATE_HAND] (with
 * [MoonStyle.ON_HOUR_HAND] the disc is drawn together with the hands, see [drawMoonOnHourHand]).
 * The angle of each point comes from [moonPointAt]. The distance from the center shows the moon's
 * altitude: a faint circle marks the horizon, above it the moon climbs outward, below it sinks
 * inward (drawn dashed and fainter). Path and horizon are drawn in the chrome [color]. The path
 * covers the same time window as the day/night ring (the active 12h or 24h window, moving with
 * it), so it always contains the current point. With
 * [WidgetSettings.showMoonCompass], a dot and letter mark where the moon passes north, east,
 * south or west.
 */
internal fun Dial.drawMoon() {
    fun pointAt(time: ZonedDateTime) = moonPointAt(time)

    val mapper = mapperFor(settings)
    val pathStart = mapper.windowStart(now)
    val spanMinutes = Duration.between(pathStart, mapper.windowEnd(now)).toMinutes()
    val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        alpha = 70
        style = Paint.Style.STROKE
        strokeWidth = radius * 0.006f
    }
    canvas.drawCircle(cx, cy, radius * moonOrbit().first, horizonPaint)

    // One contour per stretch above or below the horizon, so the dashes run on along each stretch.
    val abovePath = Path()
    val belowPath = Path()
    var lastAbove: Boolean? = null
    val compassPoints = mutableListOf<Pair<ZonedDateTime, Int>>()
    val stepSeconds = spanMinutes * 60.0 / MOON_PATH_SAMPLES
    var previousTime = pathStart
    var previous = pointAt(pathStart)
    for (i in 1..MOON_PATH_SAMPLES) {
        val time = pathStart.plusMinutes(spanMinutes * i / MOON_PATH_SAMPLES)
        val current = pointAt(time)
        val above = previous.aboveHorizon || current.aboveHorizon
        val path = if (above) abovePath else belowPath
        if (above != lastAbove) path.moveTo(previous.x, previous.y)
        path.lineTo(current.x, current.y)
        lastAbove = above
        // Azimuth step unwrapped across north (±180°), so a crossing of north is found like any other.
        val step = ((current.azimuthDeg - previous.azimuthDeg + 540.0) % 360.0) - 180.0
        for ((azimuth, label) in COMPASS_POINTS) {
            val fraction = (azimuth - previous.azimuthDeg) / step
            if (settings.showMoonCompass && step != 0.0 && fraction > 0.0 && fraction <= 1.0) {
                compassPoints += previousTime.plusSeconds((fraction * stepSeconds).toLong()) to label
            }
        }
        previousTime = time
        previous = current
    }
    val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    canvas.drawPath(
        belowPath,
        pathPaint.apply {
            alpha = 110
            strokeWidth = radius * 0.010f
            pathEffect = DashPathEffect(floatArrayOf(radius * 0.018f, radius * 0.026f), 0f)
        },
    )
    canvas.drawPath(
        abovePath,
        pathPaint.apply {
            alpha = 200
            strokeWidth = radius * 0.014f
            pathEffect = null
        },
    )
    drawCompassPoints(compassPoints.map { (time, label) -> pointAt(time) to label })
    if (settings.moonStyle != MoonStyle.SEPARATE_HAND) return

    // Hand and phase disc at the current position; faded while the moon is below the horizon.
    val (mx, my, aboveHorizon) = pointAt(now)
    val moonSize = radius * MOON_SIZE
    val alpha = if (aboveHorizon) 255 else 110
    val dx = mx - cx
    val dy = my - cy
    val distance = hypot(dx, dy)
    val handScale = ((distance - moonSize) / distance).coerceAtLeast(0f)
    val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = MARK_COLOR
        this.alpha = alpha
        strokeWidth = radius * 0.014f
        strokeCap = Paint.Cap.ROUND
    }
    canvas.drawLine(cx, cy, cx + dx * handScale, cy + dy * handScale, handPaint)

    canvas.saveLayerAlpha(null, alpha)
    drawMoonPhase(mx, my, moonSize, now.toInstant(), moonLitColor(dark), moonShadowColor(dark))
    canvas.restore()
}

/** Distance of a compass letter's center from its dot, and the least gap to the numerals, as fractions of the dial radius. */
private const val COMPASS_LABEL_OFFSET = 0.06f
private const val COMPASS_LABEL_CLEARANCE = 0.015f

/**
 * Moon azimuths (from south, positive toward west, as in [SunMoonCalculator]) labeled on the path, with
 * their letter. North appears twice so that crossings from either side of ±180° are found.
 */
private val COMPASS_POINTS = listOf(
    -180.0 to R.string.compass_north,
    -90.0 to R.string.compass_east,
    0.0 to R.string.compass_south,
    90.0 to R.string.compass_west,
    180.0 to R.string.compass_north,
)

/**
 * A dot on the path at each of [points], with its letter just outside it (away from the center),
 * or just inside it where the letter would otherwise run into the numerals, as when the moon
 * passes south high up.
 */
private fun Dial.drawCompassPoints(points: List<Pair<MoonPoint, Int>>) {
    val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = chromeColor; style = Paint.Style.FILL }
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        textSize = radius * 0.065f
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    val bounds = Rect()
    for ((point, labelRes) in points) {
        val alpha = if (point.aboveHorizon) 230 else 130
        dotPaint.alpha = alpha
        textPaint.alpha = alpha
        canvas.drawCircle(point.x, point.y, radius * 0.014f, dotPaint)
        val dx = point.x - cx
        val dy = point.y - cy
        val distance = hypot(dx, dy).coerceAtLeast(1f)
        val label = context.getString(labelRes)
        val angle = Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble()))
        val outward = distance + COMPASS_LABEL_OFFSET * radius + labelExtent(textPaint, label, angle) <=
            numeralsInnerEdge(angle) - COMPASS_LABEL_CLEARANCE * radius
        val offset = radius * COMPASS_LABEL_OFFSET * (if (outward) 1f else -1f)
        textPaint.getTextBounds(label, 0, label.length, bounds)
        canvas.drawText(label, point.x + dx / distance * offset, point.y + dy / distance * offset + bounds.height() / 2f, textPaint)
    }
}

/**
 * Phase disc on the hour hand ([MoonStyle.ON_HOUR_HAND]), framed in the hand's [color] so that it
 * reads as part of the hand and a new moon stays visible. Drawn opaque so the hand does not show
 * through; below the horizon the lit part is dimmed instead.
 */
internal fun Dial.drawMoonOnHourHand(handTime: ZonedDateTime) {
    val (mx, my, aboveHorizon) = moonPointAt(handTime)
    val moonSize = radius * MOON_ON_HAND_SIZE
    val shadowColor = moonShadowColor(dark)
    val litColor = if (aboveHorizon) moonLitColor(dark) else lerpColor(moonLitColor(dark), shadowColor, 0.55)
    drawMoonPhase(mx, my, moonSize, handTime.toInstant(), litColor, shadowColor)
    val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = chromeColor
        style = Paint.Style.STROKE
        strokeWidth = radius * MOON_ON_HAND_FRAME
    }
    canvas.drawCircle(mx, my, moonSize + framePaint.strokeWidth / 2f, framePaint)
}

/** A point of the moon on the dial, whether it is above the horizon, and its azimuth (from south, positive toward west). */
private data class MoonPoint(val x: Float, val y: Float, val aboveHorizon: Boolean, val azimuthDeg: Double)

/**
 * Where the moon appears on the dial at [time], and whether it is above the horizon. With
 * [MoonStyle.ON_HOUR_HAND] the angle is [time] itself, so the path crosses the horizon circle at
 * the actual moonrise/moonset times and the current point lies on the hour hand. With
 * [MoonStyle.SEPARATE_HAND] it is "[time] minus [SunMoonCalculator.moonLagHours]": where the moon
 * is in its daily circuit, the same way the hour hand stands for the sun.
 */
private fun Dial.moonPointAt(time: ZonedDateTime): MoonPoint {
    val dialTime = when (settings.moonStyle) {
        MoonStyle.ON_HOUR_HAND -> time
        MoonStyle.SEPARATE_HAND -> time.minusSeconds((SunMoonCalculator.moonLagHours(time.toInstant()) * 3600.0).toLong())
    }
    val angle = dialAngle(dialTime, settings.displayMode)
    val position = SunMoonCalculator.moonPosition(time.toInstant(), settings.place.latitude, settings.place.longitude)
    val (x, y) = pointOnDial(radius * moonOrbitRadiusFactor(position.altitudeDegrees), angle)
    return MoonPoint(x, y, position.altitudeDegrees > 0.0, position.azimuthDegrees)
}

private fun moonLitColor(dark: Boolean): Int = if (dark) Color.rgb(235, 235, 225) else Color.rgb(250, 250, 245)

private fun moonShadowColor(dark: Boolean): Int = if (dark) Color.rgb(40, 40, 45) else Color.rgb(90, 90, 95)

/** Moon distance from the center, as a fraction of the dial radius: the horizon circle offset by sin(altitude), outward above and inward below. */
private fun Dial.moonOrbitRadiusFactor(altitudeDeg: Double): Float {
    val (horizon, amplitude) = moonOrbit()
    return horizon + amplitude * sin(Math.toRadians(altitudeDeg.coerceIn(-90.0, 90.0))).toFloat()
}

/**
 * Horizon radius and altitude amplitude of the moon path, as fractions of the dial radius. On the
 * hour hand the framed disc spans from the center (moon at the nadir) to just short of the hand's
 * tip (moon at the zenith), so a bit of the hand always shows beyond it.
 */
private fun Dial.moonOrbit(): Pair<Float, Float> = when (settings.moonStyle) {
    MoonStyle.SEPARATE_HAND -> layout.moonHorizon to layout.moonAmplitude
    MoonStyle.ON_HOUR_HAND -> {
        val disc = MOON_ON_HAND_SIZE + MOON_ON_HAND_FRAME
        val outer = layout.hourHand - MOON_ON_HAND_TIP - disc
        (outer + disc) / 2f to (outer - disc) / 2f
    }
}

/** The Moon's phase disc at [time] centered on ([x], [y]): lit fraction and the tilt of its bright limb. */
private fun Dial.drawMoonPhase(x: Float, y: Float, r: Float, time: Instant, litColor: Int, shadowColor: Int) {
    val fraction = SunMoonCalculator.moonIllumination(time).fraction
    val limb = SunMoonCalculator.moonBrightLimbAngle(time, settings.place.latitude, settings.place.longitude)
    drawMoonPhase(canvas, x, y, r, fraction, limb, litColor, shadowColor)
}

/**
 * Moon phase disc as one sees it, with the top of the screen as the zenith: the lit part is
 * bounded by half the rim on the side of [brightLimbAngle] (from the top, counterclockwise, see
 * [SunMoonCalculator.moonBrightLimbAngle]) and by the terminator, the half-ellipse whose
 * semi-axis along the limb direction is r·(2k − 1) for the lit fraction k: bulging to the dark
 * side when gibbous, to the bright side as a crescent.
 */
internal fun drawMoonPhase(
    canvas: Canvas,
    cx: Float,
    cy: Float,
    r: Float,
    fraction: Double,
    brightLimbAngle: Double,
    litColor: Int,
    shadowColor: Int,
) {
    val litPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = litColor; style = Paint.Style.FILL }
    val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shadowColor; style = Paint.Style.FILL }
    val outlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = r * 0.08f
        color = shadowColor
    }
    canvas.drawCircle(cx, cy, r, shadowPaint)
    canvas.drawPath(moonLitPath(cx, cy, r, fraction, brightLimbAngle), litPaint)
    canvas.drawCircle(cx, cy, r, outlinePaint)
}

/**
 * The lit part of a moon disc of radius [r] at ([cx], [cy]): lit share [fraction], with the
 * bright limb at [brightLimbAngle] from the top, counterclockwise.
 */
internal fun moonLitPath(cx: Float, cy: Float, r: Float, fraction: Double, brightLimbAngle: Double): Path {
    // Towards the bright limb (screen y points down) and across it.
    val ux = -sin(brightLimbAngle)
    val uy = -cos(brightLimbAngle)
    val terminator = 2.0 * fraction.coerceIn(0.0, 1.0) - 1.0
    val lit = Path()
    fun point(along: Double, across: Double, first: Boolean) {
        val x = (cx + r * (along * ux - across * uy)).toFloat()
        val y = (cy + r * (along * uy + across * ux)).toFloat()
        if (first) lit.moveTo(x, y) else lit.lineTo(x, y)
    }
    for (i in 0..MOON_PHASE_STEPS) {
        val s = -PI / 2 + PI * i / MOON_PHASE_STEPS
        point(cos(s), sin(s), first = i == 0)
    }
    for (i in 0..MOON_PHASE_STEPS) {
        val s = PI / 2 - PI * i / MOON_PHASE_STEPS
        point(-terminator * cos(s), sin(s), first = false)
    }
    lit.close()
    return lit
}

/** Segments of each half of the lit part's outline. */
private const val MOON_PHASE_STEPS = 32

private fun lerpColor(c0: Int, c1: Int, t: Double): Int {
    val tf = t.toFloat().coerceIn(0f, 1f)
    fun ch(a: Int, b: Int) = (a + (b - a) * tf).toInt().coerceIn(0, 255)
    return Color.rgb(
        ch(Color.red(c0), Color.red(c1)),
        ch(Color.green(c0), Color.green(c1)),
        ch(Color.blue(c0), Color.blue(c1)),
    )
}
