package com.umfluten.lighttimeclouds.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.umfluten.lighttimeclouds.astro.DialTimeMapper
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.WidgetSettings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/** Renders the whole clock face (ring, sun marks, ticks, moon, hands) into a square [Bitmap]. */
object ClockFaceRenderer {

    private const val RING_SAMPLES = 360

    /** Day/night ring band and the tick area just inside it (fractions of the dial radius). */
    private const val RING_INNER = 0.82f
    private const val TICK_OUTER = 0.80f

    /**
     * Mid grey with near-equal WCAG contrast (~3.5:1) against both the daytime and nighttime
     * ring colors of [GRADIENT_STOPS], across the year's range of noon/midnight sun altitudes.
     * Used for sun marks and the moon hand/path; public so the config app's time list matches.
     */
    val MARK_COLOR = Color.rgb(105, 105, 105)

    private const val MOON_PATH_SAMPLES = 144

    /** Moon orbit (fractions of the dial radius): horizon circle, and the offset reached at ±90° altitude. */
    private const val MOON_HORIZON_LINE = 0.30f
    private const val MOON_ALTITUDE_AMPLITUDE = 0.18f

    /** Draws the clock for [now], which defaults to the current time in the time zone of the widget's place. */
    fun render(
        context: Context,
        sizePx: Int,
        settings: WidgetSettings,
        now: ZonedDateTime = ZonedDateTime.now(settings.place.zone),
    ): Bitmap {
        val size = sizePx.coerceAtLeast(1)
        val bitmap = createBitmap(size, size)
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f
        val radius = size / 2f * 0.96f
        val dark = chromeIsDark(context, settings.colorMode)
        val chromeColor = chromeColor(context, dark)

        drawDayNightRing(canvas, cx, cy, radius, settings, now)
        if (settings.showSolarMarks || settings.showSunriseSunsetMarks) {
            drawSolarMarks(canvas, cx, cy, radius, settings, now)
        }
        drawTicksAndNumerals(canvas, cx, cy, radius, settings, chromeColor)
        if (settings.showMoon) {
            drawMoon(canvas, cx, cy, radius, settings, now, dark)
        }
        drawHands(canvas, cx, cy, radius, settings, now, chromeColor)
        drawCenterPin(canvas, cx, cy, radius, chromeColor)

        return bitmap
    }

    // --- Day/twilight/night ring -------------------------------------------------

    private fun drawDayNightRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        now: ZonedDateTime,
    ) {
        val ringOuter = radius
        val ringInner = radius * RING_INNER
        val ringMid = (ringOuter + ringInner) / 2f
        val rect = RectF(cx - ringMid, cy - ringMid, cx + ringMid, cy + ringMid)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = ringOuter - ringInner
        }
        for (i in 0 until RING_SAMPLES) {
            val a0 = 360.0 * i / RING_SAMPLES
            val a1 = 360.0 * (i + 1) / RING_SAMPLES
            val mid = (a0 + a1) / 2.0
            val instant = resolveInstantForAngle(mid, settings, now)
            val altitude = SunMoonCalculator.sunAltitudeDegrees(instant, settings.place.latitude, settings.place.longitude)
            paint.color = ringColor(altitude, settings)
            canvas.drawArc(rect, (a0 - 90.0).toFloat(), (a1 - a0).toFloat() + 0.75f, false, paint)
        }
    }

    /**
     * Marks solar noon/midnight and sunrise/sunset across the ring, per settings. All strokes use
     * [MARK_COLOR]. In 12h mode every dial position stands for two times 12h apart, so only
     * events inside the currently shown 12h window are drawn.
     */
    private fun drawSolarMarks(canvas: Canvas, cx: Float, cy: Float, radius: Float, settings: WidgetSettings, now: ZonedDateTime) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            strokeWidth = radius * 0.012f
            strokeCap = Paint.Cap.BUTT
        }
        val inner = radius * RING_INNER
        val outer = radius
        for (dayOffset in -1L..1L) {
            val times = SunMoonCalculator.sunTimes(now.plusDays(dayOffset).toInstant(), settings.place.latitude, settings.place.longitude)
            val events = buildList {
                if (settings.showSolarMarks) {
                    add(times.solarNoon)
                    add(times.nadir)
                }
                if (settings.showSunriseSunsetMarks) {
                    // Null during polar day/night, when the sun never crosses the horizon.
                    add(times.sunrise)
                    add(times.sunset)
                }
            }
            for (instant in events.filterNotNull()) {
                val angle = visibleAngleFor(instant, settings, now) ?: continue
                val rad = Math.toRadians(angle - 90.0)
                val dx = cos(rad).toFloat()
                val dy = sin(rad).toFloat()
                canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, paint)
            }
        }
    }

    /** Dial angle at which [instant] appears on the ring right now, or null if it is outside the shown window. */
    private fun visibleAngleFor(instant: Instant, settings: WidgetSettings, now: ZonedDateTime): Double? {
        val local = instant.atZone(now.zone)
        val angle = dialAngle(local, settings.displayMode)
        return when (settings.displayMode) {
            DisplayMode.TWENTY_FOUR_HOUR ->
                if (local.toLocalDate() == now.toLocalDate()) angle else null
            DisplayMode.TWELVE_HOUR -> {
                val shown = mapperFor(settings).resolve(now, angle).toInstant()
                if (Duration.between(shown, instant).abs() < Duration.ofMinutes(2)) angle else null
            }
        }
    }

    /** Maps a dial angle to the real instant whose sun state should color that position, per [WidgetSettings.displayMode]. */
    private fun resolveInstantForAngle(angleDegrees: Double, settings: WidgetSettings, now: ZonedDateTime): Instant =
        when (settings.displayMode) {
            DisplayMode.TWENTY_FOUR_HOUR -> {
                val hourOfDay = angleDegrees / 15.0
                now.toLocalDate().atStartOfDay(now.zone).plusSeconds((hourOfDay * 3600.0).toLong()).toInstant()
            }
            DisplayMode.TWELVE_HOUR -> mapperFor(settings).resolve(now, angleDegrees).toInstant()
        }

    private fun mapperFor(settings: WidgetSettings): DialTimeMapper = when (settings.switchStrategyType) {
        SwitchStrategyType.FIXED_TIMES -> DialTimeMapper.FixedTimes(
            switchTime1 = LocalTime.ofSecondOfDay((settings.switchTime1Minutes * 60L).coerceIn(0, 86399)),
            switchTime2 = LocalTime.ofSecondOfDay((settings.switchTime2Minutes * 60L).coerceIn(0, 86399)),
        )
        SwitchStrategyType.TRAILING_OFFSET -> DialTimeMapper.TrailingOffset(settings.trailingOffsetMinutes)
    }

    // --- Color handling ------------------------------------------------------------

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

    private fun lerpColor(c0: Int, c1: Int, t: Double): Int {
        val tf = t.toFloat().coerceIn(0f, 1f)
        fun ch(a: Int, b: Int) = (a + (b - a) * tf).toInt().coerceIn(0, 255)
        return Color.rgb(
            ch(Color.red(c0), Color.red(c1)),
            ch(Color.green(c0), Color.green(c1)),
            ch(Color.blue(c0), Color.blue(c1)),
        )
    }

    /**
     * One fixed color per sky phase, for [RingStyle.BANDS]. Sampled from [gradientColor] at a
     * representative altitude inside each phase, so both ring styles share one palette.
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
     * the ring); also used by the config app's time list.
     */
    fun ringColor(altitudeDeg: Double, settings: WidgetSettings): Int = when (settings.ringStyle) {
        RingStyle.GRADIENT -> gradientColor(altitudeDeg)
        RingStyle.BANDS -> bandColor(SunMoonCalculator.classify(altitudeDeg))
    }

    /** True when the widget sits on a dark background, so the chrome must be light. [ColorMode.AUTO] follows the system dark mode. */
    /** Color of numerals, ticks and hands under [settings]; also used for the place name next to the clock. */
    fun chromeColor(context: Context, settings: WidgetSettings): Int =
        chromeColor(context, chromeIsDark(context, settings.colorMode))

    private fun chromeIsDark(context: Context, mode: ColorMode): Boolean = when (mode) {
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
    private fun chromeColor(context: Context, dark: Boolean): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getColor(if (dark) android.R.color.system_accent1_100 else android.R.color.system_accent1_800)
        } else if (dark) {
            Color.rgb(235, 235, 240)
        } else {
            Color.rgb(25, 25, 30)
        }

    // --- Ticks & numerals ------------------------------------------------------------

    private fun drawTicksAndNumerals(canvas: Canvas, cx: Float, cy: Float, radius: Float, settings: WidgetSettings, color: Int) {
        val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = radius * 0.02f
            strokeCap = Paint.Cap.ROUND
        }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = radius * 0.16f
            textAlign = Paint.Align.CENTER
        }
        val tickOuter = radius * TICK_OUTER
        val tickInner = radius * 0.74f
        val numeralRadius = radius * 0.62f
        val bounds = Rect()

        val labels: List<Pair<Double, String>> = when (settings.displayMode) {
            DisplayMode.TWELVE_HOUR -> (1..12).map { h -> (h * 30.0) to h.toString() }
            DisplayMode.TWENTY_FOUR_HOUR -> (0 until 24 step 2).map { h -> (h * 15.0) to h.toString() }
        }

        for ((angleDeg, label) in labels) {
            val rad = Math.toRadians(angleDeg - 90.0)
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            canvas.drawLine(cx + dx * tickInner, cy + dy * tickInner, cx + dx * tickOuter, cy + dy * tickOuter, tickPaint)
            textPaint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, cx + dx * numeralRadius, cy + dy * numeralRadius + bounds.height() / 2f, textPaint)
        }

        if (settings.showMinuteTicks) {
            drawMinuteTicks(canvas, cx, cy, radius, color)
        }
    }

    /** 60 short ticks for the minute hand; every fifth is skipped since hour ticks (both modes) sit on multiples of 30°. */
    private fun drawMinuteTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = radius * 0.01f
            strokeCap = Paint.Cap.ROUND
        }
        val outer = radius * TICK_OUTER
        val inner = radius * 0.77f
        for (minute in 0 until 60) {
            if (minute % 5 == 0) continue
            val rad = Math.toRadians(minute * 6.0 - 90.0)
            val dx = cos(rad).toFloat()
            val dy = sin(rad).toFloat()
            canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, paint)
        }
    }

    // --- Moon ------------------------------------------------------------

    /**
     * Moon hand and daily moon path. The angle stands for where the moon is in its daily circuit:
     * the dial time "t minus [SunMoonCalculator.moonLagHours]", the same way the hour hand stands
     * for the sun. The distance from the center shows the moon's altitude: a faint circle marks the
     * horizon, above it the moon climbs outward, below it sinks inward (drawn faded). The path
     * covers the same time window as the day/night ring (12h mode: the active window, flipping with it; 24h mode:
     * today), so it always contains the current point, where the phase disc sits.
     */
    private fun drawMoon(canvas: Canvas, cx: Float, cy: Float, radius: Float, settings: WidgetSettings, now: ZonedDateTime, dark: Boolean) {
        fun pointAt(time: ZonedDateTime): Triple<Float, Float, Boolean> {
            val lagSeconds = (SunMoonCalculator.moonLagHours(time.toInstant()) * 3600.0).toLong()
            val angle = dialAngle(time.minusSeconds(lagSeconds), settings.displayMode)
            val altitude = SunMoonCalculator.moonAltitudeDegrees(time.toInstant(), settings.place.latitude, settings.place.longitude)
            val r = radius * moonOrbitRadiusFactor(altitude)
            val rad = Math.toRadians(angle - 90.0)
            return Triple(cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat(), altitude > 0.0)
        }

        val pathStart = when (settings.displayMode) {
            DisplayMode.TWELVE_HOUR -> mapperFor(settings).windowStart(now)
            DisplayMode.TWENTY_FOUR_HOUR -> now.toLocalDate().atStartOfDay(now.zone)
        }
        val spanMinutes = when (settings.displayMode) {
            DisplayMode.TWELVE_HOUR -> 12 * 60L
            // Not always 24h: DST change days are 23h or 25h long.
            DisplayMode.TWENTY_FOUR_HOUR -> Duration.between(pathStart, pathStart.plusDays(1)).toMinutes()
        }
        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            alpha = 60
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.006f
        }
        canvas.drawCircle(cx, cy, radius * MOON_HORIZON_LINE, horizonPaint)

        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            strokeWidth = radius * 0.008f
            strokeCap = Paint.Cap.ROUND
        }
        var previous = pointAt(pathStart)
        for (i in 1..MOON_PATH_SAMPLES) {
            val current = pointAt(pathStart.plusMinutes(spanMinutes * i / MOON_PATH_SAMPLES))
            pathPaint.alpha = if (previous.third || current.third) 200 else 80
            canvas.drawLine(previous.first, previous.second, current.first, current.second, pathPaint)
            previous = current
        }

        // Hand and phase disc at the current position; faded while the moon is below the horizon.
        val (mx, my, aboveHorizon) = pointAt(now)
        val moonSize = radius * 0.073f
        val alpha = if (aboveHorizon) 255 else 110
        val dx = mx - cx
        val dy = my - cy
        val distance = hypot(dx, dy)
        val handScale = ((distance - moonSize) / distance).coerceAtLeast(0f)
        val handPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            this.alpha = alpha
            strokeWidth = radius * 0.014f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + dx * handScale, cy + dy * handScale, handPaint)

        val litColor = if (dark) Color.rgb(235, 235, 225) else Color.rgb(250, 250, 245)
        val shadowColor = if (dark) Color.rgb(40, 40, 45) else Color.rgb(90, 90, 95)
        val illumination = SunMoonCalculator.moonIllumination(now.toInstant())
        canvas.saveLayerAlpha(null, alpha)
        drawMoonPhase(canvas, mx, my, moonSize, illumination, litColor, shadowColor)
        canvas.restore()
    }

    /** Moon distance from the center, as a fraction of the dial radius: the horizon circle offset by sin(altitude), outward above and inward below. */
    private fun moonOrbitRadiusFactor(altitudeDeg: Double): Float =
        MOON_HORIZON_LINE + MOON_ALTITUDE_AMPLITUDE * sin(Math.toRadians(altitudeDeg.coerceIn(-90.0, 90.0))).toFloat()

    /** Dial angle (0..360, 0 = top) at which [time] appears on the dial. */
    private fun dialAngle(time: ZonedDateTime, mode: DisplayMode): Double {
        val hours = time.hour + time.minute / 60.0 + time.second / 3600.0
        return when (mode) {
            DisplayMode.TWELVE_HOUR -> (hours % 12.0) * 30.0
            DisplayMode.TWENTY_FOUR_HOUR -> hours * 15.0
        }
    }

    /**
     * Classic "two offset circles" moon-phase rendering: a lit disc and a same-radius
     * shadow disc shifted horizontally by an amount derived from the phase angle.
     */
    private fun drawMoonPhase(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        r: Float,
        illumination: SunMoonCalculator.MoonIllumination,
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

        canvas.withClip(Path().apply { addCircle(cx, cy, r, Path.Direction.CW) }) {
            drawCircle(cx, cy, r, litPaint)

            val phase = ((illumination.phase % 1.0) + 1.0) % 1.0
            val waxing = phase < 0.5
            val theta = phase * 2.0 * Math.PI
            val shift = (r * (1.0 - cos(theta))).toFloat()
            val shadowCx = if (waxing) cx - shift else cx + shift
            drawCircle(shadowCx, cy, r, shadowPaint)
        }

        canvas.drawCircle(cx, cy, r, outlinePaint)
    }

    // --- Hands ------------------------------------------------------------

    private fun drawHands(canvas: Canvas, cx: Float, cy: Float, radius: Float, settings: WidgetSettings, now: ZonedDateTime, color: Int) {
        val hourAngle = dialAngle(now.withSecond(0), settings.displayMode)
        val minuteAngle = now.minute * 6.0

        val hourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = radius * 0.035f
            strokeCap = Paint.Cap.ROUND
        }
        val minutePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = radius * 0.022f
            strokeCap = Paint.Cap.ROUND
        }

        drawHand(canvas, cx, cy, hourAngle, radius * 0.48f, hourPaint)
        drawHand(canvas, cx, cy, minuteAngle, radius * 0.72f, minutePaint)
    }

    private fun drawHand(canvas: Canvas, cx: Float, cy: Float, angleDeg: Double, length: Float, paint: Paint) {
        val rad = Math.toRadians(angleDeg - 90.0)
        val ex = cx + (cos(rad) * length).toFloat()
        val ey = cy + (sin(rad) * length).toFloat()
        canvas.drawLine(cx, cy, ex, ey, paint)
    }

    private fun drawCenterPin(canvas: Canvas, cx: Float, cy: Float, radius: Float, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL }
        canvas.drawCircle(cx, cy, radius * 0.03f, paint)
    }
}
