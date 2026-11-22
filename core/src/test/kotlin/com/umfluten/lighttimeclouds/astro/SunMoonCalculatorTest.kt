package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import kotlin.math.abs

class SunMoonCalculatorTest {

    @Test
    fun `solar noon is the altitude maximum for the day`() {
        // Berlin, DE (TV tower)
        val lat = 52.5208
        val lon = 13.4094
        val date = ZonedDateTime.of(2024, 6, 21, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val times = SunMoonCalculator.sunTimes(date, lat, lon)
        val noon = requireNotNull(times.solarNoon)

        val noonAltitude = SunMoonCalculator.sunAltitudeDegrees(noon, lat, lon)
        val threeHoursBefore = SunMoonCalculator.sunAltitudeDegrees(noon.minusSeconds(3 * 3600), lat, lon)
        val threeHoursAfter = SunMoonCalculator.sunAltitudeDegrees(noon.plusSeconds(3 * 3600), lat, lon)

        assertTrue(noonAltitude > threeHoursBefore)
        assertTrue(noonAltitude > threeHoursAfter)
    }

    @Test
    fun `sunrise precedes solar noon precedes sunset on a normal mid-latitude day`() {
        val lat = 52.5208
        val lon = 13.4094
        val date = ZonedDateTime.of(2024, 3, 20, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val times = SunMoonCalculator.sunTimes(date, lat, lon)

        val sunrise = assertNotNullAndGet(times.sunrise)
        val noon = assertNotNullAndGet(times.solarNoon)
        val sunset = assertNotNullAndGet(times.sunset)

        assertTrue(sunrise.isBefore(noon))
        assertTrue(noon.isBefore(sunset))
    }

    @Test
    fun `polar day near the north pole in summer has no sunset`() {
        val lat = 89.0
        val lon = 0.0
        val date = ZonedDateTime.of(2024, 6, 21, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val times = SunMoonCalculator.sunTimes(date, lat, lon)
        assertNull(times.sunrise)
        assertNull(times.sunset)
    }

    @Test
    fun `moon illumination fraction and phase stay within their defined ranges`() {
        val instant: Instant = ZonedDateTime.of(2024, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val illumination = SunMoonCalculator.moonIllumination(instant)
        assertTrue(illumination.fraction in 0.0..1.0)
        assertTrue(illumination.phase in 0.0..1.0)
    }

    private fun assertNotNullAndGet(instant: Instant?): Instant {
        assertNotNull(instant)
        return instant!!
    }

    @Test
    fun `moon lag is about zero at new moon and about twelve hours at full moon`() {
        val newMoon = ZonedDateTime.of(2024, 1, 11, 11, 57, 0, 0, ZoneOffset.UTC).toInstant()
        val fullMoon = ZonedDateTime.of(2024, 1, 25, 17, 54, 0, 0, ZoneOffset.UTC).toInstant()

        val lagAtNew = SunMoonCalculator.moonLagHours(newMoon)
        // Wraps around 0/24, so measure the distance to 0 on the circle.
        assertTrue("lag at new moon was $lagAtNew", minOf(lagAtNew, 24.0 - lagAtNew) < 1.0)
        assertEquals(12.0, SunMoonCalculator.moonLagHours(fullMoon), 1.0)
    }

    @Test
    fun `next full and new moon match published times to within two minutes`() {
        fun utc(y: Int, mo: Int, d: Int, h: Int, mi: Int) = ZonedDateTime.of(y, mo, d, h, mi, 0, 0, ZoneOffset.UTC).toInstant()
        fun assertNear(expected: Instant, actual: Instant) =
            assertTrue("expected $expected, was $actual", abs(Duration.between(expected, actual).seconds) <= 120)

        val start = utc(2024, 1, 1, 0, 0)
        assertNear(utc(2024, 1, 11, 11, 57), SunMoonCalculator.nextMoonPhase(start, 0.0))
        assertNear(utc(2024, 1, 25, 17, 54), SunMoonCalculator.nextMoonPhase(start, 0.5))
        // A total lunar and a partial solar eclipse.
        assertNear(utc(2025, 3, 14, 6, 55), SunMoonCalculator.nextMoonPhase(utc(2025, 3, 1, 0, 0), 0.5))
        assertNear(utc(2025, 3, 29, 10, 58), SunMoonCalculator.nextMoonPhase(utc(2025, 3, 15, 0, 0), 0.0))
    }

    @Test
    fun `next moon phase lies strictly after the start, even right at the event`() {
        val fullMoon = SunMoonCalculator.nextMoonPhase(ZonedDateTime.of(2024, 1, 20, 0, 0, 0, 0, ZoneOffset.UTC).toInstant(), 0.5)
        for (start in listOf(fullMoon.minusSeconds(1), fullMoon, fullMoon.plusSeconds(1))) {
            val next = SunMoonCalculator.nextMoonPhase(start, 0.5)
            assertTrue("$next after $start", next.isAfter(start))
            assertTrue(Duration.between(start, next).toDays() <= 30)
        }
        assertEquals(29.5, Duration.between(fullMoon, SunMoonCalculator.nextMoonPhase(fullMoon.plusSeconds(60), 0.5)).seconds / 86_400.0, 0.4)
    }

    @Test
    fun `upcoming moon phases name each phase once, in order, at its moment or beginning`() {
        val start = ZonedDateTime.of(2026, 11, 22, 12, 0, 0, 0, ZoneOffset.UTC).toInstant()
        val upcoming = SunMoonCalculator.upcomingMoonPhases(start)

        assertEquals(SunMoonCalculator.MoonPhase.entries.toSet(), upcoming.map { it.first }.toSet())
        assertEquals(upcoming.sortedBy { it.second }, upcoming)
        assertTrue(upcoming.all { it.second.isAfter(start) && Duration.between(start, it.second).toDays() < 30 })
        for ((phase, instant) in upcoming) {
            // Just after its moment or beginning the phase carries its own name.
            assertEquals(phase, SunMoonCalculator.moonPhaseOf(SunMoonCalculator.moonIllumination(instant.plusSeconds(60)).phase))
        }
    }

    /** The times themselves are checked against DE440s in [EphemerisReferenceTest]. */
    @Test
    fun `moonrise and moonset are horizon crossings in the right direction`() {
        val lat = 52.5208
        val lon = 13.4094
        val start = ZonedDateTime.of(2026, 10, 15, 0, 0, 0, 0, ZoneOffset.ofHours(2)).toInstant()
        val times = SunMoonCalculator.moonTimes(start, lat, lon)
        val rise = requireNotNull(times.rise)
        val set = requireNotNull(times.set)

        fun alt(instant: Instant) = SunMoonCalculator.moonAltitudeDegrees(instant, lat, lon)
        // Upper limb on the horizon: the center is about a quarter degree below it.
        assertEquals(-0.25, alt(rise), 0.1)
        assertEquals(-0.25, alt(set), 0.1)
        assertTrue(alt(rise.plusSeconds(1800)) > alt(rise.minusSeconds(1800)))
        assertTrue(alt(set.plusSeconds(1800)) < alt(set.minusSeconds(1800)))
    }

    @Test
    fun `moon phase names cover the cycle in eighths`() {
        assertEquals(SunMoonCalculator.MoonPhase.NEW_MOON, SunMoonCalculator.moonPhaseOf(0.01))
        assertEquals(SunMoonCalculator.MoonPhase.NEW_MOON, SunMoonCalculator.moonPhaseOf(0.97))
        assertEquals(SunMoonCalculator.MoonPhase.FIRST_QUARTER, SunMoonCalculator.moonPhaseOf(0.25))
        assertEquals(SunMoonCalculator.MoonPhase.FULL_MOON, SunMoonCalculator.moonPhaseOf(0.5))
        assertEquals(SunMoonCalculator.MoonPhase.WANING_CRESCENT, SunMoonCalculator.moonPhaseOf(0.88))
    }
}
