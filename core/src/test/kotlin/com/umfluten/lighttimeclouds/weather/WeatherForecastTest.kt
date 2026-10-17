package com.umfluten.lighttimeclouds.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class WeatherForecastTest {

    private val t0: Instant = Instant.parse("2026-10-17T10:00:00Z")

    private fun hour(
        offset: Long,
        cloud: Double,
        precipitation: Double = 0.0,
        snow: Double = 0.0,
        probability: Double? = null,
        temperature: Double? = null,
        apparent: Double? = null,
    ) = HourlyWeather(t0.plus(Duration.ofHours(offset)), cloud, precipitation, snow, probability, temperature, apparent)

    private fun forecast(vararg hours: HourlyWeather, fetchedAt: Instant = t0) =
        WeatherForecast(52.52, 13.41, fetchedAt, hours.toList())

    @Test
    fun `condition uses the precipitation of the step ending at the next full hour and the mean cloud cover`() {
        val f = forecast(hour(0, 20.0, 0.0), hour(1, 60.0, 1.5), hour(2, 100.0, 4.0))

        val c = f.conditionAt(t0.plus(Duration.ofMinutes(30)))!!
        assertEquals(40.0, c.cloudCoverPercent, 1e-9)
        assertEquals(1.5, c.precipitationMm, 1e-9)

        // Exactly on a full hour, the hour that starts there applies.
        val atStep = f.conditionAt(t0.plus(Duration.ofHours(1)))!!
        assertEquals(80.0, atStep.cloudCoverPercent, 1e-9)
        assertEquals(4.0, atStep.precipitationMm, 1e-9)
    }

    @Test
    fun `condition is null outside the forecast and across gaps`() {
        val f = forecast(hour(0, 50.0), hour(1, 50.0), hour(5, 50.0))

        assertNull(f.conditionAt(t0.plus(Duration.ofHours(5))))
        assertNull(f.conditionAt(t0.minus(Duration.ofMinutes(61))))
        assertNull(f.conditionAt(t0.plus(Duration.ofHours(2))))
        // The first step has no predecessor: its precipitation still covers the hour before it.
        assertEquals(50.0, f.conditionAt(t0.minus(Duration.ofMinutes(30)))!!.cloudCoverPercent, 1e-9)
        assertNull(forecast().conditionAt(t0))
    }

    @Test
    fun `precipitation level grows logarithmically with the amount`() {
        fun level(mm: Double) = WeatherCondition(100.0, mm, 0.0).precipitationLevel

        assertEquals(0f, level(0.05), 1e-6f)
        assertEquals(0f, level(0.1), 1e-6f)
        assertEquals(0.5f, level(1.0), 1e-6f)
        assertEquals(1f, level(10.0), 1e-6f)
        assertEquals(1f, level(40.0), 1e-6f)
    }

    @Test
    fun `precipitation opacity follows the probability, with a floor for forecast amounts`() {
        fun c(mm: Double, probability: Double?) = WeatherCondition(100.0, mm, 0.0, probability)

        // Only a chance: light blue, as opaque as it is likely.
        assertTrue(c(0.0, 40.0).hasPrecipitation)
        assertEquals(0.4f, c(0.0, 40.0).precipitationAlpha, 1e-6f)
        assertFalse(c(0.0, 0.0).hasPrecipitation)
        assertFalse(c(0.0, null).hasPrecipitation)

        assertEquals(0.9f, c(2.0, 90.0).precipitationAlpha, 1e-6f)
        assertEquals(WeatherCondition.MIN_AMOUNT_ALPHA, c(2.0, 5.0).precipitationAlpha, 1e-6f)
        assertEquals(1f, c(2.0, null).precipitationAlpha, 1e-6f)
    }

    @Test
    fun `snow detection from snowfall share or, with only a chance, from frost`() {
        fun c(mm: Double, snowCm: Double = 0.0, probability: Double? = null, temperature: Double? = null) =
            WeatherCondition(100.0, mm, snowCm, probability, temperature)

        assertFalse(c(2.0).isSnow)
        assertTrue(c(1.0, snowCm = 0.7).isSnow) // 0.7 cm snow ≈ 1 mm water
        assertFalse(c(4.0, snowCm = 0.7).isSnow) // mostly rain
        assertFalse(c(0.0).isSnow)
        assertTrue(c(0.0, probability = 30.0, temperature = -2.0).isSnow)
        assertFalse(c(0.0, probability = 30.0, temperature = 3.0).isSnow)
        assertFalse(c(0.0, probability = 30.0).isSnow)
    }

    @Test
    fun `condition carries the probability of its hour and the mean temperature`() {
        val f = forecast(hour(0, 50.0, temperature = 2.0), hour(1, 50.0, 0.0, probability = 30.0, temperature = -4.0))
        val c = f.conditionAt(t0.plus(Duration.ofMinutes(10)))!!

        assertEquals(30.0, c.precipitationProbabilityPercent!!, 1e-9)
        assertEquals(-1.0, c.temperatureC!!, 1e-9)
    }

    @Test
    fun `temperature is interpolated between hours and null where data is missing`() {
        val f = forecast(
            hour(0, 0.0, temperature = 10.0, apparent = 7.0),
            hour(1, 0.0, temperature = 14.0, apparent = 13.0),
            hour(2, 0.0),
        )

        assertEquals(10.0, f.temperatureAt(t0, TemperatureKind.AIR)!!, 1e-9)
        assertEquals(12.0, f.temperatureAt(t0.plus(Duration.ofMinutes(30)), TemperatureKind.AIR)!!, 1e-9)
        assertEquals(10.0, f.temperatureAt(t0.plus(Duration.ofMinutes(30)), TemperatureKind.APPARENT)!!, 1e-9)
        assertEquals(14.0, f.temperatureAt(t0.plus(Duration.ofHours(1)), TemperatureKind.AIR)!!, 1e-9)
        assertNull(f.temperatureAt(t0.plus(Duration.ofMinutes(90)), TemperatureKind.AIR))
        assertNull(f.temperatureAt(t0.minus(Duration.ofMinutes(1)), TemperatureKind.AIR))
        assertNull(f.temperatureAt(t0.plus(Duration.ofHours(3)), TemperatureKind.AIR))
    }

    @Test
    fun `mean temperature averages the interpolated course over the span`() {
        val f = forecast(
            hour(0, 0.0, temperature = 10.0),
            hour(1, 0.0, temperature = 20.0, apparent = 18.0),
            hour(2, 0.0, temperature = 14.0),
        )
        fun at(minutes: Long) = t0.plus(Duration.ofMinutes(minutes))

        // One hour: the mean of both ends.
        assertEquals(15.0, f.meanTemperature(at(0), at(60), TemperatureKind.AIR)!!, 1e-9)
        // Two hours: the middle value counts twice, (10 + 2·20 + 14) / 4.
        assertEquals(16.0, f.meanTemperature(at(0), at(120), TemperatureKind.AIR)!!, 1e-9)
        // Off the full hours, e.g. a trailing window: 0:30 → 1:30 runs 15 → 20 → 17.
        assertEquals(18.0, f.meanTemperature(at(30), at(90), TemperatureKind.AIR)!!, 1e-9)
        // Missing data anywhere in the span gives no value.
        assertNull(f.meanTemperature(at(0), at(120), TemperatureKind.APPARENT))
        assertNull(f.meanTemperature(at(60), at(180), TemperatureKind.AIR))
    }

    @Test
    fun `freshness fades after three hours and hides after twelve`() {
        val f = forecast(fetchedAt = t0)

        assertEquals(1f, f.freshnessAlpha(t0.plus(Duration.ofHours(3))), 1e-6f)
        val mid = f.freshnessAlpha(t0.plus(Duration.ofMinutes(450)))
        assertTrue("was $mid", mid in 0.6f..0.8f)
        assertEquals(0.4f, f.freshnessAlpha(t0.plus(Duration.ofHours(12))), 1e-6f)
        assertEquals(0f, f.freshnessAlpha(t0.plus(Duration.ofHours(12)).plusSeconds(1)), 1e-6f)
    }
}
