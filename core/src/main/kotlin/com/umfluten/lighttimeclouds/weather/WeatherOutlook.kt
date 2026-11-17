package com.umfluten.lighttimeclouds.weather

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** What falls. */
enum class PrecipitationKind { RAIN, SNOW }

/** The precipitation of the coming [WeatherOutlook.HORIZON_HOURS] hours, as a sentence can tell it. */
sealed interface PrecipitationOutlook {
    /** Nothing falls up to [until], the end of the horizon. */
    data class Dry(val until: TimeOfDay) : PrecipitationOutlook

    /** [kind] is falling now and stops around [end]; null if it lasts beyond the horizon. */
    data class Ongoing(val kind: PrecipitationKind, val end: TimeOfDay?) : PrecipitationOutlook

    /**
     * [kind] starts around [start], [likely] or only possible. [continues] if it goes on into a
     * later part of the day (or beyond the horizon), so "from the afternoon" rather than "in the
     * afternoon".
     */
    data class Coming(
        val kind: PrecipitationKind,
        val likely: Boolean,
        val start: TimeOfDay,
        val continues: Boolean,
    ) : PrecipitationOutlook
}

/** One thing worth saying about the weather; [WeatherOutlook.remarks] picks the ones to say. */
sealed interface Remark {
    /** Air temperature now minus 24 h earlier, in whole degrees. */
    data class TemperatureChange(val degrees: Int) : Remark

    /** Apparent minus air temperature now, in whole degrees. */
    data class FeelsLike(val offset: Int) : Remark

    data class Precipitation(val outlook: PrecipitationOutlook) : Remark
}

/**
 * A short assessment of the weather at one moment, the basis for a sentence or three: the change
 * of the air temperature since the same time yesterday, a notable gap between apparent and air
 * temperature, and the precipitation to come. Each part is null where the forecast lacks the data.
 */
data class WeatherOutlook(
    /** Air temperature now minus 24 h earlier, in whole degrees. */
    val changeSinceYesterday: Int?,
    /** Apparent minus air temperature now, in whole degrees; only if at least [NOTABLE_FEELS_LIKE] apart. */
    val feelsLikeOffset: Int?,
    val precipitation: PrecipitationOutlook?,
) {
    val isEmpty: Boolean get() = changeSinceYesterday == null && feelsLikeOffset == null && precipitation == null

    /**
     * The remarks worth saying, in reading order: the [MAX_REMARKS] most notable ones, topped up
     * to [MIN_REMARKS] with unremarkable ones ("about as warm as yesterday", "dry") so the text
     * never says nothing while it knows something. Deterministic, so the text stays put while
     * the weather does.
     */
    fun remarks(): List<Remark> {
        val all = listOfNotNull(
            changeSinceYesterday?.let { Remark.TemperatureChange(it) },
            feelsLikeOffset?.let { Remark.FeelsLike(it) },
            precipitation?.let { Remark.Precipitation(it) },
        )
        val notable = all.filter { weight(it) > 0 }.sortedByDescending(::weight).take(MAX_REMARKS)
        val filler = all.filter { weight(it) == 0 }.take((MIN_REMARKS - notable.size).coerceAtLeast(0))
        val chosen = (notable + filler).toSet()
        return all.filter { it in chosen }
    }

    companion object {
        /** From this change on (°C), it is "a bit" warmer or cooler than yesterday ... */
        const val NOTABLE_CHANGE = 2

        /** ... and from this one on "much". */
        const val LARGE_CHANGE = 5

        /** From this gap on (°C), the apparent temperature is worth a mention. */
        const val NOTABLE_FEELS_LIKE = 3

        /** From this amount (mm in one hour) on, rain or snow is likely whatever the probability. */
        const val LIKELY_PRECIPITATION_MM = 1.0

        /** A smaller forecast amount counts as likely from this probability (%) on. */
        const val LIKELY_PROBABILITY = 70.0

        /** Probability (%) from which precipitation is called possible, within [NEAR_HOURS] and later. */
        const val NEAR_PROBABILITY = 50.0
        const val LATER_PROBABILITY = 40.0
        const val NEAR_HOURS = 3

        /** How far ahead the precipitation is looked at. */
        const val HORIZON_HOURS = 12

        const val MAX_REMARKS = 3
        const val MIN_REMARKS = 2

        /** How notable [remark] is; 0 for one that is only worth saying when nothing else is. */
        fun weight(remark: Remark): Int = when (remark) {
            is Remark.TemperatureChange -> when {
                abs(remark.degrees) >= LARGE_CHANGE -> 70
                abs(remark.degrees) >= NOTABLE_CHANGE -> 40
                else -> 0
            }
            is Remark.FeelsLike -> 50
            is Remark.Precipitation -> when (val p = remark.outlook) {
                is PrecipitationOutlook.Dry -> 0
                is PrecipitationOutlook.Ongoing -> 90
                is PrecipitationOutlook.Coming -> when {
                    p.likely -> if (p.kind == PrecipitationKind.SNOW) 85 else 80
                    else -> if (p.kind == PrecipitationKind.SNOW) 65 else 55
                }
            }
        }

        /** Assesses [forecast] at [now], with parts of the day by the clock of [zone]. */
        fun of(forecast: WeatherForecast, now: Instant, zone: ZoneId): WeatherOutlook {
            val airNow = forecast.temperatureAt(now, TemperatureKind.AIR)
            val airYesterday = forecast.temperatureAt(now.minus(Duration.ofDays(1)), TemperatureKind.AIR)
            val apparentNow = forecast.temperatureAt(now, TemperatureKind.APPARENT)
            return WeatherOutlook(
                changeSinceYesterday = if (airNow != null && airYesterday != null) (airNow - airYesterday).roundToInt() else null,
                feelsLikeOffset = if (airNow != null && apparentNow != null) {
                    (apparentNow - airNow).roundToInt().takeIf { abs(it) >= NOTABLE_FEELS_LIKE }
                } else {
                    null
                },
                precipitation = precipitationOutlook(forecast, now, zone),
            )
        }

        /**
         * The first spell of precipitation within the horizon: from the first hour where it is
         * likely or possible to the first one of two dry hours in a row, so a single dry hour
         * between showers does not split the spell. Unknown if the forecast ends earlier.
         */
        private fun precipitationOutlook(forecast: WeatherForecast, now: Instant, zone: ZoneId): PrecipitationOutlook? {
            val firstHour = now.truncatedTo(ChronoUnit.HOURS)
            fun hourStart(i: Int): Instant = firstHour.plus(Duration.ofHours(i.toLong()))
            val conditions = (0 until HORIZON_HOURS).map { forecast.conditionAt(hourStart(it)) ?: return null }
            fun likely(i: Int) = conditions[i].let {
                it.precipitationMm >= LIKELY_PRECIPITATION_MM ||
                    (it.precipitationMm >= WeatherCondition.MIN_PRECIPITATION_MM && (it.precipitationProbabilityPercent ?: 100.0) >= LIKELY_PROBABILITY)
            }
            fun possible(i: Int) = (conditions[i].precipitationProbabilityPercent ?: 0.0) >= if (i < NEAR_HOURS) NEAR_PROBABILITY else LATER_PROBABILITY
            fun wet(i: Int) = likely(i) || possible(i)
            fun at(instant: Instant) = TimeOfDay.of(instant, now, zone)

            val start = conditions.indices.firstOrNull(::wet)
                ?: return PrecipitationOutlook.Dry(at(now.plus(Duration.ofHours(HORIZON_HOURS.toLong()))))
            val end = (start + 1 until HORIZON_HOURS).firstOrNull { !wet(it) && (it + 1 >= HORIZON_HOURS || !wet(it + 1)) }
            val wetHours = (start until (end ?: HORIZON_HOURS)).filter(::wet)
            val kind = if (wetHours.count { conditions[it].isSnow } * 2 >= wetHours.size) PrecipitationKind.SNOW else PrecipitationKind.RAIN
            // The last moment it falls, so rain until 18:00 ends in the afternoon, not the evening.
            val lastWet = end?.let { at(hourStart(it).minusMillis(1)) }
            return when {
                start == 0 && likely(0) -> PrecipitationOutlook.Ongoing(kind, lastWet)
                else -> {
                    val startTime = at(hourStart(start).coerceAtLeast(now))
                    PrecipitationOutlook.Coming(kind, wetHours.any(::likely), startTime, continues = lastWet != startTime)
                }
            }
        }

        private fun Instant.coerceAtLeast(other: Instant): Instant = if (isBefore(other)) other else this
    }
}
