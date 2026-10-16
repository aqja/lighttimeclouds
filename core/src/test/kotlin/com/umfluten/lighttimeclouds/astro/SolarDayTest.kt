package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class SolarDayTest {

    // Berlin, DE (TV tower) - reference values computed with PyEphem for 2026-10-15.
    private val lat = 52.5208
    private val lon = 13.4094
    private val zone = ZoneId.of("Europe/Berlin")

    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private fun assertNear(expected: ZonedDateTime, event: SolarEvent) {
        val actual = requireNotNull(event.instant) { "${event.type} missing" }
        val diff = Duration.between(expected.toInstant(), actual).abs()
        assertTrue("${event.type}: expected $expected, was ${actual.atZone(zone)}", diff <= Duration.ofMinutes(3))
    }

    @Test
    fun `events match reference times and run from solar midnight to solar midnight`() {
        val events = SolarDay.eventsAround(at(15, 10, 0).toInstant(), zone, lat, lon)

        assertEquals(SolarEventType.entries + SolarEventType.SOLAR_MIDNIGHT, events.map { it.type })
        assertNear(at(15, 0, 52), events.first())
        assertNear(at(15, 5, 38), events[1])
        assertNear(at(15, 7, 32), events.first { it.type == SolarEventType.SUNRISE })
        assertNear(at(15, 12, 52), events.first { it.type == SolarEventType.SOLAR_NOON })
        assertNear(at(15, 18, 12), events.first { it.type == SolarEventType.SUNSET })
        assertNear(at(15, 20, 5), events.first { it.type == SolarEventType.ASTRONOMICAL_DUSK })
        assertNear(at(16, 0, 52), events.last())
    }

    @Test
    fun `before solar midnight the list still shows the previous solar day`() {
        // 00:30 is before the 00:52 solar midnight, so the solar day began on the 14th.
        val events = SolarDay.eventsAround(at(15, 0, 30).toInstant(), zone, lat, lon)
        val first = requireNotNull(events.first().instant).atZone(zone)
        val last = requireNotNull(events.last().instant).atZone(zone)

        assertEquals(14, first.dayOfMonth)
        assertEquals(15, last.dayOfMonth)
        assertTrue(last.toLocalTime().isAfter(LocalTime.of(0, 30)))
    }

    // Longyearbyen, Svalbard - polar day in June, polar night in December.
    private val polarLat = 78.2232
    private val polarLon = 15.6267
    private val polarZone = ZoneId.of("Arctic/Longyearbyen")

    @Test
    fun `polar day and polar night yield a full list with missing sunrise and sunset but valid noon and midnights`() {
        for (month in listOf(6, 12)) {
            val now = ZonedDateTime.of(2026, month, 21, 12, 0, 0, 0, polarZone).toInstant()
            val events = SolarDay.eventsAround(now, polarZone, polarLat, polarLon)
            val byType = events.associateBy { it.type }

            assertEquals(15, events.size)
            assertEquals(null, byType.getValue(SolarEventType.SUNRISE).instant)
            assertEquals(null, byType.getValue(SolarEventType.SUNSET).instant)
            assertTrue(byType.getValue(SolarEventType.SOLAR_NOON).instant != null)
            assertTrue(events.first().instant!!.isBefore(now) && events.last().instant!!.isAfter(now))
        }
    }

    @Test
    fun `moon events at polar latitude stay well-formed over a whole month`() {
        val start = ZonedDateTime.of(2026, 12, 1, 0, 0, 0, 0, polarZone).toInstant()
        val end = start.plus(Duration.ofDays(30))
        val events = SolarDay.moonEventsBetween(start, end, polarZone, polarLat, polarLon)

        assertEquals(events.sortedBy { it.instant }, events)
        assertTrue(events.all { !it.instant.isBefore(start) && !it.instant.isAfter(end) })
    }

    @Test
    fun `at the equinox on the equator day and night are about twelve hours each`() {
        val quito = ZoneId.of("America/Guayaquil")
        val now = ZonedDateTime.of(2026, 3, 20, 12, 0, 0, 0, quito).toInstant()
        val byType = SolarDay.eventsAround(now, quito, -0.18, -78.47).associateBy { it.type }
        val day = Duration.between(byType.getValue(SolarEventType.SUNRISE).instant, byType.getValue(SolarEventType.SUNSET).instant)

        assertTrue("day length was $day", day > Duration.ofHours(11).plusMinutes(55) && day < Duration.ofHours(12).plusMinutes(15))
    }
}
