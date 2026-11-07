package com.umfluten.lighttimeclouds.config

import com.umfluten.lighttimeclouds.weather.WeatherCondition
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant

class HourIndexTest {

    private val start = Instant.parse("2026-11-06T00:00:00Z")

    private fun hours(vararg offsets: Long) = offsets.map {
        HourRow(start.plusSeconds(it * 3600), 5.0, 3.0, 50.0, WeatherCondition(50.0, 0.0, 0.0))
    }

    @Test
    fun `a point on the chart falls into the hour drawn there`() {
        val rows = hours(0, 1, 2, 3)
        assertEquals(0, hourIndexAt(rows, 0f))
        assertEquals(0, hourIndexAt(rows, 0.24f))
        assertEquals(1, hourIndexAt(rows, 0.26f))
        assertEquals(3, hourIndexAt(rows, 0.99f))
    }

    @Test
    fun `sliding past either end stays on the first or last hour`() {
        val rows = hours(0, 1, 2)
        assertEquals(0, hourIndexAt(rows, -0.5f))
        assertEquals(2, hourIndexAt(rows, 1f))
        assertEquals(2, hourIndexAt(rows, 1.5f))
    }

    @Test
    fun `a gap in the forecast belongs to the hour before it`() {
        val rows = hours(0, 1, 5)
        assertEquals(1, hourIndexAt(rows, 0.5f))
    }
}
