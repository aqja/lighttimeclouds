package com.umfluten.lighttimeclouds.astro

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Sun events of one solar day, in chronological order. */
enum class SolarEventType {
    SOLAR_MIDNIGHT,
    ASTRONOMICAL_DAWN,
    NAUTICAL_DAWN,
    CIVIL_DAWN,
    SUNRISE,
    SUNRISE_END,
    GOLDEN_HOUR_END,
    SOLAR_NOON,
    GOLDEN_HOUR_START,
    SUNSET_START,
    SUNSET,
    CIVIL_DUSK,
    NAUTICAL_DUSK,
    ASTRONOMICAL_DUSK,
}

/** [instant] is null when the event does not occur that day (polar day/night, white nights). */
data class SolarEvent(val type: SolarEventType, val instant: Instant?)

enum class MoonEventType { MOONRISE, MOONSET }

data class MoonEvent(val type: MoonEventType, val instant: Instant)

object SolarDay {

    /**
     * All sun events of the solar day containing [now]: from the most recent solar midnight up to
     * and including the next one (so [SolarEventType.SOLAR_MIDNIGHT] appears first and last).
     * The list therefore switches over at solar midnight, not at civil midnight.
     */
    fun eventsAround(now: Instant, zone: ZoneId, latitude: Double, longitude: Double): List<SolarEvent> {
        fun timesFor(date: LocalDate) =
            SunMoonCalculator.sunTimes(date.atTime(12, 0).atZone(zone).toInstant(), latitude, longitude)

        val date = now.atZone(zone).toLocalDate()
        val times = listOf(date.minusDays(1), date, date.plusDays(1)).map(::timesFor)
            .last { !it.solarMidnight.isAfter(now) }

        return listOf(
            SolarEvent(SolarEventType.SOLAR_MIDNIGHT, times.solarMidnight),
            SolarEvent(SolarEventType.ASTRONOMICAL_DAWN, times.astronomicalDawn),
            SolarEvent(SolarEventType.NAUTICAL_DAWN, times.nauticalDawn),
            SolarEvent(SolarEventType.CIVIL_DAWN, times.civilDawn),
            SolarEvent(SolarEventType.SUNRISE, times.sunrise),
            SolarEvent(SolarEventType.SUNRISE_END, times.sunriseEnd),
            SolarEvent(SolarEventType.GOLDEN_HOUR_END, times.goldenHourEnd),
            SolarEvent(SolarEventType.SOLAR_NOON, times.solarNoon),
            SolarEvent(SolarEventType.GOLDEN_HOUR_START, times.goldenHourStart),
            SolarEvent(SolarEventType.SUNSET_START, times.sunsetStart),
            SolarEvent(SolarEventType.SUNSET, times.sunset),
            SolarEvent(SolarEventType.CIVIL_DUSK, times.civilDusk),
            SolarEvent(SolarEventType.NAUTICAL_DUSK, times.nauticalDusk),
            SolarEvent(SolarEventType.ASTRONOMICAL_DUSK, times.astronomicalDusk),
            SolarEvent(SolarEventType.SOLAR_MIDNIGHT, times.nextSolarMidnight),
        )
    }

    /** All moonrises and moonsets in [[start], [end]], chronologically; there can be none or several. */
    fun moonEventsBetween(start: Instant, end: Instant, zone: ZoneId, latitude: Double, longitude: Double): List<MoonEvent> {
        val firstDay = start.atZone(zone).toLocalDate().minusDays(1)
        val lastDay = end.atZone(zone).toLocalDate()
        return generateSequence(firstDay) { it.plusDays(1) }
            .takeWhile { !it.isAfter(lastDay) }
            .flatMap { date ->
                val times = SunMoonCalculator.moonTimes(date.atStartOfDay(zone).toInstant(), latitude, longitude)
                listOfNotNull(
                    times.rise?.let { MoonEvent(MoonEventType.MOONRISE, it) },
                    times.set?.let { MoonEvent(MoonEventType.MOONSET, it) },
                )
            }
            .filter { !it.instant.isBefore(start) && !it.instant.isAfter(end) }
            .distinctBy { it.type to it.instant.epochSecond / 600 }
            .sortedBy { it.instant }
            .toList()
    }
}
