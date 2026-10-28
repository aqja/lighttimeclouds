package com.umfluten.lighttimeclouds.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WeatherOutlookTest {

    private val now: Instant = Instant.parse("2026-10-17T10:30:00Z")
    private val t0: Instant = Instant.parse("2026-10-16T08:00:00Z")

    /** Hourly steps from t0 to well past now; [at] overrides single hours (offset from now's hour). */
    private fun forecast(
        temperature: (Long) -> Double = { 10.0 },
        apparent: (Long) -> Double = temperature,
        at: Map<Long, Triple<Double, Double, Double>> = emptyMap(),
    ): WeatherForecast {
        val hours = (0L..40L).map { i ->
            val time = t0.plus(Duration.ofHours(i))
            val fromNow = Duration.between(Instant.parse("2026-10-17T10:00:00Z"), time).toHours()
            val (mm, snow, probability) = at[fromNow] ?: Triple(0.0, 0.0, 0.0)
            HourlyWeather(time, 50.0, mm, snow, probability, temperature(i), apparent(i))
        }
        return WeatherForecast(52.52, 13.41, now, hours)
    }

    @Test
    fun `compares the air temperature with the same time yesterday`() {
        // Index 26.5 is now, 2.5 is 24 h earlier: 4 degrees rise per day.
        val o = WeatherOutlook.of(forecast(temperature = { 5.0 + it / 6.0 }), now)
        assertEquals(4, o.changeSinceYesterday)
    }

    @Test
    fun `mentions the apparent temperature only when it is notably off`() {
        assertNull(WeatherOutlook.of(forecast(apparent = { 8.0 }), now).feelsLikeOffset)
        assertEquals(-4, WeatherOutlook.of(forecast(apparent = { 6.0 }), now).feelsLikeOffset)
        assertEquals(3, WeatherOutlook.of(forecast(apparent = { 13.0 }), now).feelsLikeOffset)
    }

    @Test
    fun `dry when nothing falls within six hours`() {
        assertEquals(PrecipitationOutlook(PrecipitationKind.DRY, 6), WeatherOutlook.of(forecast(), now).precipitation)
    }

    @Test
    fun `rain soon outranks snow later`() {
        // The step ending at now's hour + 1 h holds the precipitation of the hour containing now.
        val f = forecast(at = mapOf(2L to Triple(2.0, 0.0, 90.0), 5L to Triple(2.0, 3.0, 90.0)))
        assertEquals(PrecipitationOutlook(PrecipitationKind.RAIN, 3), WeatherOutlook.of(f, now).precipitation)
    }

    @Test
    fun `snow later when the near hours stay dry`() {
        val f = forecast(at = mapOf(5L to Triple(2.0, 3.0, 90.0)))
        assertEquals(PrecipitationOutlook(PrecipitationKind.SNOW, 6), WeatherOutlook.of(f, now).precipitation)
    }

    @Test
    fun `a mere chance needs more probability soon than later`() {
        val soon = forecast(at = mapOf(1L to Triple(0.2, 0.0, 45.0)))
        assertEquals(PrecipitationOutlook(PrecipitationKind.CHANCE, 6), WeatherOutlook.of(soon, now).precipitation)
        val likely = forecast(at = mapOf(1L to Triple(0.2, 0.0, 60.0)))
        assertEquals(PrecipitationOutlook(PrecipitationKind.CHANCE, 3), WeatherOutlook.of(likely, now).precipitation)
    }

    @Test
    fun `parts are unknown outside the forecast`() {
        val o = WeatherOutlook.of(forecast(), Instant.parse("2026-10-30T00:00:00Z"))
        assertTrue(o.isEmpty)
    }
}
