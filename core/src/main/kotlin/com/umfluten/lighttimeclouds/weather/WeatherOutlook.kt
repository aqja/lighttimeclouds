package com.umfluten.lighttimeclouds.weather

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.roundToInt

/** What falls. */
enum class PrecipitationKind { RAIN, SNOW }

/**
 * How a spell falls: [SHOWERS] with breaks (or more spells after it), [STEADY] for at least
 * [WeatherOutlook.STEADY_HOURS] hours without one. The models do not tell showers from steady
 * rain themselves, so this only reads the hourly course.
 */
enum class SpellCharacter { NORMAL, SHOWERS, STEADY }

/** Fresh snow a spell leaves, from [minCm] of snowfall on. */
enum class FreshSnow(val minCm: Double) { LITTLE(0.5), SOME(2.0), LOTS(10.0) }

/** The precipitation of the coming [WeatherOutlook.HORIZON_HOURS] hours, as a sentence can tell it. */
sealed interface PrecipitationOutlook {
    /** Nothing falls up to [until], the end of the horizon. */
    data class Dry(val until: TimeOfDay) : PrecipitationOutlook

    /**
     * A spell of [kind], at most as strong as [intensity], falling as [character], with
     * [snowfallCm] of snow in total (within the horizon).
     */
    sealed interface Spell : PrecipitationOutlook {
        val kind: PrecipitationKind
        val intensity: PrecipitationIntensity
        val character: SpellCharacter
        val snowfallCm: Double
        val likely: Boolean
    }

    /** A spell falling now that stops around [end]; null if it lasts beyond the horizon. */
    data class Ongoing(
        override val kind: PrecipitationKind,
        override val intensity: PrecipitationIntensity,
        val end: TimeOfDay?,
        override val character: SpellCharacter = SpellCharacter.NORMAL,
        override val snowfallCm: Double = 0.0,
    ) : Spell {
        override val likely: Boolean get() = true
    }

    /**
     * A spell starting around [start], [likely] or only possible. [continues] if it goes on into
     * a later part of the day (or beyond the horizon), so "from the afternoon" rather than "in
     * the afternoon".
     */
    data class Coming(
        override val kind: PrecipitationKind,
        override val intensity: PrecipitationIntensity,
        override val likely: Boolean,
        val start: TimeOfDay,
        val continues: Boolean,
        override val character: SpellCharacter = SpellCharacter.NORMAL,
        override val snowfallCm: Double = 0.0,
    ) : Spell
}

/** One thing worth saying about the weather; [WeatherOutlook.remarks] picks the ones to say. */
sealed interface Remark {
    /** Air temperature now minus 24 h earlier, in whole degrees. */
    data class TemperatureChange(val degrees: Int) : Remark

    /** Apparent minus air temperature now, in whole degrees. */
    data class FeelsLike(val offset: Int) : Remark

    data class Precipitation(val outlook: PrecipitationOutlook) : Remark

    /** The fresh snow the coming or falling snow leaves. */
    data class Snow(val amount: FreshSnow) : Remark

    data class Temperature(val course: TemperatureCourse) : Remark

    data class Clouds(val clouds: CloudOutlook) : Remark

    data class Warning(val warning: WeatherWarning) : Remark

    /** The strongest gusts within the horizon, first reaching their [level] around [at]. */
    data class Wind(val level: GustLevel, val at: TimeOfDay) : Remark

    data class Tomorrow(val outlook: TomorrowOutlook) : Remark
}

/** Weather that can be dangerous, the first time it is expected within the horizon. */
sealed interface WeatherWarning {
    /** Rain of at least [level] within one hour, at the strongest level around [at]. */
    data class HeavyRain(val level: HeavyRainWarning, val at: TimeOfDay) : WeatherWarning

    /** Rain at or below freezing, so glaze ice, from [at]. */
    data class FreezingRain(val at: TimeOfDay) : WeatherWarning
}

/** How often it rains or snows in the daytime. */
enum class DaytimePrecipitation { DRY, POSSIBLE, AT_TIMES, OFTEN }

/**
 * Tomorrow's daytime (9 to 18 o'clock) compared with today's: the highest temperature [change]
 * from today's [todayMaxC], and how often [kind] falls.
 */
data class TomorrowOutlook(
    val change: Int,
    val todayMaxC: Int,
    val precipitation: DaytimePrecipitation,
    val kind: PrecipitationKind,
)

/** How the sky changes, or that it does not. */
sealed interface CloudOutlook {
    /** Cloudy now, clear from [start]. */
    data class Clearing(val start: TimeOfDay) : CloudOutlook

    /** Clear now, cloudy from [start]. */
    data class CloudingOver(val start: TimeOfDay) : CloudOutlook

    /** Overcast up to [until], the end of the horizon. */
    data class Overcast(val until: TimeOfDay) : CloudOutlook

    /** Hardly a cloud up to [until], the end of the horizon. */
    data class Clear(val until: TimeOfDay) : CloudOutlook
}

/** The course of the air temperature in the coming hours, in whole degrees. */
sealed interface TemperatureCourse {
    /** Up to [maxC] around [at]. */
    data class Heat(val maxC: Int, val at: TimeOfDay) : TemperatureCourse

    /** Below freezing now and up to [until], at most [maxC]. */
    data class LastingFrost(val maxC: Int, val until: TimeOfDay) : TemperatureCourse

    /** Above freezing now, down to [minC] around [at]. */
    data class Frost(val minC: Int, val at: TimeOfDay) : TemperatureCourse

    /** [degrees] warmer until [until]. */
    data class Warming(val degrees: Int, val until: TimeOfDay) : TemperatureCourse

    /** [degrees] colder until [until]. */
    data class Cooling(val degrees: Int, val until: TimeOfDay) : TemperatureCourse
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
    val clouds: CloudOutlook? = null,
    val temperatureCourse: TemperatureCourse? = null,
    val warnings: List<WeatherWarning> = emptyList(),
    /** Only in the evening and at night. */
    val tomorrow: TomorrowOutlook? = null,
    val wind: Remark.Wind? = null,
) {
    val isEmpty: Boolean
        get() = changeSinceYesterday == null && feelsLikeOffset == null && precipitation == null && clouds == null &&
            temperatureCourse == null && warnings.isEmpty() && tomorrow == null && wind == null

    /**
     * The remarks worth saying, in reading order: the [MAX_REMARKS] most notable ones, topped up
     * to [MIN_REMARKS] with unremarkable ones ("about as warm as yesterday", "dry") so the text
     * never says nothing while it knows something. Deterministic, so the text stays put while
     * the weather does.
     */
    fun remarks(): List<Remark> {
        val all = warnings.map { Remark.Warning(it) } + listOfNotNull(
            wind,
            changeSinceYesterday?.let { Remark.TemperatureChange(it) },
            feelsLikeOffset?.let { Remark.FeelsLike(it) },
            temperatureCourse?.let { Remark.Temperature(it) },
            precipitation?.let { Remark.Precipitation(it) },
            freshSnow()?.let { Remark.Snow(it) },
            // Rain or snow already says it is cloudy.
            clouds?.takeUnless { it is CloudOutlook.Overcast && precipitation is PrecipitationOutlook.Spell }?.let { Remark.Clouds(it) },
            tomorrow?.let { Remark.Tomorrow(it) },
        )
        val notable = all.filter { weight(it) > 0 }.sortedByDescending(::weight).take(MAX_REMARKS)
        val filler = all.filter { weight(it) == 0 }.take((MIN_REMARKS - notable.size).coerceAtLeast(0))
        val chosen = (notable + filler).toSet()
        return all.filter { it in chosen }
    }

    /** The fresh snow worth a mention: only from snow that is likely, and only from [FreshSnow.LITTLE] on. */
    private fun freshSnow(): FreshSnow? {
        val spell = precipitation as? PrecipitationOutlook.Spell ?: return null
        if (spell.kind != PrecipitationKind.SNOW || !spell.likely) return null
        return FreshSnow.entries.lastOrNull { spell.snowfallCm >= it.minCm }
    }

    companion object {
        /** From this change on (°C), it is "a bit" warmer or cooler than yesterday ... */
        const val NOTABLE_CHANGE = 2

        /** ... and from this one on "much". */
        const val LARGE_CHANGE = 5

        /** From this gap on (°C), the apparent temperature is worth a mention. */
        const val NOTABLE_FEELS_LIKE = 3

        /** From this gap on (°C), the apparent temperature is among the first things to say. */
        const val LARGE_FEELS_LIKE = 8

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

        /** A spell without breaks of at least this many hours is steady rain or snow. */
        const val STEADY_HOURS = 6

        /** Cloud cover (%) from which an hour counts as cloudy, and up to which as clear ... */
        const val CLOUDY_PERCENT = 70.0
        const val CLEAR_PERCENT = 30.0

        /** ... and the bounds for a sky that stays overcast (7/8) or clear (2/8) all along. */
        const val OVERCAST_PERCENT = 87.5
        const val MOSTLY_CLEAR_PERCENT = 25.0

        /** From this temperature on (°C), the day is hot. */
        const val HEAT_C = 30

        /** From this rise or fall (°C) within the horizon on, the course is worth a mention. */
        const val NOTABLE_COURSE = 6

        /** Tomorrow's outlook compares the daytime from and until these hours. */
        const val DAYTIME_FROM_HOUR = 9
        const val DAYTIME_UNTIL_HOUR = 18

        /** From this many likely hours of the daytime on, it rains often. */
        const val OFTEN_HOURS = 4

        const val MAX_REMARKS = 3
        const val MIN_REMARKS = 2

        /** How notable [remark] is; 0 for one that is only worth saying when nothing else is. */
        fun weight(remark: Remark): Int = when (remark) {
            is Remark.TemperatureChange -> when {
                abs(remark.degrees) >= LARGE_CHANGE -> 70
                abs(remark.degrees) >= NOTABLE_CHANGE -> 40
                else -> 0
            }
            // A few degrees colder are common with any wind, so only a large gap ranks high.
            is Remark.FeelsLike -> when {
                abs(remark.offset) >= LARGE_FEELS_LIKE -> 65
                abs(remark.offset) >= 5 -> 45
                else -> 20
            }
            is Remark.Temperature -> when (remark.course) {
                is TemperatureCourse.Heat -> 60
                is TemperatureCourse.Frost -> 55
                is TemperatureCourse.LastingFrost -> 45
                is TemperatureCourse.Warming, is TemperatureCourse.Cooling -> 30
            }
            is Remark.Warning -> when (val w = remark.warning) {
                is WeatherWarning.HeavyRain -> 100 + 5 * w.level.ordinal
                is WeatherWarning.FreezingRain -> 95
            }
            is Remark.Tomorrow -> 50
            // Gusts from Beaufort 8 on are a warning, below only worth a mention.
            is Remark.Wind -> when (remark.level) {
                GustLevel.GUSTS -> 40
                GustLevel.STORM -> 75
                else -> 100 + remark.level.ordinal
            }
            is Remark.Clouds -> when (remark.clouds) {
                is CloudOutlook.Clearing -> 45
                is CloudOutlook.CloudingOver -> 35
                is CloudOutlook.Clear -> 25
                is CloudOutlook.Overcast -> 10
            }
            // Below any snow spell, so the amount is only said together with the snow.
            is Remark.Snow -> when (remark.amount) {
                FreshSnow.LITTLE -> 30
                FreshSnow.SOME -> 50
                FreshSnow.LOTS -> 60
            }
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
            val horizon = Horizon.of(forecast, now, zone)
            return WeatherOutlook(
                changeSinceYesterday = if (airNow != null && airYesterday != null) (airNow - airYesterday).roundToInt() else null,
                feelsLikeOffset = if (airNow != null && apparentNow != null) {
                    (apparentNow - airNow).roundToInt().takeIf { abs(it) >= NOTABLE_FEELS_LIKE }
                } else {
                    null
                },
                precipitation = horizon?.let(::precipitationOutlook),
                clouds = horizon?.let(::cloudOutlook),
                temperatureCourse = if (horizon != null && airNow != null) temperatureCourse(horizon, airNow) else null,
                warnings = horizon?.let(::warnings).orEmpty(),
                tomorrow = tomorrowOutlook(forecast, now, zone),
                wind = horizon?.let(::wind),
            )
        }

        private fun WeatherCondition.isLikely(): Boolean =
            precipitationMm >= LIKELY_PRECIPITATION_MM ||
                (precipitationMm >= WeatherCondition.MIN_PRECIPITATION_MM && (precipitationProbabilityPercent ?: 100.0) >= LIKELY_PROBABILITY)

        private fun WeatherCondition.isPossible(probability: Double): Boolean = (precipitationProbabilityPercent ?: 0.0) >= probability

        /** The strongest gusts within the horizon, if at least [GustLevel.GUSTS]. */
        private fun wind(h: Horizon): Remark.Wind? {
            fun level(c: WeatherCondition) = c.windGustsKmh?.let { kmh -> GustLevel.entries.lastOrNull { kmh >= it.minKmh } }
            val level = h.conditions.mapNotNull(::level).maxOrNull() ?: return null
            return Remark.Wind(level, h.atHour(h.conditions.indexOfFirst { level(it) == level }))
        }

        /** Heavy rain at its strongest level, and rain at or below freezing, each the first time within the horizon. */
        private fun warnings(h: Horizon): List<WeatherWarning> {
            val heavy = h.conditions.mapNotNull { it.heavyRainWarning }.maxOrNull()?.let { level ->
                WeatherWarning.HeavyRain(level, h.atHour(h.conditions.indexOfFirst { it.heavyRainWarning == level }))
            }
            val freezing = h.conditions.indexOfFirst { c ->
                !c.isSnow && (c.isLikely() || c.isPossible(NEAR_PROBABILITY)) && c.temperatureC != null && c.temperatureC <= 0.0
            }.takeIf { it >= 0 }?.let { WeatherWarning.FreezingRain(h.atHour(it)) }
            return listOfNotNull(heavy, freezing)
        }

        /**
         * Tomorrow's daytime against today's, in the evening and at night only, when the day's
         * own weather is mostly past. Null where the forecast does not cover both daytimes.
         */
        private fun tomorrowOutlook(forecast: WeatherForecast, now: Instant, zone: ZoneId): TomorrowOutlook? {
            if (DayPeriod.ofHour(now.atZone(zone).hour) !in setOf(DayPeriod.EVENING, DayPeriod.NIGHT)) return null
            val today = TimeOfDay.dayOf(now, zone)
            fun hours(day: LocalDate) = (DAYTIME_FROM_HOUR..DAYTIME_UNTIL_HOUR).map { day.atTime(it, 0).atZone(zone).toInstant() }
            fun maxTemperature(day: LocalDate): Double? = hours(day).map { forecast.temperatureAt(it, TemperatureKind.AIR) ?: return null }.max()
            val todayMax = maxTemperature(today) ?: return null
            val tomorrowMax = maxTemperature(today.plusDays(1)) ?: return null
            val conditions = hours(today.plusDays(1)).dropLast(1).map { forecast.conditionAt(it) ?: return null }
            val wet = conditions.filter { it.isLikely() || it.isPossible(NEAR_PROBABILITY) }
            val likelyHours = conditions.count { it.isLikely() }
            return TomorrowOutlook(
                change = (tomorrowMax - todayMax).roundToInt(),
                todayMaxC = todayMax.roundToInt(),
                precipitation = when {
                    wet.isEmpty() -> DaytimePrecipitation.DRY
                    likelyHours == 0 -> DaytimePrecipitation.POSSIBLE
                    likelyHours < OFTEN_HOURS -> DaytimePrecipitation.AT_TIMES
                    else -> DaytimePrecipitation.OFTEN
                },
                kind = if (wet.isNotEmpty() && wet.count { it.isSnow } * 2 >= wet.size) PrecipitationKind.SNOW else PrecipitationKind.RAIN,
            )
        }

        /**
         * The first spell of precipitation within the horizon: from the first hour where it is
         * likely or possible to the first one of two dry hours in a row, so a single dry hour
         * between showers does not split the spell.
         */
        private fun precipitationOutlook(h: Horizon): PrecipitationOutlook {
            val conditions = h.conditions
            fun likely(i: Int) = conditions[i].isLikely()
            fun possible(i: Int) = conditions[i].isPossible(if (i < NEAR_HOURS) NEAR_PROBABILITY else LATER_PROBABILITY)
            fun wet(i: Int) = likely(i) || possible(i)

            val start = conditions.indices.firstOrNull(::wet) ?: return PrecipitationOutlook.Dry(h.atEnd)
            val end = (start + 1 until HORIZON_HOURS).firstOrNull { !wet(it) && (it + 1 >= HORIZON_HOURS || !wet(it + 1)) }
            val wetHours = (start until (end ?: HORIZON_HOURS)).filter(::wet)
            val kind = if (wetHours.count { conditions[it].isSnow } * 2 >= wetHours.size) PrecipitationKind.SNOW else PrecipitationKind.RAIN
            // The strongest hour, in the same classes the ring draws its dots in.
            val intensity = wetHours.maxOf { conditions[it].precipitationIntensity ?: PrecipitationIntensity.SLIGHT }
            val spellHours = (end ?: HORIZON_HOURS) - start
            val moreLater = end != null && (end until HORIZON_HOURS).any(::wet)
            val character = when {
                wetHours.size < spellHours || moreLater -> SpellCharacter.SHOWERS
                wetHours.size >= STEADY_HOURS -> SpellCharacter.STEADY
                else -> SpellCharacter.NORMAL
            }
            val snowfallCm = wetHours.sumOf { conditions[it].snowfallCm }
            // The last moment it falls, so rain until 18:00 ends in the afternoon, not the evening.
            val lastWet = end?.let { h.at(h.hourStart(it).minusMillis(1)) }
            return when {
                start == 0 && likely(0) -> PrecipitationOutlook.Ongoing(kind, intensity, lastWet, character, snowfallCm)
                else -> {
                    val startTime = h.atHour(start)
                    PrecipitationOutlook.Coming(
                        kind, intensity, wetHours.any(::likely), startTime, continues = lastWet != startTime,
                        character = character, snowfallCm = snowfallCm,
                    )
                }
            }
        }

        /**
         * A change between cloudy and clear for at least two hours in a row, or a sky that stays
         * overcast or clear over the whole horizon; null for anything in between.
         */
        private fun cloudOutlook(h: Horizon): CloudOutlook? {
            val cover = h.conditions.map { it.cloudCoverPercent }
            fun cloudy(i: Int) = cover[i] >= CLOUDY_PERCENT
            fun clear(i: Int) = cover[i] <= CLEAR_PERCENT
            fun firstTwo(test: (Int) -> Boolean) = (2 until HORIZON_HOURS - 1).firstOrNull { test(it) && test(it + 1) }
            return when {
                cover.all { it >= OVERCAST_PERCENT } -> CloudOutlook.Overcast(h.atEnd)
                cover.all { it <= MOSTLY_CLEAR_PERCENT } -> CloudOutlook.Clear(h.atEnd)
                cloudy(0) && cloudy(1) -> firstTwo(::clear)?.let { CloudOutlook.Clearing(h.atHour(it)) }
                clear(0) && clear(1) -> firstTwo(::cloudy)?.let { CloudOutlook.CloudingOver(h.atHour(it)) }
                else -> null
            }
        }

        /**
         * The most notable course of the air temperature over the horizon, from [airNow]: heat,
         * lasting frost, frost to come, or a large rise or fall.
         */
        private fun temperatureCourse(h: Horizon, airNow: Double): TemperatureCourse? {
            val points = (1..HORIZON_HOURS).mapNotNull { i ->
                val time = h.hourStart(i)
                h.forecast.temperatureAt(time, TemperatureKind.AIR)?.let { time to it }
            } + (h.now to airNow)
            val (maxTime, max) = points.maxBy { it.second }
            val (minTime, min) = points.minBy { it.second }
            val maxC = max.roundToInt()
            val minC = min.roundToInt()
            return when {
                maxC >= HEAT_C -> TemperatureCourse.Heat(maxC, h.at(maxTime))
                maxC <= 0 -> TemperatureCourse.LastingFrost(maxC, h.atEnd)
                airNow > 0.0 && minC <= -1 -> TemperatureCourse.Frost(minC, h.at(minTime))
                (max - airNow).roundToInt() >= NOTABLE_COURSE && (max - airNow) >= (airNow - min) ->
                    TemperatureCourse.Warming((max - airNow).roundToInt(), h.at(maxTime))
                (airNow - min).roundToInt() >= NOTABLE_COURSE -> TemperatureCourse.Cooling((airNow - min).roundToInt(), h.at(minTime))
                else -> null
            }
        }
    }
}

/**
 * The hours from the one containing [now] to [WeatherOutlook.HORIZON_HOURS] later, with their
 * [conditions]; hours are full UTC hours like the forecast's steps.
 */
private class Horizon(val forecast: WeatherForecast, val now: Instant, val zone: ZoneId, val conditions: List<WeatherCondition>) {
    private val firstHour: Instant = now.truncatedTo(ChronoUnit.HOURS)

    fun hourStart(i: Int): Instant = firstHour.plus(Duration.ofHours(i.toLong()))

    fun at(instant: Instant): TimeOfDay = TimeOfDay.of(instant, now, zone)

    /** When hour [i] starts, but not before now. */
    fun atHour(i: Int): TimeOfDay = at(hourStart(i).let { if (it.isBefore(now)) now else it })

    /** The end of the horizon. */
    val atEnd: TimeOfDay get() = at(now.plus(Duration.ofHours(WeatherOutlook.HORIZON_HOURS.toLong())))

    companion object {
        /** Null if the forecast does not cover the whole horizon. */
        fun of(forecast: WeatherForecast, now: Instant, zone: ZoneId): Horizon? {
            val firstHour = now.truncatedTo(ChronoUnit.HOURS)
            val conditions = (0 until WeatherOutlook.HORIZON_HOURS).map {
                forecast.conditionAt(firstHour.plus(Duration.ofHours(it.toLong()))) ?: return null
            }
            return Horizon(forecast, now, zone, conditions)
        }
    }
}
