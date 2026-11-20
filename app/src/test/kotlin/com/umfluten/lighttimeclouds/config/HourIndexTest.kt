package com.umfluten.lighttimeclouds.config

import com.umfluten.lighttimeclouds.weather.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HourIndexTest {

    private val start = Instant.parse("2026-11-06T00:00:00Z")

    private fun at(hours: Double): Instant = start.plusSeconds((hours * 3600).toLong())

    private fun hours(vararg offsets: Long) = offsets.map {
        HourRow(start.plusSeconds(it * 3600), 5.0, 3.0, 50.0, WeatherCondition(50.0, 0.0, 0.0))
    }

    @Test
    fun `a tapped time falls into the hour drawn there`() {
        val rows = hours(0, 1, 2, 3)
        assertEquals(0, hourIndexAt(rows, at(0.0)))
        assertEquals(0, hourIndexAt(rows, at(0.99)))
        assertEquals(1, hourIndexAt(rows, at(1.0)))
        assertEquals(3, hourIndexAt(rows, at(3.9)))
    }

    @Test
    fun `a time past either end stays on the first or last hour`() {
        val rows = hours(0, 1, 2)
        assertEquals(0, hourIndexAt(rows, at(-2.0)))
        assertEquals(2, hourIndexAt(rows, at(7.0)))
    }

    @Test
    fun `a gap in the forecast belongs to the hour before it`() {
        val rows = hours(0, 1, 5)
        assertEquals(1, hourIndexAt(rows, at(3.0)))
    }
}
