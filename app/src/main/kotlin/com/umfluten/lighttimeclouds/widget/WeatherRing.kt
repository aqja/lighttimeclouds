package com.umfluten.lighttimeclouds.widget

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import com.umfluten.lighttimeclouds.weather.HeavyRainWarning
import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity
import com.umfluten.lighttimeclouds.weather.WeatherCondition
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Weather ring opacity for hours already past, so the current moment stands out. */
internal const val WEATHER_PAST_ALPHA = 0.5f

/**
 * Cloud cover and precipitation of the shown time span, in hour-long segments, as two layers:
 * clouds as grey that is transparent under a clear sky and grows opaque with cloud cover; on
 * top, precipitation as an even lattice of dots, larger with each intensity class and as opaque
 * as it is likely: blue for rain (yellow, orange, red at the heavy rain warning levels), violet
 * for snow. Past hours are dimmed, and the whole ring fades as the forecast ages
 * ([WeatherForecast.freshnessAlpha]).
 */
internal fun Dial.drawWeatherRing(forecast: WeatherForecast) {
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
    drawPrecipitationDots(radius * bands.weatherInner, radius * bands.weatherOuter, precipitation, dotSizes)
}

/**
 * Precipitation dots on a fixed hexagonal lattice of [DOT_ROWS] staggered rows across the band
 * between [inner] and [outer], every dot equally far from its neighbors. [sizes] gives the dot
 * radius per ring sample as a fraction of that distance.
 */
private fun Dial.drawPrecipitationDots(inner: Float, outer: Float, colors: IntArray, sizes: FloatArray) {
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

internal fun withAlphaFactor(color: Int, factor: Float): Int =
    Color.argb((Color.alpha(color) * factor).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
