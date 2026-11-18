package com.umfluten.lighttimeclouds.widget

import android.content.res.Resources
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.weather.DayPeriod
import com.umfluten.lighttimeclouds.weather.GustLevel
import com.umfluten.lighttimeclouds.weather.HourlyWeather
import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Duration
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeatherSummaryTextTest {

    private val resources: Resources get() = RuntimeEnvironment.getApplication().resources
    private val zone = Place.DEFAULT.zone
    private val now = ZonedDateTime.of(2026, 10, 17, 10, 30, 0, 0, zone)

    /** Ten degrees all day, with [mm] of rain in each hour starting at the local [rainHours]. */
    private fun forecast(rainHours: Set<Int> = emptySet(), mm: Double = 2.0): WeatherForecast {
        val start = now.minusDays(1).withMinute(0)
        val hours = (0L..72L).map { i ->
            val time = start.plusHours(i)
            val rain = time.minusHours(1).hour in rainHours && time.isAfter(now)
            HourlyWeather(time.toInstant(), 50.0, if (rain) mm else 0.0, 0.0, if (rain) 90.0 else 0.0, 10.0, 10.0)
        }
        return WeatherForecast(52.52, 13.41, now.toInstant().minus(Duration.ofHours(1)), hours)
    }

    @Test
    @Config(qualifiers = "de")
    fun `german sentences`() {
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Bis in die Nacht trocken.",
            WeatherSummaryText.of(resources, forecast(), now),
        )
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Ab heute Abend leichter Regen wahrscheinlich.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(19, 20, 21, 22)), now),
        )
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Der leichte Regen hört heute gegen Mittag auf.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(10, 11, 12)), now),
        )
    }

    @Test
    fun `english sentences`() {
        assertEquals(
            "About as warm as this time yesterday. Light rain likely this afternoon.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15, 16)), now),
        )
        assertEquals(
            "About as warm as this time yesterday. Heavy rain likely soon.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(11), mm = 12.0), now),
        )
        assertEquals(
            "About as warm as this time yesterday. The steady rain goes on for many hours.",
            WeatherSummaryText.of(resources, forecast(rainHours = (0..23).toSet(), mm = 4.0), now),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `german snow`() {
        val f = forecast().let { f ->
            f.copy(hours = f.hours.map { h ->
                val hour = h.time.atZone(zone).minusHours(1).hour
                if (h.time.isAfter(now.toInstant()) && hour in 15..16) h.copy(precipitationMm = 1.5, snowfallCm = 1.0, precipitationProbabilityPercent = 50.0) else h
            })
        }
        assertEquals(
            "Heute Nachmittag leichter Schneefall wahrscheinlich. Einige Zentimeter Neuschnee.",
            WeatherSummaryText.of(resources, f, now),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `german showers`() {
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Die leichten Schauer hören heute gegen Mittag auf.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(10, 12, 13)), now),
        )
    }

    /** [forecast] with the air temperature and cloud cover per local full hour of today and tomorrow. */
    private fun forecastWith(temperature: (ZonedDateTime) -> Double, cloud: (ZonedDateTime) -> Double = { 50.0 }) =
        forecast().let { f ->
            f.copy(hours = f.hours.map { h ->
                val t = h.time.atZone(zone)
                h.copy(temperatureC = temperature(t), apparentTemperatureC = temperature(t), cloudCoverPercent = cloud(t))
            })
        }

    @Test
    @Config(qualifiers = "de")
    fun `german frost and clearing`() {
        // Equally warm as yesterday at this hour, then frost in the evening; clear from the afternoon.
        val f = forecastWith(
            temperature = { if (it.isAfter(now) && it.hour == 19 && it.dayOfMonth == 17) -3.0 else 2.0 },
            cloud = { if (it.isAfter(now.plusHours(4))) 5.0 else 95.0 },
        )
        assertEquals(
            "Heute Abend Frost bis \u22123°. Heute Nachmittag klart es auf.",
            WeatherSummaryText.of(resources, f, now),
        )
    }

    @Test
    fun `english heat`() {
        val f = forecastWith(temperature = { if (it.isAfter(now) && it.hour == 15 && it.dayOfMonth == 17) 32.0 else 26.0 })
        assertEquals(
            "About as warm as this time yesterday. Hot this afternoon, up to 32°.",
            WeatherSummaryText.of(resources, f, now),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `german heavy rain`() {
        assertEquals(
            "Heute Nachmittag Gefahr von Starkregen. Heute Nachmittag kräftiger Regen wahrscheinlich.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15), mm = 20.0), now),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `german outlook on tomorrow in the evening`() {
        val evening = now.withHour(20)
        // Today up to 8°, tomorrow up to 12°: milder, as both are cold days.
        val f = forecastWith(temperature = { if (it.dayOfMonth == 18 && it.hour in 9..18) 12.0 else 8.0 })
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Morgen milder und trocken.",
            WeatherSummaryText.of(resources, f, evening),
        )
    }

    @Test
    @Config(qualifiers = "de")
    fun `german storm gusts`() {
        val f = forecast().let { f ->
            f.copy(hours = f.hours.map { h ->
                val hour = h.time.atZone(zone).minusHours(1).hour
                h.copy(windGustsKmh = if (h.time.isAfter(now.toInstant()) && hour in 19..20) 75.0 else 30.0)
            })
        }
        assertEquals(
            "Heute Abend Sturmböen möglich. Etwa so warm wie gestern um diese Zeit.",
            WeatherSummaryText.of(resources, f, now),
        )
    }

    /** [forecast] with gusts of [kmh] in the local [hours]. */
    private fun forecastWithGusts(hours: IntRange, kmh: Double) = forecast().let { f ->
        f.copy(hours = f.hours.map { h ->
            val hour = h.time.atZone(zone).minusHours(1).hour
            h.copy(windGustsKmh = if (h.time.isAfter(now.toInstant()) && hour in hours) kmh else 30.0)
        })
    }

    @Test
    @Config(qualifiers = "fr")
    fun `french sentences`() {
        assertEquals(
            "À peu près aussi chaud qu’hier à la même heure. Pluie faible probable cet après-midi.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15, 16)), now),
        )
        assertEquals(
            "À peu près aussi chaud qu’hier à la même heure. Les faibles averses cessent vers midi.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(10, 12, 13)), now),
        )
        assertEquals(
            "À peu près aussi chaud qu’hier à la même heure. Faibles averses probables cet après-midi.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15, 17)), now),
        )
        assertEquals(
            "Rafales de coup de vent possibles ce soir. À peu près aussi chaud qu’hier à la même heure.",
            WeatherSummaryText.of(resources, forecastWithGusts(19..20, 75.0), now),
        )
    }

    @Test
    @Config(qualifiers = "es")
    fun `spanish sentences`() {
        assertEquals(
            "Temperatura parecida a la de ayer a esta hora. Probable lluvia débil a partir de última hora de la tarde.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(19, 20, 21, 22)), now),
        )
        assertEquals(
            "Temperatura parecida a la de ayer a esta hora. Probables chubascos débiles esta tarde.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15, 17)), now),
        )
        assertEquals(
            "Temperatura parecida a la de ayer a esta hora. La lluvia persistente continúa durante muchas horas.",
            WeatherSummaryText.of(resources, forecast(rainHours = (0..23).toSet(), mm = 4.0), now),
        )
    }

    @Test
    fun `no text without a forecast`() {
        assertEquals(null, WeatherSummaryText.of(resources, null, now))
    }

    @Test
    @Config(qualifiers = "de")
    fun `every part of the day has a german phrase`() = assertPhraseCounts()

    @Test
    fun `every part of the day has an english phrase`() = assertPhraseCounts()

    @Test
    @Config(qualifiers = "es")
    fun `every part of the day has a spanish phrase`() = assertPhraseCounts()

    @Test
    @Config(qualifiers = "fr")
    fun `every part of the day has a french phrase`() = assertPhraseCounts()

    private fun assertPhraseCounts() {
        listOf(
            R.array.summary_time_later,
            R.array.summary_time_at_today, R.array.summary_time_at_tomorrow,
            R.array.summary_time_from_today, R.array.summary_time_from_tomorrow,
            R.array.summary_time_until_today, R.array.summary_time_until_tomorrow,
        ).forEach { assertEquals(DayPeriod.entries.size, resources.getStringArray(it).size) }
        listOf(
            R.array.summary_rain, R.array.summary_snow, R.array.summary_rain_definite, R.array.summary_snow_definite,
            R.array.summary_rain_showers, R.array.summary_rain_showers_definite, R.array.summary_rain_steady, R.array.summary_rain_steady_definite,
            R.array.summary_snow_showers, R.array.summary_snow_showers_definite, R.array.summary_snow_steady, R.array.summary_snow_steady_definite,
        ).forEach { assertEquals(PrecipitationIntensity.entries.size, resources.getStringArray(it).size) }
        listOf(R.array.summary_tomorrow_temperature, R.array.summary_tomorrow_temperature_cold).forEach { assertEquals(5, resources.getStringArray(it).size) }
        listOf(R.array.summary_tomorrow_rain, R.array.summary_tomorrow_snow).forEach { assertEquals(4, resources.getStringArray(it).size) }
        assertEquals(GustLevel.entries.size, resources.getStringArray(R.array.summary_gusts).size)
    }
}
