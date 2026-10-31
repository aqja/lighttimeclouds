package com.umfluten.lighttimeclouds.widget

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.astro.DialTimeMapper
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.MoonStyle
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.TemperatureDisplay
import com.umfluten.lighttimeclouds.data.TemperaturePosition
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.SkyGradientTable
import com.umfluten.lighttimeclouds.weather.HeavyRainWarning
import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity
import com.umfluten.lighttimeclouds.weather.TemperatureKind
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZonedDateTime
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Renders the whole clock face (rings, sun marks, ticks, moon, hands) into a square [Bitmap]. */
object ClockFaceRenderer {

    private const val RING_SAMPLES = 360

    /**
     * Radial extent (fractions of the dial radius) of the day/night ring and the weather ring.
     * Everything else lies inside 0.75, so the inner dial layout does not depend on these.
     */
    private class RingBands(val sunInner: Float, val sunOuter: Float, val weatherInner: Float, val weatherOuter: Float)

    private val BANDS_WEATHER_INSIDE = RingBands(sunInner = 0.82f, sunOuter = 1f, weatherInner = 0.75f, weatherOuter = 0.81f)

    /** Weather outermost: the day/night ring keeps its width and moves inward by the weather ring's width. */
    private val BANDS_WEATHER_OUTSIDE = RingBands(sunInner = 0.75f, sunOuter = 0.93f, weatherInner = 0.94f, weatherOuter = 1f)

    private fun ringBandsFor(settings: WidgetSettings): RingBands =
        if (settings.showWeather && settings.weatherRingPosition == WeatherRingPosition.OUTSIDE) {
            BANDS_WEATHER_OUTSIDE
        } else {
            BANDS_WEATHER_INSIDE
        }

    /**
     * Temperature scale outside the rings: upright labels of [OUTSIDE_TEMPERATURE_SIZE] (fraction of
     * the full radius), [OUTSIDE_TEMPERATURE_GAP] away from the outermost ring. The dial shrinks just
     * enough for the widest possible label ([OUTSIDE_TEMPERATURE_WIDEST]) to fit at every position.
     */
    private const val OUTSIDE_TEMPERATURE_SIZE = 0.075f
    private const val OUTSIDE_TEMPERATURE_GAP = 0.015f
    private const val OUTSIDE_TEMPERATURE_WIDEST = "\u221288°"

    /** Weather ring opacity for hours already past, so the current moment stands out. */
    private const val WEATHER_PAST_ALPHA = 0.5f

    /** Radii (fractions of the dial radius) and sizes of everything inside the ring(s). */
    private class DialLayout(
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

    private val LAYOUT_PLAIN = DialLayout(
        tickOuter = 0.80f, hourTickInner = 0.74f, minuteTickInner = 0.77f,
        temperatureRadius = 0f, temperatureSize = 0f,
        numeralRadius = 0.62f, numeralSize = 0.16f,
        hourHand = 0.48f, minuteHand = 0.72f,
        moonHorizon = 0.30f, moonAmplitude = 0.18f,
    )

    /** Everything moves inward to make room for the weather ring. */
    private val LAYOUT_WEATHER = DialLayout(
        tickOuter = 0.735f, hourTickInner = 0.68f, minuteTickInner = 0.71f,
        temperatureRadius = 0f, temperatureSize = 0f,
        numeralRadius = 0.575f, numeralSize = 0.15f,
        hourHand = 0.46f, minuteHand = 0.66f,
        moonHorizon = 0.30f, moonAmplitude = 0.18f,
    )

    /** Temperatures midway between the numerals, just outside their circle; numerals and moon orbit move further in. */
    private val LAYOUT_TEMPERATURE = DialLayout(
        tickOuter = 0.735f, hourTickInner = 0.68f, minuteTickInner = 0.71f,
        temperatureRadius = 0.605f, temperatureSize = 0.082f,
        numeralRadius = 0.50f, numeralSize = 0.135f,
        hourHand = 0.40f, minuteHand = 0.62f,
        moonHorizon = 0.26f, moonAmplitude = 0.15f,
    )

    /**
     * Mid grey with near-equal WCAG contrast (~3.5:1) against both the daytime and nighttime
     * ring colors of [GRADIENT_STOPS], across the year's range of noon/midnight sun altitudes.
     * Used for sun marks and the moon hand/path; public so the config app's time list matches.
     */
    val MARK_COLOR = Color.rgb(105, 105, 105)

    private const val MOON_PATH_SAMPLES = 144

    /** Radius of the moon's phase disc, as a fraction of the dial radius. */
    private const val MOON_SIZE = 0.073f

    /** With [MoonStyle.ON_HOUR_HAND]: radius of the phase disc, width of its frame, and how much hand stays visible beyond it. */
    private const val MOON_ON_HAND_SIZE = 0.055f
    private const val MOON_ON_HAND_FRAME = 0.014f
    private const val MOON_ON_HAND_TIP = 0.025f

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
        val canvas = Canvas(bitmap)

        val cx = size / 2f
        val cy = size / 2f
        val fullRadius = size / 2f * 0.96f
        val dark = chromeIsDark(context, settings.colorMode)
        val chromeColor = chromeColor(context, dark)
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

        val bands = ringBandsFor(settings)

        drawDayNightRing(canvas, cx, cy, radius, bands, settings, now)
        if (settings.showSolarMarks || settings.showSunriseSunsetMarks) {
            drawSolarMarks(canvas, cx, cy, radius, bands, settings, now)
        }
        if (settings.showWeather && weather != null) {
            drawWeatherRing(canvas, cx, cy, radius, bands, settings, now, weather, dark)
        }
        drawTicksAndNumerals(canvas, cx, cy, radius, settings, layout, chromeColor)
        if (settings.showNextAlarm && nextAlarm != null) {
            drawAlarmTick(canvas, cx, cy, radius, settings, layout, now, nextAlarm)
        }
        if (temperatureKind != null && weather != null) {
            drawTemperatures(
                canvas, cx, cy, settings, now, weather, temperatureKind, chromeColor,
                placement = if (temperaturesOutside) {
                    TemperaturePlacement.Outside(radius + gap, outsidePaint.textSize)
                } else {
                    TemperaturePlacement.Between(radius * layout.temperatureRadius, radius * layout.temperatureSize)
                },
            )
        }
        if (settings.showMoon) {
            drawMoon(context, canvas, cx, cy, radius, settings, layout, now, dark)
        }
        drawHands(canvas, cx, cy, radius, settings, layout, now, chromeColor, dark)
        drawCenterPin(canvas, cx, cy, radius, chromeColor)

        return bitmap
    }

    // --- Day/twilight/night ring -------------------------------------------------

    private fun drawDayNightRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bands: RingBands,
        settings: WidgetSettings,
        now: ZonedDateTime,
    ) {
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
        val skyPositions = FloatArray(SkyGradientTable.STOPS) { k ->
            (ringInner + (ringOuter - ringInner) * k / (SkyGradientTable.STOPS - 1)) / ringOuter
        }
        for (i in 0 until RING_SAMPLES) {
            val a0 = 360.0 * i / RING_SAMPLES
            val a1 = 360.0 * (i + 1) / RING_SAMPLES
            val mid = (a0 + a1) / 2.0
            val instant = resolveInstantForAngle(mid, settings, now)
            val altitude = SunMoonCalculator.sunAltitudeDegrees(instant, settings.place.latitude, settings.place.longitude)
            if (settings.ringStyle == RingStyle.SKY) {
                val colors = skyColors(altitude).let { if (settings.skyHorizon == SkyHorizon.OUTER) it.reversedArray() else it }
                paint.shader = RadialGradient(cx, cy, ringOuter, colors, skyPositions, Shader.TileMode.CLAMP)
            } else {
                paint.color = ringColor(altitude, settings)
            }
            canvas.drawArc(rect, (a0 - 90.0).toFloat(), (a1 - a0).toFloat() + 0.75f, false, paint)
        }
    }

    /** Darkest sky tone: the night color of the other ring styles, so night is never pure black. */
    private val SKY_NIGHT_FLOOR: Int get() = bandColor(SunMoonCalculator.SkyState.NIGHT)

    /** Below this sun altitude the sky shows only [SKY_NIGHT_FLOOR] (end of astronomical twilight). */
    private const val SKY_NIGHT_BELOW = -18.0

    /** The twilight base tone follows [gradientColor] up to this sun altitude (the night purple). */
    private const val SKY_BASE_TOP = -6.0

    /** The twilight base tone fades out between these sun altitudes, leaving the modeled daylight. */
    private const val SKY_BASE_FADE_START = -3.0
    private const val SKY_BASE_FADE_END = 2.0

    /** Twilight base: horizon tone as the sky [SKY_TWILIGHT_SPREAD]° higher, top tone as that much lower. */
    private const val SKY_TWILIGHT_SPREAD = 3.0

    /**
     * Sky colors for [altitudeDeg] as opaque ARGB, horizon first, as two layers combined with a
     * "screen" blend:
     * - the light of the physical model ([SkyGradientTable]: averaged all around, capped eye
     *   adaptation), which supplies daylight and the red/golden glow from about -6° upward;
     * - a twilight base tone from [gradientColor] (from [SKY_NIGHT_FLOOR] through the blue to the
     *   night purple at [SKY_BASE_TOP]), standing in for the blue of the blue hour, which comes from
     *   ozone absorption and multiple scattering that the single-scattering model barely captures.
     *   It fades out around sunrise/sunset.
     * Below [SKY_NIGHT_BELOW] only the night color remains.
     */
    internal fun skyColors(altitudeDeg: Double): IntArray {
        val light = SkyGradientTable.colors(altitudeDeg)
        val t = ((altitudeDeg - SKY_BASE_FADE_START) / (SKY_BASE_FADE_END - SKY_BASE_FADE_START)).coerceIn(0.0, 1.0)
        val baseStrength = 1.0 - t * t * (3 - 2 * t)
        val last = SkyGradientTable.STOPS - 1
        return IntArray(SkyGradientTable.STOPS) { k ->
            val baseAltitude = (altitudeDeg + SKY_TWILIGHT_SPREAD * (1 - 2.0 * k / last)).coerceIn(SKY_NIGHT_BELOW, SKY_BASE_TOP)
            val base = lerpColor(Color.BLACK, gradientColor(baseAltitude), baseStrength)
            screenBlend(base, light[k] or (0xFF shl 24))
        }
    }

    /** "Screen" blend of two opaque colors: 1 - (1 - a)(1 - b) per channel; never darker than either. */
    private fun screenBlend(a: Int, b: Int): Int {
        fun ch(x: Int, y: Int) = 255 - (255 - x) * (255 - y) / 255
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    /**
     * Marks solar noon/midnight and sunrise/sunset across the ring, per settings. All strokes use
     * [MARK_COLOR]. In 12h mode every dial position stands for two times 12h apart, so only
     * events inside the currently shown 12h window are drawn.
     */
    private fun drawSolarMarks(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bands: RingBands,
        settings: WidgetSettings,
        now: ZonedDateTime,
    ) {
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
        val angle = dialAngle(instant.atZone(now.zone), settings.displayMode)
        val shown = resolveInstantForAngle(angle, settings, now)
        return if (Duration.between(shown, instant).abs() < Duration.ofMinutes(2)) angle else null
    }

    /** Maps a dial angle to the real instant whose sun state should color that position: the one inside the shown window. */
    private fun resolveInstantForAngle(angleDegrees: Double, settings: WidgetSettings, now: ZonedDateTime): Instant =
        mapperFor(settings).resolve(now, angleDegrees).toInstant()

    private fun mapperFor(settings: WidgetSettings): DialTimeMapper {
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
            SwitchStrategyType.TRAILING_OFFSET -> DialTimeMapper.TrailingOffset(settings.trailingOffsetMinutes, dialHours)
        }
    }

    // --- Weather ring ----------------------------------------------------------------

    /**
     * Cloud cover and precipitation of the shown time span, in hour-long segments, as two layers:
     * clouds as grey that is transparent under a clear sky and grows opaque with cloud cover; on
     * top, precipitation as an even lattice of dots, larger with each intensity class and as opaque
     * as it is likely: blue for rain (yellow, orange, red at the heavy rain warning levels), violet
     * for snow. Past hours are dimmed, and the whole ring fades as the forecast ages
     * ([WeatherForecast.freshnessAlpha]).
     */
    private fun drawWeatherRing(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        bands: RingBands,
        settings: WidgetSettings,
        now: ZonedDateTime,
        forecast: WeatherForecast,
        dark: Boolean,
    ) {
        val freshness = forecast.freshnessAlpha(now.toInstant())
        if (freshness <= 0f) return

        val clouds = IntArray(RING_SAMPLES)
        val precipitation = IntArray(RING_SAMPLES)
        val dotSizes = FloatArray(RING_SAMPLES)
        for (i in 0 until RING_SAMPLES) {
            val instant = resolveInstantForAngle(360.0 * (i + 0.5) / RING_SAMPLES, settings, now)
            val condition = forecast.conditionAt(instant) ?: continue
            val alpha = if (instant.isBefore(now.toInstant())) freshness * WEATHER_PAST_ALPHA else freshness
            clouds[i] = withAlphaFactor(cloudColor(condition, dark), alpha)
            precipitation[i] = withAlphaFactor(precipitationColor(condition), alpha)
            dotSizes[i] = precipitationDotSize(condition)
        }

        val mid = radius * (bands.weatherOuter + bands.weatherInner) / 2f
        val rect = RectF(cx - mid, cy - mid, cx + mid, cy + mid)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = radius * (bands.weatherOuter - bands.weatherInner)
            strokeCap = Paint.Cap.BUTT
        }
        drawColorRuns(canvas, rect, paint, clouds)
        drawPrecipitationDots(canvas, cx, cy, radius * bands.weatherInner, radius * bands.weatherOuter, precipitation, dotSizes)
    }

    /**
     * Precipitation dots on a fixed hexagonal lattice of [DOT_ROWS] staggered rows across the band
     * between [inner] and [outer], every dot equally far from its neighbors. [sizes] gives the dot
     * radius per ring sample as a fraction of that distance.
     */
    private fun drawPrecipitationDots(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        inner: Float,
        outer: Float,
        colors: IntArray,
        sizes: FloatArray,
    ) {
        val rowPitch = (outer - inner) / DOT_ROWS
        val spacing = rowPitch * 2f / sqrt(3f)
        val columns = (PI * (inner + outer) / spacing).roundToInt().coerceAtLeast(1)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        for (row in 0 until DOT_ROWS) {
            val r = inner + rowPitch * (row + 0.5f)
            val offset = if (row % 2 == 1) 0.5 else 0.0
            for (column in 0 until columns) {
                val fraction = (column + offset) / columns
                val sample = (fraction * RING_SAMPLES).toInt() % RING_SAMPLES
                if (Color.alpha(colors[sample]) == 0 || sizes[sample] <= 0f) continue
                paint.color = colors[sample]
                val angle = 2 * PI * fraction - PI / 2
                canvas.drawCircle(cx + r * cos(angle).toFloat(), cy + r * sin(angle).toFloat(), spacing * sizes[sample], paint)
            }
        }
    }

    /**
     * One arc per run of equal color in [colors] (one entry per ring sample): unlike the opaque
     * day/night ring, overlapping translucent arcs would show darker seams.
     */
    private fun drawColorRuns(canvas: Canvas, rect: RectF, paint: Paint, colors: IntArray) {
        var runStart = 0
        for (i in 1..colors.size) {
            if (i < colors.size && colors[i] == colors[runStart]) continue
            if (Color.alpha(colors[runStart]) > 0) {
                paint.color = colors[runStart]
                val start = 360f * runStart / colors.size
                canvas.drawArc(rect, start - 90f, 360f * (i - runStart) / colors.size, false, paint)
            }
            runStart = i
        }
    }

    private val CLOUD_ON_DARK = Color.rgb(205, 208, 215)
    private val CLOUD_ON_LIGHT = Color.rgb(110, 115, 125)
    private const val CLOUD_MAX_ALPHA = 230

    private val RAIN_COLOR = Color.rgb(40, 110, 230)
    private val SNOW_COLOR = Color.rgb(140, 90, 230)

    /** Rain colors per [HeavyRainWarning] level. */
    private val WARNING_COLORS = mapOf(
        HeavyRainWarning.MARKED to Color.rgb(245, 200, 0),
        HeavyRainWarning.SEVERE to Color.rgb(245, 125, 0),
        HeavyRainWarning.EXTREME to Color.rgb(220, 25, 25),
    )

    private const val DOT_ROWS = 2

    /** Dot radius per [PrecipitationIntensity] as a fraction of the dot spacing; at 0.5 neighbors touch. */
    private val DOT_SIZES = mapOf(
        PrecipitationIntensity.SLIGHT to 0.17f,
        PrecipitationIntensity.LIGHT to 0.25f,
        PrecipitationIntensity.MODERATE to 0.33f,
        PrecipitationIntensity.HEAVY to 0.42f,
    )


    /** Cloud layer color; lighter on a [dark] background so clouds stay visible. Also used by the config app's weather chart. */
    internal fun cloudColor(condition: WeatherCondition, dark: Boolean): Int {
        val base = if (dark) CLOUD_ON_DARK else CLOUD_ON_LIGHT
        val alpha = (condition.cloudCoverPercent.coerceIn(0.0, 100.0) / 100.0 * CLOUD_MAX_ALPHA).toInt()
        return Color.argb(alpha, Color.red(base), Color.green(base), Color.blue(base))
    }

    /**
     * Precipitation color, as opaque as it is likely: blue for rain, or its heavy rain warning
     * color; violet for snow; transparent if dry. Also used by the weather chart.
     */
    internal fun precipitationColor(condition: WeatherCondition): Int {
        if (!condition.hasPrecipitation) return Color.TRANSPARENT
        val color = when {
            condition.isSnow -> SNOW_COLOR
            else -> condition.heavyRainWarning?.let(WARNING_COLORS::getValue) ?: RAIN_COLOR
        }
        return withAlphaFactor(color, condition.precipitationAlpha)
    }

    /** Precipitation dot radius from the intensity class (see [DOT_SIZES]); 0 if dry. */
    private fun precipitationDotSize(condition: WeatherCondition): Float =
        condition.precipitationIntensity?.let(DOT_SIZES::getValue) ?: 0f

    private fun withAlphaFactor(color: Int, factor: Float): Int =
        Color.argb((Color.alpha(color) * factor).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))

    // --- Hourly temperatures ------------------------------------------------------------

    private fun temperatureKindFor(settings: WidgetSettings): TemperatureKind? =
        if (!settings.showWeather) null else when (settings.temperatureDisplay) {
            TemperatureDisplay.NONE -> null
            TemperatureDisplay.AIR -> TemperatureKind.AIR
            TemperatureDisplay.APPARENT -> TemperatureKind.APPARENT
        }

    /** Labels sit midway between the numerals, which are every 30° in both modes. */
    private val TEMPERATURE_ANGLES = List(12) { it * 30.0 + 15.0 }

    /** Where and how large ([textSize], px) the upright temperature labels are drawn. */
    private sealed class TemperaturePlacement(val textSize: Float) {
        /** Centered on a circle of [radius] px, between the numerals. */
        class Between(val radius: Float, textSize: Float) : TemperaturePlacement(textSize)

        /** Just outside the circle of [edge] px (the outermost ring), each label as close as its width allows. */
        class Outside(val edge: Float, textSize: Float) : TemperaturePlacement(textSize)
    }

    private fun temperaturePaint(textSize: Float) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.textSize = textSize
        textAlign = Paint.Align.CENTER
    }

    /** Height of the digits, used to center labels with and without minus sign alike. */
    private fun digitHeight(paint: Paint): Float = Rect().also { paint.getTextBounds("0", 0, 1, it) }.height().toFloat()

    /**
     * How far an upright [label] reaches from its center towards the dial center when placed at
     * [angleDeg]: half its width sideways, half its height at top and bottom, mixed in between.
     */
    private fun labelExtent(paint: Paint, label: String, angleDeg: Double): Float {
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

    /** The time span the dial currently shows: the active 12h or 24h window (an hour more or less across a DST change). */
    private fun shownWindow(settings: WidgetSettings, now: ZonedDateTime): Pair<Instant, Instant> {
        val mapper = mapperFor(settings)
        return mapper.windowStart(now).toInstant() to mapper.windowEnd(now).toInstant()
    }

    /**
     * The rounded mean temperature of each [temperatureLabelSpans] span, midway between the
     * numerals. Like the weather ring, labels for past times are dimmed and all fade as the
     * forecast ages.
     */
    private fun drawTemperatures(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        settings: WidgetSettings,
        now: ZonedDateTime,
        forecast: WeatherForecast,
        kind: TemperatureKind,
        color: Int,
        placement: TemperaturePlacement,
    ) {
        val freshness = forecast.freshnessAlpha(now.toInstant())
        if (freshness <= 0f) return
        val paint = temperaturePaint(placement.textSize)
        val halfHeight = digitHeight(paint) / 2f
        for (span in temperatureLabelSpans(settings, now)) {
            if (!span.to.isAfter(span.from)) continue
            val angle = span.angle
            val temperature = forecast.meanTemperature(span.from, span.to, kind) ?: continue
            val past = span.mid.isBefore(now.toInstant())
            paint.color = withAlphaFactor(color, if (past) freshness * WEATHER_PAST_ALPHA else freshness)
            val label = formatTemperature(temperature)
            val distance = when (placement) {
                is TemperaturePlacement.Between -> placement.radius
                is TemperaturePlacement.Outside -> placement.edge + labelExtent(paint, label, angle)
            }
            val rad = Math.toRadians(angle - 90.0)
            val x = cx + (cos(rad) * distance).toFloat()
            val y = cy + (sin(rad) * distance).toFloat() + halfHeight
            canvas.drawText(label, x, y, paint)
        }
    }

    /** Whole degrees with a real minus sign; "-0" becomes "0". */
    internal fun formatTemperature(celsius: Double): String {
        val rounded = celsius.roundToInt()
        return if (rounded < 0) "\u2212${-rounded}°" else "$rounded°"
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
        // A single representative color: the sky about halfway up the band.
        RingStyle.SKY -> skyColors(altitudeDeg)[SkyGradientTable.STOPS / 2]
        RingStyle.GRADIENT -> gradientColor(altitudeDeg)
        RingStyle.BANDS -> bandColor(SunMoonCalculator.classify(altitudeDeg))
    }

    /** Color of numerals, ticks and hands under [settings]; also used for the place name next to the clock. */
    fun chromeColor(context: Context, settings: WidgetSettings): Int =
        chromeColor(context, chromeIsDark(context, settings.colorMode))

    /** True when the widget sits on a dark background, so the chrome must be light. [ColorMode.AUTO] follows the system dark mode. */
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

    private fun drawTicksAndNumerals(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
        color: Int,
    ) {
        val tickPaint = hourTickPaint(radius, color)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            textSize = radius * layout.numeralSize
            textAlign = Paint.Align.CENTER
        }
        val tickOuter = radius * layout.tickOuter
        val tickInner = radius * layout.hourTickInner
        val numeralRadius = radius * layout.numeralRadius
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
            drawMinuteTicks(canvas, cx, cy, radius, layout, color)
        }
    }

    /** Signal red, clearly apart from the chrome and the weather colors on light and dark backgrounds. */
    private val ALARM_COLOR = Color.rgb(225, 45, 45)

    /** Stroke of the hour ticks; the alarm tick uses the same, only in [ALARM_COLOR]. */
    private fun hourTickPaint(radius: Float, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        strokeWidth = radius * 0.02f
        strokeCap = Paint.Cap.ROUND
    }

    /** A tick identical to an hour tick except for its red color, at [alarm] if it lies in the shown time span. */
    private fun drawAlarmTick(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
        now: ZonedDateTime,
        alarm: Instant,
    ) {
        val angle = visibleAngleFor(alarm, settings, now) ?: return
        val paint = hourTickPaint(radius, ALARM_COLOR)
        val rad = Math.toRadians(angle - 90.0)
        val dx = cos(rad).toFloat()
        val dy = sin(rad).toFloat()
        val inner = radius * layout.hourTickInner
        val outer = radius * layout.tickOuter
        canvas.drawLine(cx + dx * inner, cy + dy * inner, cx + dx * outer, cy + dy * outer, paint)
    }

    /** 60 short ticks for the minute hand; every fifth is skipped since hour ticks (both modes) sit on multiples of 30°. */
    private fun drawMinuteTicks(canvas: Canvas, cx: Float, cy: Float, radius: Float, layout: DialLayout, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            strokeWidth = radius * 0.01f
            strokeCap = Paint.Cap.ROUND
        }
        val outer = radius * layout.tickOuter
        val inner = radius * layout.minuteTickInner
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
     * Daily moon path, plus the moon hand and phase disc with [MoonStyle.SEPARATE_HAND] (with
     * [MoonStyle.ON_HOUR_HAND] the disc is drawn together with the hands, see [drawMoonOnHourHand]).
     * The angle of each point comes from [moonPointAt]. The distance from the center shows the moon's
     * altitude: a faint circle marks the horizon, above it the moon climbs outward, below it sinks
     * inward (drawn faded). The path covers the same time window as the day/night ring (the active
     * 12h or 24h window, moving with it), so it always contains the current point. With
     * [WidgetSettings.showMoonCompass], a dot and letter mark where the moon passes north, east,
     * south or west.
     */
    private fun drawMoon(
        context: Context,
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
        now: ZonedDateTime,
        dark: Boolean,
    ) {
        fun pointAt(time: ZonedDateTime) = moonPointAt(time, cx, cy, radius, settings, layout)

        val mapper = mapperFor(settings)
        val pathStart = mapper.windowStart(now)
        val spanMinutes = Duration.between(pathStart, mapper.windowEnd(now)).toMinutes()
        val horizonPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            alpha = 60
            style = Paint.Style.STROKE
            strokeWidth = radius * 0.006f
        }
        canvas.drawCircle(cx, cy, radius * moonOrbit(settings, layout).first, horizonPaint)

        val pathPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            strokeWidth = radius * 0.008f
            strokeCap = Paint.Cap.ROUND
        }
        val compassPoints = mutableListOf<Pair<ZonedDateTime, Int>>()
        val stepSeconds = spanMinutes * 60.0 / MOON_PATH_SAMPLES
        var previousTime = pathStart
        var previous = pointAt(pathStart)
        for (i in 1..MOON_PATH_SAMPLES) {
            val time = pathStart.plusMinutes(spanMinutes * i / MOON_PATH_SAMPLES)
            val current = pointAt(time)
            pathPaint.alpha = if (previous.aboveHorizon || current.aboveHorizon) 200 else 80
            canvas.drawLine(previous.x, previous.y, current.x, current.y, pathPaint)
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
        drawCompassPoints(context, canvas, cx, cy, radius, compassPoints.map { (time, label) -> pointAt(time) to label })
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
            color = MARK_COLOR
            this.alpha = alpha
            strokeWidth = radius * 0.014f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx, cy, cx + dx * handScale, cy + dy * handScale, handPaint)

        val illumination = SunMoonCalculator.moonIllumination(now.toInstant())
        canvas.saveLayerAlpha(null, alpha)
        drawMoonPhase(canvas, mx, my, moonSize, illumination, moonLitColor(dark), moonShadowColor(dark))
        canvas.restore()
    }

    /**
     * Moon azimuths (SunCalc convention: from south, positive toward west) labeled on the path, with
     * their letter. North appears twice so that crossings from either side of ±180° are found.
     */
    private val COMPASS_POINTS = listOf(
        -180.0 to R.string.compass_north,
        -90.0 to R.string.compass_east,
        0.0 to R.string.compass_south,
        90.0 to R.string.compass_west,
        180.0 to R.string.compass_north,
    )

    /** A dot on the path at each of [points], with its letter just outside it (away from the center). */
    private fun drawCompassPoints(context: Context, canvas: Canvas, cx: Float, cy: Float, radius: Float, points: List<Pair<MoonPoint, Int>>) {
        val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = MARK_COLOR; style = Paint.Style.FILL }
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = MARK_COLOR
            textSize = radius * 0.065f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val bounds = Rect()
        for ((point, labelRes) in points) {
            val alpha = if (point.aboveHorizon) 255 else 130
            dotPaint.alpha = alpha
            textPaint.alpha = alpha
            canvas.drawCircle(point.x, point.y, radius * 0.014f, dotPaint)
            val dx = point.x - cx
            val dy = point.y - cy
            val distance = hypot(dx, dy).coerceAtLeast(1f)
            val offset = radius * 0.06f
            val label = context.getString(labelRes)
            textPaint.getTextBounds(label, 0, label.length, bounds)
            canvas.drawText(label, point.x + dx / distance * offset, point.y + dy / distance * offset + bounds.height() / 2f, textPaint)
        }
    }

    /**
     * Phase disc on the hour hand ([MoonStyle.ON_HOUR_HAND]), framed in the hand's [color] so that it
     * reads as part of the hand and a new moon stays visible. Drawn opaque so the hand does not show
     * through; below the horizon the lit part is dimmed instead.
     */
    private fun drawMoonOnHourHand(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
        handTime: ZonedDateTime,
        dark: Boolean,
        color: Int,
    ) {
        val (mx, my, aboveHorizon) = moonPointAt(handTime, cx, cy, radius, settings, layout)
        val moonSize = radius * MOON_ON_HAND_SIZE
        val shadowColor = moonShadowColor(dark)
        val litColor = if (aboveHorizon) moonLitColor(dark) else lerpColor(moonLitColor(dark), shadowColor, 0.55)
        drawMoonPhase(canvas, mx, my, moonSize, SunMoonCalculator.moonIllumination(handTime.toInstant()), litColor, shadowColor)
        val framePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
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
    private fun moonPointAt(
        time: ZonedDateTime,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
    ): MoonPoint {
        val dialTime = when (settings.moonStyle) {
            MoonStyle.ON_HOUR_HAND -> time
            MoonStyle.SEPARATE_HAND -> time.minusSeconds((SunMoonCalculator.moonLagHours(time.toInstant()) * 3600.0).toLong())
        }
        val angle = dialAngle(dialTime, settings.displayMode)
        val position = SunMoonCalculator.moonPosition(time.toInstant(), settings.place.latitude, settings.place.longitude)
        val r = radius * moonOrbitRadiusFactor(position.altitudeDegrees, settings, layout)
        val rad = Math.toRadians(angle - 90.0)
        return MoonPoint(cx + (cos(rad) * r).toFloat(), cy + (sin(rad) * r).toFloat(), position.altitudeDegrees > 0.0, position.azimuthDegrees)
    }

    private fun moonLitColor(dark: Boolean): Int = if (dark) Color.rgb(235, 235, 225) else Color.rgb(250, 250, 245)

    private fun moonShadowColor(dark: Boolean): Int = if (dark) Color.rgb(40, 40, 45) else Color.rgb(90, 90, 95)

    /** Moon distance from the center, as a fraction of the dial radius: the horizon circle offset by sin(altitude), outward above and inward below. */
    private fun moonOrbitRadiusFactor(altitudeDeg: Double, settings: WidgetSettings, layout: DialLayout): Float {
        val (horizon, amplitude) = moonOrbit(settings, layout)
        return horizon + amplitude * sin(Math.toRadians(altitudeDeg.coerceIn(-90.0, 90.0))).toFloat()
    }

    /**
     * Horizon radius and altitude amplitude of the moon path, as fractions of the dial radius. On the
     * hour hand the framed disc spans from the center (moon at the nadir) to just short of the hand's
     * tip (moon at the zenith), so a bit of the hand always shows beyond it.
     */
    private fun moonOrbit(settings: WidgetSettings, layout: DialLayout): Pair<Float, Float> = when (settings.moonStyle) {
        MoonStyle.SEPARATE_HAND -> layout.moonHorizon to layout.moonAmplitude
        MoonStyle.ON_HOUR_HAND -> {
            val disc = MOON_ON_HAND_SIZE + MOON_ON_HAND_FRAME
            val outer = layout.hourHand - MOON_ON_HAND_TIP - disc
            (outer + disc) / 2f to (outer - disc) / 2f
        }
    }

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

    private fun drawHands(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        radius: Float,
        settings: WidgetSettings,
        layout: DialLayout,
        now: ZonedDateTime,
        color: Int,
        dark: Boolean,
    ) {
        val handTime = now.withSecond(0)
        val hourAngle = dialAngle(handTime, settings.displayMode)
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

        drawHand(canvas, cx, cy, hourAngle, radius * layout.hourHand, hourPaint)
        if (settings.showMoon && settings.moonStyle == MoonStyle.ON_HOUR_HAND) {
            drawMoonOnHourHand(canvas, cx, cy, radius, settings, layout, handTime, dark, color)
        }
        drawHand(canvas, cx, cy, minuteAngle, radius * layout.minuteHand, minutePaint)
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
