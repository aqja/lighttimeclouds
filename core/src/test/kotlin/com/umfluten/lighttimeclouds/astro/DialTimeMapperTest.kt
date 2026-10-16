package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DialTimeMapperTest {

    private val zone = ZoneOffset.UTC

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2024, 6, 15, hour, minute, 0, 0, zone)

    @Test
    fun `fixed times - within day window maps hour-hand reading to the matching afternoon instant`() {
        val mapper = DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0))
        val now = at(10, 0) // inside [06:00, 18:00)

        // Hour-hand reading "2" (angle 60 deg) during the day window must mean 14:00, not 02:00.
        val resolved = mapper.resolve(now, 60.0)
        assertEquals(at(14, 0), resolved)
    }

    @Test
    fun `fixed times - within day window hour-hand reading before window start means same morning`() {
        val mapper = DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0))
        val now = at(10, 0)

        // Hour-hand reading "9" (angle 270 deg) during the day window means 09:00 that same morning.
        val resolved = mapper.resolve(now, 270.0)
        assertEquals(at(9, 0), resolved)
    }

    @Test
    fun `fixed times - night window maps hour-hand reading to the matching instant`() {
        val mapper = DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0))
        val now = at(22, 0) // inside [18:00, 06:00 next day)

        // Hour-hand reading "11" (angle 330 deg) during the night window means 23:00 that evening.
        val resolved = mapper.resolve(now, 330.0)
        assertEquals(at(23, 0), resolved)
    }

    @Test
    fun `fixed times - angle 0 and 360 are equivalent`() {
        val mapper = DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0))
        val now = at(10, 0)
        assertEquals(mapper.resolve(now, 0.0), mapper.resolve(now, 360.0))
    }

    @Test
    fun `trailing offset - angle exactly at the window start resolves to now minus the offset`() {
        val mapper = DialTimeMapper.TrailingOffset(offsetMinutes = 30)
        val now = at(10, 0)

        // Window starts at 09:30; hour-hand reading 9.5 (angle 285 deg) must resolve to exactly that.
        val resolved = mapper.resolve(now, 285.0)
        assertEquals(at(9, 30), resolved)
    }

    @Test
    fun `trailing offset - angle just ahead of the seam resolves one 12h cycle later`() {
        val mapper = DialTimeMapper.TrailingOffset(offsetMinutes = 30)
        val now = at(10, 0)

        // Just before the window start (9.5 - small delta) must wrap to the *next* cycle,
        // i.e. nearly 12h after the seam, not nearly 12h before it.
        val resolved = mapper.resolve(now, 284.0)
        assertEquals(at(21, 28), resolved)
    }

    @Test
    fun `window start is the most recent switch time for fixed times and now minus offset for trailing`() {
        val fixed = DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0))
        assertEquals(at(6, 0), fixed.windowStart(at(10, 0)))
        assertEquals(at(18, 0), fixed.windowStart(at(23, 30)))
        assertEquals(at(18, 0).minusDays(1), fixed.windowStart(at(2, 0)))

        val trailing = DialTimeMapper.TrailingOffset(30)
        assertEquals(at(9, 30), trailing.windowStart(at(10, 0)))
    }

    @Test
    fun `every angle resolves into the active 12h window, for both strategies around the clock`() {
        val mappers = listOf(
            DialTimeMapper.FixedTimes(LocalTime.of(6, 0), LocalTime.of(18, 0)),
            DialTimeMapper.FixedTimes(LocalTime.of(7, 30), LocalTime.of(19, 30)),
            DialTimeMapper.TrailingOffset(0),
            DialTimeMapper.TrailingOffset(180),
        )
        for (mapper in mappers) {
            for (minuteOfDay in 0 until 24 * 60 step 7) {
                val now = at(0).plusMinutes(minuteOfDay.toLong())
                val start = mapper.windowStart(now)
                val end = start.plusHours(12)
                assertTrue("$mapper at $now: window $start must contain now", !now.isBefore(start) && now.isBefore(end))
                for (angle in 0 until 360 step 5) {
                    val resolved = mapper.resolve(now, angle.toDouble())
                    assertTrue("$mapper at $now, angle $angle -> $resolved", !resolved.isBefore(start) && resolved.isBefore(end))
                }
            }
        }
    }
}
