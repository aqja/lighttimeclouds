package com.umfluten.lighttimeclouds.weather

import com.umfluten.lighttimeclouds.weather.PrecipitationKind.RAIN
import com.umfluten.lighttimeclouds.weather.PrecipitationKind.SNOW
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset

class WeatherOutlookTest {

    // In the morning period; hour i of the outlook runs from 10+i to 11+i o'clock.
    private val now: Instant = Instant.parse("2026-10-17T10:30:00Z")
    private val t0: Instant = Instant.parse("2026-10-16T08:00:00Z")
    private val zone = ZoneOffset.UTC

    private data class Wet(val mm: Double, val probability: Double, val snowCm: Double = 0.0)

    private val rain = Wet(2.0, 90.0)

    /** Hourly steps from t0 to well past now; [wet] sets the precipitation of single hours, counted from now's hour. */
    private fun forecast(
        temperature: (Long) -> Double = { 10.0 },
        apparent: (Long) -> Double = temperature,
        wet: Map<Int, Wet> = emptyMap(),
    ): WeatherForecast {
        val hours = (0L..40L).map { i ->
            val time = t0.plus(Duration.ofHours(i))
            // A step holds the precipitation of the hour before it.
            val hour = Duration.between(Instant.parse("2026-10-17T10:00:00Z"), time).toHours().toInt() - 1
            val w = wet[hour] ?: Wet(0.0, 0.0)
            HourlyWeather(time, 50.0, w.mm, w.snowCm, w.probability, temperature(i), apparent(i))
        }
        return WeatherForecast(52.52, 13.41, now, hours)
    }

    private fun precipitation(vararg wet: Pair<Int, Wet>) = WeatherOutlook.of(forecast(wet = wet.toMap()), now, zone).precipitation

    private fun today(period: DayPeriod) = TimeOfDay.InPeriod(period, tomorrow = false)

    @Test
    fun `compares the air temperature with the same time yesterday`() {
        // Index 26.5 is now, 2.5 is 24 h earlier: 4 degrees rise per day.
        val o = WeatherOutlook.of(forecast(temperature = { 5.0 + it / 6.0 }), now, zone)
        assertEquals(4, o.changeSinceYesterday)
    }

    @Test
    fun `mentions the apparent temperature only when it is notably off`() {
        assertNull(WeatherOutlook.of(forecast(apparent = { 8.0 }), now, zone).feelsLikeOffset)
        assertEquals(-4, WeatherOutlook.of(forecast(apparent = { 6.0 }), now, zone).feelsLikeOffset)
        assertEquals(3, WeatherOutlook.of(forecast(apparent = { 13.0 }), now, zone).feelsLikeOffset)
    }

    @Test
    fun `dry up to the end of the horizon`() {
        assertEquals(PrecipitationOutlook.Dry(today(DayPeriod.NIGHT)), precipitation())
    }

    @Test
    fun `rain falling now ends when it falls last`() {
        // Wet until 13:00, so the last rain falls at midday.
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, today(DayPeriod.MIDDAY)), precipitation(0 to rain, 1 to rain, 2 to rain))
    }

    @Test
    fun `a single dry hour does not end the rain`() {
        assertEquals(
            PrecipitationOutlook.Ongoing(RAIN, today(DayPeriod.AFTERNOON)),
            precipitation(0 to rain, 1 to rain, 3 to rain, 4 to rain),
        )
    }

    @Test
    fun `rain that lasts beyond the horizon has no end`() {
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, null), precipitation(*(0..11).map { it to rain }.toTypedArray()))
    }

    @Test
    fun `rain to come in one part of the day`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, likely = true, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(6 to rain),
        )
    }

    @Test
    fun `rain to come that goes on into the evening`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, likely = true, start = today(DayPeriod.AFTERNOON), continues = true),
            precipitation(6 to rain, 7 to rain, 8 to rain, 9 to rain),
        )
    }

    @Test
    fun `snow that is only possible is not called likely`() {
        assertEquals(
            PrecipitationOutlook.Coming(SNOW, likely = false, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(4 to Wet(0.2, 45.0, snowCm = 0.3)),
        )
    }

    @Test
    fun `a small amount counts as likely when it is probable`() {
        val o = precipitation(0 to Wet(0.3, 80.0))
        assertTrue(o is PrecipitationOutlook.Ongoing)
    }

    @Test
    fun `a mere chance right now is rain to come soon`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, likely = false, start = TimeOfDay.Soon, continues = false),
            precipitation(0 to Wet(0.0, 60.0)),
        )
    }

    @Test
    fun `a mere chance needs more probability in the near hours`() {
        assertEquals(PrecipitationOutlook.Dry(today(DayPeriod.NIGHT)), precipitation(1 to Wet(0.0, 45.0)))
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, likely = false, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(4 to Wet(0.0, 45.0)),
        )
    }

    @Test
    fun `snow when most of the spell is snow`() {
        val snow = Wet(2.0, 90.0, snowCm = 2.0)
        assertEquals(PrecipitationOutlook.Ongoing(SNOW, today(DayPeriod.MIDDAY)), precipitation(0 to rain, 1 to snow, 2 to snow))
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, today(DayPeriod.MIDDAY)), precipitation(0 to rain, 1 to rain, 2 to snow))
    }

    @Test
    fun `parts are unknown outside the forecast`() {
        val o = WeatherOutlook.of(forecast(), Instant.parse("2026-10-30T00:00:00Z"), zone)
        assertTrue(o.isEmpty)
    }

    @Test
    fun `remarks leave out the unremarkable when there is enough else to say`() {
        val dry = PrecipitationOutlook.Dry(today(DayPeriod.NIGHT))
        val coming = PrecipitationOutlook.Coming(RAIN, likely = true, start = today(DayPeriod.EVENING), continues = true)
        // Nothing notable: both unremarkable remarks.
        assertEquals(
            listOf(Remark.TemperatureChange(1), Remark.Precipitation(dry)),
            WeatherOutlook(1, null, dry).remarks(),
        )
        // One notable remark, topped up with an unremarkable one.
        assertEquals(
            listOf(Remark.TemperatureChange(1), Remark.Precipitation(coming)),
            WeatherOutlook(1, null, coming).remarks(),
        )
        // Two notable remarks, in reading order.
        assertEquals(
            listOf(Remark.FeelsLike(-4), Remark.Precipitation(coming)),
            WeatherOutlook(1, -4, coming).remarks(),
        )
        assertEquals(
            listOf(Remark.TemperatureChange(-6), Remark.FeelsLike(-4)),
            WeatherOutlook(-6, -4, dry).remarks(),
        )
    }
}
