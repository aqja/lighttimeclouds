package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

class DialTimeMapperTest {

    private val zone = ZoneOffset.UTC

    private fun at(hour: Int, minute: Int = 0): ZonedDateTime =
        ZonedDateTime.of(2024, 6, 15, hour, minute, 0, 0, zone)

    @Test
    fun `fixed times - within day window maps hour-hand reading to the matching afternoon instant`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val now = at(10, 0) // inside [06:00, 18:00)

        // Hour-hand reading "2" (angle 60 deg) during the day window must mean 14:00, not 02:00.
        val resolved = mapper.resolve(now, 60.0)
        assertEquals(at(14, 0), resolved)
    }

    @Test
    fun `fixed times - within day window hour-hand reading before window start means same morning`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val now = at(10, 0)

        // Hour-hand reading "9" (angle 270 deg) during the day window means 09:00 that same morning.
        val resolved = mapper.resolve(now, 270.0)
        assertEquals(at(9, 0), resolved)
    }

    @Test
    fun `fixed times - night window maps hour-hand reading to the matching instant`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val now = at(22, 0) // inside [18:00, 06:00 next day)

        // Hour-hand reading "11" (angle 330 deg) during the night window means 23:00 that evening.
        val resolved = mapper.resolve(now, 330.0)
        assertEquals(at(23, 0), resolved)
    }

    @Test
    fun `fixed times - angle 0 and 360 are equivalent`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
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
        val fixed = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        assertEquals(at(6, 0), fixed.windowStart(at(10, 0)))
        assertEquals(at(18, 0), fixed.windowStart(at(23, 30)))
        assertEquals(at(18, 0).minusDays(1), fixed.windowStart(at(2, 0)))

        val trailing = DialTimeMapper.TrailingOffset(30)
        assertEquals(at(9, 30), trailing.windowStart(at(10, 0)))
    }

    @Test
    fun `24h fixed time - the day starts at the switch time`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(4, 0)), dialHours = 24)
        assertEquals(at(4, 0), mapper.windowStart(at(10, 0)))
        assertEquals(at(4, 0).minusDays(1), mapper.windowStart(at(2, 0)))

        // At 02:00 the window runs from yesterday 04:00: "03:00" (angle 45 deg) is still ahead tonight,
        // "05:00" (angle 75 deg) was yesterday morning.
        assertEquals(at(3, 0), mapper.resolve(at(2, 0), 45.0))
        assertEquals(at(5, 0).minusDays(1), mapper.resolve(at(2, 0), 75.0))
    }

    @Test
    fun `24h trailing - angle just ahead of the seam resolves almost a day later`() {
        val mapper = DialTimeMapper.TrailingOffset(offsetMinutes = 30, dialHours = 24)
        val now = at(10, 0)
        assertEquals(at(9, 30), mapper.resolve(now, 142.5))
        assertEquals(at(9, 26).plusDays(1), mapper.resolve(now, 141.5))
    }

    @Test
    fun `every angle resolves into the active window, for both strategies and dial sizes around the clock`() {
        val mappers = listOf(
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0))),
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(7, 30), LocalTime.of(19, 30))),
            DialTimeMapper.TrailingOffset(0),
            DialTimeMapper.TrailingOffset(180),
            DialTimeMapper.FixedTimes(listOf(LocalTime.MIDNIGHT), dialHours = 24),
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(5, 45)), dialHours = 24),
            DialTimeMapper.TrailingOffset(0, dialHours = 24),
            DialTimeMapper.TrailingOffset(180, dialHours = 24),
        )
        for (mapper in mappers) {
            for (minuteOfDay in 0 until 24 * 60 step 7) {
                val now = at(0).plusMinutes(minuteOfDay.toLong())
                val start = mapper.windowStart(now)
                val end = mapper.windowEnd(now)
                assertTrue("$mapper at $now: window $start must contain now", !now.isBefore(start) && now.isBefore(end))
                for (angle in 0 until 360 step 5) {
                    val resolved = mapper.resolve(now, angle.toDouble())
                    assertTrue("$mapper at $now, angle $angle -> $resolved", !resolved.isBefore(start) && resolved.isBefore(end))
                }
            }
        }
    }

    // --- DST changes (Europe/Berlin: 2026-03-29 02:00 -> 03:00, 2026-10-25 03:00 -> 02:00) ---

    private val berlin = ZoneId.of("Europe/Berlin")
    private val cest = ZoneOffset.ofHours(2)
    private val cet = ZoneOffset.ofHours(1)

    private fun berlinAt(month: Int, day: Int, hour: Int, minute: Int = 0, offset: ZoneOffset? = null): ZonedDateTime =
        ZonedDateTime.ofLocal(LocalDateTime.of(2026, month, day, hour, minute), berlin, offset)

    /** Angle of the hour hand at [time]'s wall-clock reading. */
    private fun handAngle(time: ZonedDateTime, dialHours: Int): Double =
        ((time.hour % dialHours) * 60.0 + time.minute) / (dialHours * 60.0) * 360.0

    @Test
    fun `end of DST - every dial position still shows its wall-clock hour after the switch`() {
        val day = DialTimeMapper.FixedTimes(listOf(LocalTime.MIDNIGHT), dialHours = 24)
        for (hour in 0 until 24) {
            assertEquals("24h dial at $hour:00", hour, day.resolve(berlinAt(10, 25, 15, 30), hour * 15.0).hour)
        }
        // The night window runs from 18:00 CEST to 06:00 CET, 13 real hours.
        val night = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val now = berlinAt(10, 25, 4, 30)
        assertEquals(berlinAt(10, 25, 5), night.resolve(now, 150.0))
        assertEquals(now, night.resolve(now, handAngle(now, 12)))
        assertEquals(Duration.ofHours(13), Duration.between(night.windowStart(now), night.windowEnd(now)))
    }

    @Test
    fun `end of DST - in the repeated hour the dial shows the pass the clock is in`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val firstPass = berlinAt(10, 25, 2, 30, cest)
        val secondPass = berlinAt(10, 25, 2, 30, cet)
        assertEquals(firstPass, mapper.resolve(firstPass, 75.0))
        assertEquals(secondPass, mapper.resolve(secondPass, 75.0))
        assertEquals(berlinAt(10, 25, 2, 45, cet), mapper.resolve(secondPass, 82.5))
    }

    @Test
    fun `start of DST - the skipped hour shows the moment of the jump`() {
        val mapper = DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0)))
        val now = berlinAt(3, 29, 4, 30)
        val jump = berlinAt(3, 29, 3, 0)
        assertEquals(berlinAt(3, 29, 1, 30), mapper.resolve(now, 45.0))
        assertEquals(jump, mapper.resolve(now, 60.0))
        assertEquals(jump, mapper.resolve(now, 75.0))
        assertEquals(jump, mapper.resolve(now, 90.0))
        assertEquals(berlinAt(3, 29, 4, 0), mapper.resolve(now, 120.0))
        assertEquals(Duration.ofHours(11), Duration.between(mapper.windowStart(now), mapper.windowEnd(now)))
    }

    @Test
    fun `around both DST changes the window contains now and the hour hand points at now`() {
        val mappers = listOf(
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(6, 0), LocalTime.of(18, 0))),
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(2, 30), LocalTime.of(14, 30))),
            DialTimeMapper.TrailingOffset(0),
            DialTimeMapper.TrailingOffset(90),
            DialTimeMapper.FixedTimes(listOf(LocalTime.MIDNIGHT), dialHours = 24),
            DialTimeMapper.FixedTimes(listOf(LocalTime.of(2, 30)), dialHours = 24),
            DialTimeMapper.TrailingOffset(90, dialHours = 24),
        )
        for (mapper in mappers) {
            for ((month, day) in listOf(3 to 28, 10 to 24)) {
                val from = berlinAt(month, day, 0)
                for (step in 0 until 3 * 24 * 60 step 7) {
                    val now = from.plusMinutes(step.toLong())
                    val start = mapper.windowStart(now)
                    val end = mapper.windowEnd(now)
                    assertTrue("$mapper at $now: window $start..$end must contain now", !now.isBefore(start) && now.isBefore(end))
                    assertEquals("$mapper at $now", now, mapper.resolve(now, handAngle(now, mapper.dialHours)))
                    for (angle in 0 until 360 step 5) {
                        val resolved = mapper.resolve(now, angle.toDouble())
                        // A window ending inside the skipped hour ends at the jump, where the skipped readings land too.
                        assertTrue("$mapper at $now, angle $angle -> $resolved", !resolved.isBefore(start) && !resolved.isAfter(end))
                    }
                }
            }
        }
    }
}
