package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.abs

/**
 * Compares perigees, apogees and eclipses with reference values from JPL's DE440s, computed by
 * tools/reference_ephemeris.py (Skyfield) for 2000-2050 (geocentric) and 2000-2025 (solar eclipses
 * as seen from Berlin, Canberra, Madrid and Lusaka), with the same conventions.
 */
class LunarEventsTest {

    private fun rows(name: String): List<Map<String, String>> {
        val lines = requireNotNull(javaClass.getResourceAsStream("/astro/$name")).bufferedReader().readLines()
        val header = lines.first().split(",")
        return lines.drop(1).filter { it.isNotBlank() }.map { line -> header.zip(line.split(",", limit = header.size)).toMap() }
    }

    private val places = mapOf(
        "berlin" to (52.5208 to 13.4094),
        "canberra" to (-35.2809 to 149.1300),
        "madrid" to (40.4168 to -3.7038),
        "lusaka" to (-15.4167 to 28.2833),
    )

    private fun seconds(expected: String, actual: Instant?): Double {
        assertNotNull("missing $expected", actual)
        return Duration.between(Instant.parse(expected), actual).toMillis() / 1000.0
    }

    private class Worst(val label: String) {
        var value = 0.0
        var where = ""
        fun add(v: Double, at: String) {
            if (abs(v) > value) {
                value = abs(v)
                where = at
            }
        }
        override fun toString() = "$label %.3f at %s".format(value, where)
    }

    @Test
    fun `perigees and apogees match DE440s`() {
        val expected = rows("apsides.csv")
        val actual = LunarEvents.apsides(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2051-01-01T00:00:00Z"))
        assertEquals(expected.size, actual.size)
        val time = Worst("apsis s")
        val distance = Worst("apsis km")
        expected.zip(actual).forEach { (row, apsis) ->
            assertEquals(row.toString(), row.getValue("apsis") == "perigee", apsis.perigee)
            time.add(seconds(row.getValue("utc"), apsis.instant), row.getValue("utc"))
            distance.add(apsis.distanceKm - row.getValue("distance_km").toDouble(), row.getValue("utc"))
        }
        println("$time\n$distance")
        assertTrue(time.toString(), time.value <= LIMIT_APSIS_SECONDS)
        assertTrue(distance.toString(), distance.value <= LIMIT_DISTANCE_KM)
    }

    @Test
    fun `lunar eclipses match DE440s`() {
        val expected = rows("lunar_eclipses.csv")
        val actual = LunarEvents.fullMoons(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2051-01-01T00:00:00Z"))
            .mapNotNull(LunarEvents::lunarEclipse)
            .toList()
        assertEquals(expected.size, actual.size)
        val greatest = Worst("greatest s")
        val contacts = Worst("contact s")
        val magnitude = Worst("magnitude")
        expected.zip(actual).forEach { (row, eclipse) ->
            val at = row.getValue("greatest")
            assertEquals(at, row.getValue("kind").uppercase(), eclipse.kind.name)
            greatest.add(seconds(at, eclipse.greatest), at)
            magnitude.add(eclipse.umbralMagnitude - row.getValue("umbral_magnitude").toDouble(), at)
            magnitude.add(eclipse.penumbralMagnitude - row.getValue("penumbral_magnitude").toDouble(), at)
            listOf("p1" to "p4", "u1" to "u4", "u2" to "u3").forEach { (startColumn, endColumn) ->
                val (start, end) = when (startColumn) {
                    "p1" -> eclipse.penumbralStart to eclipse.penumbralEnd
                    "u1" -> eclipse.partialStart to eclipse.partialEnd
                    else -> eclipse.totalStart to eclipse.totalEnd
                }
                val expectedStart = row.getValue(startColumn)
                if (expectedStart.isEmpty()) {
                    assertNull("$at $startColumn", start)
                    assertNull("$at $endColumn", end)
                    return@forEach
                }
                // Where the Moon only just reaches into a shadow, its edge runs nearly along the
                // Moon's path, and an arcsecond moves the contacts the more, the shorter the phase.
                val duration = Duration.between(Instant.parse(expectedStart), Instant.parse(row.getValue(endColumn))).seconds
                val scale = LIMIT_ECLIPSE_SECONDS / maxOf(LIMIT_ECLIPSE_SECONDS, GRAZING_FRACTION * duration)
                contacts.add(seconds(expectedStart, start) * scale, "$at $startColumn")
                contacts.add(seconds(row.getValue(endColumn), end) * scale, "$at $endColumn")
            }
        }
        println("$greatest\n$contacts (scaled for short phases)\n$magnitude")
        assertTrue(greatest.toString(), greatest.value <= LIMIT_ECLIPSE_SECONDS)
        assertTrue(contacts.toString(), contacts.value <= LIMIT_ECLIPSE_SECONDS)
        assertTrue(magnitude.toString(), magnitude.value <= LIMIT_MAGNITUDE)
    }

    @Test
    fun `solar eclipses as seen from a place match DE440s`() {
        val greatest = Worst("greatest s")
        val contacts = Worst("contact s")
        val magnitude = Worst("magnitude")
        for ((place, coordinates) in places) {
            val (lat, lon) = coordinates
            val expected = rows("solar_eclipses.csv").filter { it["place"] == place }
            val actual = LunarEvents.newMoons(Instant.parse("2000-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))
                .mapNotNull { LunarEvents.solarEclipse(it, lat, lon) }
                .toList()
            assertEquals(place, expected.map { it.getValue("greatest").take(10) }, actual.map { it.greatest.toString().take(10) })
            expected.zip(actual).forEach { (row, eclipse) ->
                val at = "$place ${row.getValue("greatest")}"
                assertEquals(at, row.getValue("kind").uppercase(), eclipse.kind.name)
                greatest.add(seconds(row.getValue("greatest"), eclipse.greatest), at)
                magnitude.add(eclipse.magnitude - row.getValue("magnitude").toDouble(), at)
                magnitude.add(eclipse.obscuration - row.getValue("obscuration").toDouble(), at)
                listOf("c1" to eclipse.start, "c2" to eclipse.centralStart, "c3" to eclipse.centralEnd, "c4" to eclipse.end)
                    .forEach { (column, instant) ->
                        val value = row.getValue(column)
                        if (value.isEmpty()) assertNull("$at $column", instant) else contacts.add(seconds(value, instant), "$at $column")
                    }
            }
        }
        println("$greatest\n$contacts\n$magnitude")
        assertTrue(greatest.toString(), greatest.value <= LIMIT_SOLAR_SECONDS)
        assertTrue(contacts.toString(), contacts.value <= LIMIT_SOLAR_SECONDS)
        assertTrue(magnitude.toString(), magnitude.value <= LIMIT_MAGNITUDE)
    }

    @Test
    fun `the record perigee of November 2016 brings a supermoon`() {
        val fullMoon = LunarEvents.fullMoons(Instant.parse("2016-11-10T00:00:00Z"), Instant.parse("2016-11-20T00:00:00Z")).single()
        val apsides = LunarEvents.apsides(fullMoon.minus(Duration.ofDays(20)), fullMoon.plus(Duration.ofDays(20)))
        val notable = LunarEvents.notableFullMoon(fullMoon, apsides)
        assertNotNull(notable)
        assertTrue(notable!!.supermoon)
        assertEquals(356_500.0, notable.distanceKm, 100.0)
    }

    @Test
    fun `the full moon two weeks after a perigee is a micromoon`() {
        // 2025-04-13: full moon 0.6 days after apogee (406 300 km).
        val fullMoon = LunarEvents.fullMoons(Instant.parse("2025-04-10T00:00:00Z"), Instant.parse("2025-04-16T00:00:00Z")).single()
        val apsides = LunarEvents.apsides(fullMoon.minus(Duration.ofDays(20)), fullMoon.plus(Duration.ofDays(20)))
        val notable = LunarEvents.notableFullMoon(fullMoon, apsides)
        assertNotNull(notable)
        assertTrue(!notable!!.supermoon)
    }

    @Test
    fun `standstills follow the node, major ones in 2006 and early 2025, minor ones between`() {
        val major = LunarEvents.nextStandstill(Instant.parse("2020-01-01T00:00:00Z"), major = true)
        assertEquals("2025-01", major.instant.toString().take(7))
        assertEquals(28.6, major.declinationDeg, 0.1)
        val minor = LunarEvents.nextStandstill(major.instant, major = false)
        assertEquals(9.3, Duration.between(major.instant, minor.instant).toDays() / 365.25, 0.05)
        assertEquals(18.3, minor.declinationDeg, 0.1)
        val next = LunarEvents.nextStandstill(major.instant.plusSeconds(1), major = true)
        assertEquals(18.6, Duration.between(major.instant, next.instant).toDays() / 365.25, 0.05)
    }

    @Test
    fun `the coming events at Berlin, each kind once and in order`() {
        val after = Instant.parse("2026-11-27T12:00:00Z")
        val started = System.nanoTime()
        val events = LunarEvents.upcoming(after, 52.5208, 13.4094)
        println("upcoming in %d ms:".format((System.nanoTime() - started) / 1_000_000))
        events.forEach { println("  ${it.event} ${it.visibility}") }
        assertEquals(events.sortedBy { it.event.instant }, events)
        assertTrue(events.all { it.event.instant.isAfter(after.minus(Duration.ofHours(5))) })
        fun day(event: LunarEvent) = event.instant.toString().take(10)
        val supermoon = events.map { it.event }.filterIsInstance<NotableFullMoon>().single { it.supermoon }
        assertEquals("2026-12-24", day(supermoon))
        // A partial solar eclipse in August 2027, wholly in view (the one of August 2026 is past).
        val solar = events.single { it.event is SolarEclipse }
        assertEquals("2027-08-02", day(solar.event))
        assertEquals(EclipseKind.PARTIAL, (solar.event as SolarEclipse).kind)
        assertEquals(Visibility.WHOLE, solar.visibility)
        // A small partial lunar eclipse first, then a total one, the blood moon of New Year's Eve 2028.
        val lunar = events.map { it.event }.filterIsInstance<LunarEclipse>()
        assertEquals(listOf("2028-01-12", "2028-12-31"), lunar.map(::day))
        assertEquals(listOf(EclipseKind.PARTIAL, EclipseKind.TOTAL), lunar.map { it.kind })
        val standstills = events.map { it.event }.filterIsInstance<LunarStandstill>()
        assertEquals(listOf(false, true), standstills.map { it.major })
    }

    @Test
    fun `a disc covers the part its overlap says`() {
        assertEquals(0.0, LunarEvents.obscuration(1.0, 1.0, 2.0), 1e-12)
        assertEquals(1.0, LunarEvents.obscuration(1.0, 1.05, 0.01), 1e-12)
        assertEquals(0.81, LunarEvents.obscuration(1.0, 0.9, 0.05), 1e-12)
        // Two equal discs half a radius apart overlap by 68.5%.
        assertEquals(0.6850, LunarEvents.obscuration(1.0, 1.0, 0.5), 1e-4)
    }

    private companion object {
        /** The distance is flat around perigee and apogee: a few km of truncation move them by minutes. */
        const val LIMIT_APSIS_SECONDS = 300.0
        const val LIMIT_DISTANCE_KM = 5.0

        /** About 5″ of position at the Moon's 0.5″ per second against the shadow or the Sun. */
        const val LIMIT_ECLIPSE_SECONDS = 20.0

        /** For phases shorter than 200 s / this, the contacts may be off by this part of their duration. */
        const val GRAZING_FRACTION = 0.1

        /** As seen from a place, also UT1 ≈ UTC. */
        const val LIMIT_SOLAR_SECONDS = 30.0
        const val LIMIT_MAGNITUDE = 0.005
    }
}
