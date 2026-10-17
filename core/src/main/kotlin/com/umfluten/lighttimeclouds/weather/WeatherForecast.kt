package com.umfluten.lighttimeclouds.weather

import java.time.Duration
import java.time.Instant
import kotlin.math.log10

/**
 * One hourly step as Open-Meteo reports it: [cloudCoverPercent] and the temperatures are instant
 * values at [time]; [precipitationMm] (rain, showers and snow as water), [snowfallCm] and
 * [precipitationProbabilityPercent] (chance of more than 0.1 mm) refer to the hour *before*
 * [time]. The optional values are null where the model has none.
 */
data class HourlyWeather(
    val time: Instant,
    val cloudCoverPercent: Double,
    val precipitationMm: Double,
    val snowfallCm: Double,
    val precipitationProbabilityPercent: Double? = null,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
)

/** Which temperature the dial labels show. */
enum class TemperatureKind { AIR, APPARENT }

/** Weather of one hour-long segment of the ring. */
data class WeatherCondition(
    val cloudCoverPercent: Double,
    val precipitationMm: Double,
    val snowfallCm: Double,
    val precipitationProbabilityPercent: Double? = null,
    val temperatureC: Double? = null,
) {
    private val hasAmount: Boolean get() = precipitationMm >= MIN_PRECIPITATION_MM

    /** True when the segment shows precipitation: a forecast amount, or only a chance of it. */
    val hasPrecipitation: Boolean
        get() = hasAmount || (precipitationProbabilityPercent ?: 0.0) > 0.0

    /**
     * Position on the precipitation color ramp, from 0 (0.1 mm/h, or only a chance) to 1
     * (10 mm/h and more), logarithmic so that 1 mm/h sits in the middle.
     */
    val precipitationLevel: Float
        get() = if (!hasAmount) 0f else (log10(precipitationMm / MIN_PRECIPITATION_MM) / 2.0).toFloat().coerceIn(0f, 1f)

    /**
     * Opacity of the precipitation color: its probability. A forecast amount is never drawn fainter
     * than [MIN_AMOUNT_ALPHA], even if the ensemble probability is lower; without a probability
     * (not every model has one) a forecast amount is drawn fully opaque.
     */
    val precipitationAlpha: Float
        get() {
            val probability = precipitationProbabilityPercent?.let { (it / 100.0).toFloat().coerceIn(0f, 1f) }
            return when {
                hasAmount -> maxOf(probability ?: 1f, MIN_AMOUNT_ALPHA)
                else -> probability ?: 0f
            }
        }

    /**
     * True when the precipitation is mainly snow: at least half of the forecast amount as snow
     * (Open-Meteo: 7 cm of snow ≈ 10 mm of water), or - with only a chance of precipitation -
     * a temperature at or below freezing.
     */
    val isSnow: Boolean
        get() = when {
            hasAmount -> snowfallCm * 10.0 / 7.0 >= precipitationMm / 2.0
            hasPrecipitation -> temperatureC != null && temperatureC <= 0.0
            else -> false
        }

    companion object {
        /** Below this, Open-Meteo's precipitation probability counts an hour as dry. */
        const val MIN_PRECIPITATION_MM = 0.1
        const val MIN_AMOUNT_ALPHA = 0.25f
    }
}

/**
 * Hourly forecast for the place at [latitude]/[longitude] (the requested coordinates, so a cache
 * can tell when the place has moved), fetched at [fetchedAt]. [hours] are in chronological order.
 */
data class WeatherForecast(
    val latitude: Double,
    val longitude: Double,
    val fetchedAt: Instant,
    val hours: List<HourlyWeather>,
) {
    /**
     * Weather of the full hour containing [instant]: the step ending at the next full hour supplies
     * the precipitation values, and the cloud cover and temperature are the means of that hour's
     * start and end values. Null outside the forecast range or where a step is missing.
     */
    fun conditionAt(instant: Instant): WeatherCondition? {
        val endIndex = firstIndexAfter(instant)
        val end = hours.getOrNull(endIndex) ?: return null
        if (Duration.between(instant, end.time) > STEP) return null
        val start = hours.getOrNull(endIndex - 1)?.takeIf { Duration.between(it.time, end.time) == STEP }
        return WeatherCondition(
            cloudCoverPercent = mean(start?.cloudCoverPercent, end.cloudCoverPercent)!!,
            precipitationMm = end.precipitationMm,
            snowfallCm = end.snowfallCm,
            precipitationProbabilityPercent = end.precipitationProbabilityPercent,
            temperatureC = mean(start?.temperatureC, end.temperatureC),
        )
    }

    /**
     * Temperature of [kind] at [instant], linearly interpolated between the surrounding hourly
     * values; null outside the forecast, across gaps, or where the model has no value.
     */
    fun temperatureAt(instant: Instant, kind: TemperatureKind): Double? {
        fun HourlyWeather.value() = when (kind) {
            TemperatureKind.AIR -> temperatureC
            TemperatureKind.APPARENT -> apparentTemperatureC
        }
        val afterIndex = firstIndexAfter(instant)
        val before = hours.getOrNull(afterIndex - 1) ?: return null
        if (before.time == instant) return before.value()
        val after = hours.getOrNull(afterIndex) ?: return null
        if (Duration.between(before.time, after.time) != STEP) return null
        val v0 = before.value() ?: return null
        val v1 = after.value() ?: return null
        val t = Duration.between(before.time, instant).toMillis().toDouble() / STEP.toMillis()
        return v0 + (v1 - v0) * t
    }

    /**
     * Mean temperature of [kind] from [start] to [end]: the average of the linearly interpolated
     * course (trapezoids between the hourly values), e.g. (T14 + 2·T15 + T16) / 4 for 14:00–16:00.
     * Null if any part of the span lacks data.
     */
    fun meanTemperature(start: Instant, end: Instant, kind: TemperatureKind): Double? {
        require(end.isAfter(start)) { "end must be after start" }
        val points = buildList {
            add(start)
            hours.asSequence().map { it.time }.filter { it.isAfter(start) && it.isBefore(end) }.forEach(::add)
            add(end)
        }
        var integral = 0.0
        var previous = temperatureAt(points.first(), kind) ?: return null
        for ((t0, t1) in points.zipWithNext()) {
            val current = temperatureAt(t1, kind) ?: return null
            integral += (previous + current) / 2.0 * Duration.between(t0, t1).toMillis()
            previous = current
        }
        return integral / Duration.between(start, end).toMillis()
    }

    /**
     * Opacity for drawing this forecast at [now]: full while fresh, fading once a refresh (every two
     * hours) was missed, and 0 (hidden) once it is too old to trust.
     */
    fun freshnessAlpha(now: Instant): Float {
        val age = Duration.between(fetchedAt, now)
        return when {
            age <= FRESH -> 1f
            age > EXPIRED -> 0f
            else -> {
                val t = (age - FRESH).toMinutes().toFloat() / (EXPIRED - FRESH).toMinutes()
                1f - t * (1f - STALE_ALPHA)
            }
        }
    }

    /** True when [latitude]/[longitude] are the coordinates this forecast was requested for. */
    fun isFor(latitude: Double, longitude: Double): Boolean =
        this.latitude == latitude && this.longitude == longitude

    /** Index of the first step strictly after [instant] (hours.size if none). */
    private fun firstIndexAfter(instant: Instant): Int {
        var low = 0
        var high = hours.size
        while (low < high) {
            val mid = (low + high) / 2
            if (hours[mid].time.isAfter(instant)) high = mid else low = mid + 1
        }
        return low
    }

    companion object {
        private val STEP: Duration = Duration.ofHours(1)
        private val FRESH: Duration = Duration.ofHours(4)
        private val EXPIRED: Duration = Duration.ofHours(12)
        private const val STALE_ALPHA = 0.4f

        /** Mean of both values; just [b] if [a] is missing. */
        private fun mean(a: Double?, b: Double?): Double? = if (a == null || b == null) b else (a + b) / 2.0
    }
}
