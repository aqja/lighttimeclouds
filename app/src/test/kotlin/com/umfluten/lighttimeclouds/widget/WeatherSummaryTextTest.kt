package com.umfluten.lighttimeclouds.widget

import android.content.res.Resources
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.weather.DayPeriod
import com.umfluten.lighttimeclouds.weather.HourlyWeather
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

    /** Ten degrees all day, with rain in the hours starting at the local [rainHours]. */
    private fun forecast(rainHours: Set<Int> = emptySet()): WeatherForecast {
        val start = now.minusDays(1).withMinute(0)
        val hours = (0L..48L).map { i ->
            val time = start.plusHours(i)
            val rain = time.minusHours(1).hour in rainHours && time.isAfter(now)
            HourlyWeather(time.toInstant(), 50.0, if (rain) 2.0 else 0.0, 0.0, if (rain) 90.0 else 0.0, 10.0, 10.0)
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
            "Etwa so warm wie gestern um diese Zeit. Ab heute Abend Regen wahrscheinlich.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(19, 20, 21, 22)), now),
        )
        assertEquals(
            "Etwa so warm wie gestern um diese Zeit. Der Regen hört heute gegen Mittag auf.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(10, 11, 12)), now),
        )
    }

    @Test
    fun `english sentences`() {
        assertEquals(
            "About as warm as this time yesterday. Rain likely this afternoon.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(15, 16)), now),
        )
        assertEquals(
            "About as warm as this time yesterday. Rain likely soon.",
            WeatherSummaryText.of(resources, forecast(rainHours = setOf(11)), now),
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

    private fun assertPhraseCounts() {
        listOf(
            R.array.summary_time_later,
            R.array.summary_time_at_today, R.array.summary_time_at_tomorrow,
            R.array.summary_time_from_today, R.array.summary_time_from_tomorrow,
            R.array.summary_time_until_today, R.array.summary_time_until_tomorrow,
        ).forEach { assertEquals(DayPeriod.entries.size, resources.getStringArray(it).size) }
    }
}
