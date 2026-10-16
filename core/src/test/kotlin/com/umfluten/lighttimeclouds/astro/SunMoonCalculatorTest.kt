package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime

class SunMoonCalculatorTest {

    @Test
    fun `classify buckets match the defined altitude thresholds`() {
        assertEquals(SunMoonCalculator.SkyState.NIGHT, SunMoonCalculator.classify(-30.0))
        assertEquals(SunMoonCalculator.SkyState.ASTRONOMICAL_TWILIGHT, SunMoonCalculator.classify(-15.0))
        assertEquals(SunMoonCalculator.SkyState.NAUTICAL_TWILIGHT, SunMoonCalculator.classify(-9.0))
        assertEquals(SunMoonCalculator.SkyState.CIVIL_TWILIGHT, SunMoonCalculator.classify(-3.0))
        assertEquals(SunMoonCalculator.SkyState.GOLDEN_HOUR, SunMoonCalculator.classify(2.0))
        assertEquals(SunMoonCalculator.SkyState.DAY, SunMoonCalculator.classify(45.0))
    }

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
    fun `moonrise and moonset are horizon crossings in the right direction`() {
        val lat = 52.5208
        val lon = 13.4094
        val start = ZonedDateTime.of(2026, 10, 15, 0, 0, 0, 0, ZoneOffset.ofHours(2)).toInstant()
        val times = SunMoonCalculator.moonTimes(start, lat, lon)
        val rise = requireNotNull(times.rise)
        val set = requireNotNull(times.set)

        fun alt(instant: Instant) = SunMoonCalculator.moonAltitudeDegrees(instant, lat, lon)
        assertEquals(0.133, alt(rise), 0.3)
        assertEquals(0.133, alt(set), 0.3)
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
