package com.umfluten.lighttimeclouds.weather

import java.time.Duration
import java.time.Instant
import kotlin.math.abs
import kotlin.math.roundToInt

/** What falls in the coming hours, from most to least notable; [DRY] if nothing does. */
enum class PrecipitationKind { SNOW, RAIN, CHANCE, DRY }

/** The most notable precipitation within the next [hours] hours. */
data class PrecipitationOutlook(val kind: PrecipitationKind, val hours: Int)

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

    companion object {
        /** From this change on (°C), it is "a bit" warmer or cooler than yesterday ... */
        const val NOTABLE_CHANGE = 2

        /** ... and from this one on "much". */
        const val LARGE_CHANGE = 5

        /** From this gap on (°C), the apparent temperature is worth a mention. */
        const val NOTABLE_FEELS_LIKE = 3

        /** From this amount (mm in one hour) on, rain or snow is expected rather than possible. */
        const val LIKELY_PRECIPITATION_MM = 1.0

        const val SOON_HOURS = 3
        const val LATER_HOURS = 6

        /** Probability from which precipitation is called possible, for the near and the farther window. */
        const val SOON_PROBABILITY = 50.0
        const val LATER_PROBABILITY = 40.0

        /**
         * Assesses [forecast] at [now]. Precipitation is checked for the next [SOON_HOURS] hours
         * first and then for the next [LATER_HOURS]; within each window snow outranks rain, and
         * an expected amount outranks a mere chance.
         */
        fun of(forecast: WeatherForecast, now: Instant): WeatherOutlook {
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
                precipitation = precipitationOutlook(forecast, now),
            )
        }

        private fun precipitationOutlook(forecast: WeatherForecast, now: Instant): PrecipitationOutlook? {
            // The hour containing now and the ones after it; unknown if the forecast ends earlier.
            val conditions = (0 until LATER_HOURS).map { forecast.conditionAt(now.plus(Duration.ofHours(it.toLong()))) ?: return null }
            for ((hours, probability) in listOf(SOON_HOURS to SOON_PROBABILITY, LATER_HOURS to LATER_PROBABILITY)) {
                val window = conditions.take(hours)
                fun WeatherCondition.likely() = precipitationMm >= LIKELY_PRECIPITATION_MM
                fun WeatherCondition.possible() = (precipitationProbabilityPercent ?: 0.0) >= probability
                val kind = when {
                    window.any { it.isSnow && (it.likely() || it.possible()) } -> PrecipitationKind.SNOW
                    window.any { it.likely() } -> PrecipitationKind.RAIN
                    window.any { it.possible() } -> PrecipitationKind.CHANCE
                    else -> null
                }
                if (kind != null) return PrecipitationOutlook(kind, hours)
            }
            return PrecipitationOutlook(PrecipitationKind.DRY, LATER_HOURS)
        }
    }
}
