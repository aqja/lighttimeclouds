package com.umfluten.lighttimeclouds.weather

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class TimeOfDayTest {

    private val berlin = ZoneId.of("Europe/Berlin")

    private fun at(day: Int, hour: Int, minute: Int = 0) = ZonedDateTime.of(2026, 3, day, hour, minute, 0, 0, berlin).toInstant()

    private fun of(target: java.time.Instant, now: java.time.Instant) = TimeOfDay.of(target, now, berlin)

    @Test
    fun `periods by local clock time`() {
        assertEquals(DayPeriod.NIGHT, DayPeriod.ofHour(3))
        assertEquals(DayPeriod.EARLY_MORNING, DayPeriod.ofHour(5))
        assertEquals(DayPeriod.MORNING, DayPeriod.ofHour(11))
        assertEquals(DayPeriod.MIDDAY, DayPeriod.ofHour(12))
        assertEquals(DayPeriod.AFTERNOON, DayPeriod.ofHour(17))
        assertEquals(DayPeriod.EVENING, DayPeriod.ofHour(21))
        assertEquals(DayPeriod.NIGHT, DayPeriod.ofHour(22))
    }

    @Test
    fun `within two hours is soon, whatever the period`() {
        assertEquals(TimeOfDay.Soon, of(at(10, 13, 30), at(10, 11, 45)))
    }

    @Test
    fun `later in the current period`() {
        assertEquals(TimeOfDay.Later(DayPeriod.AFTERNOON), of(at(10, 17, 30), at(10, 14, 15)))
    }

    @Test
    fun `a later period of today`() {
        assertEquals(TimeOfDay.InPeriod(DayPeriod.EVENING, tomorrow = false), of(at(10, 19), at(10, 10)))
        assertEquals(TimeOfDay.InPeriod(DayPeriod.NIGHT, tomorrow = false), of(at(10, 23, 30), at(10, 10)))
    }

    @Test
    fun `the night after midnight still belongs to the evening before`() {
        assertEquals(TimeOfDay.Later(DayPeriod.NIGHT), of(at(11, 3), at(10, 22, 30)))
        assertEquals(TimeOfDay.InPeriod(DayPeriod.NIGHT, tomorrow = false), of(at(11, 1), at(10, 15)))
    }

    @Test
    fun `the next morning is tomorrow, also seen from after midnight`() {
        assertEquals(TimeOfDay.InPeriod(DayPeriod.EARLY_MORNING, tomorrow = true), of(at(11, 8), at(10, 20)))
        assertEquals(TimeOfDay.InPeriod(DayPeriod.MORNING, tomorrow = true), of(at(11, 10), at(11, 1)))
    }
}
