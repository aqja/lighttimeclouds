package com.umfluten.lighttimeclouds.weather

import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity.HEAVY
import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity.LIGHT
import com.umfluten.lighttimeclouds.weather.PrecipitationIntensity.SLIGHT
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
        cloud: (Int) -> Double = { 50.0 },
    ): WeatherForecast {
        val hours = (0L..64L).map { i ->
            val time = t0.plus(Duration.ofHours(i))
            // A step holds the precipitation of the hour before it.
            val hour = Duration.between(Instant.parse("2026-10-17T10:00:00Z"), time).toHours().toInt() - 1
            val w = wet[hour] ?: Wet(0.0, 0.0)
            HourlyWeather(time, cloud(hour + 1), w.mm, w.snowCm, w.probability, temperature(i), apparent(i))
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
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, LIGHT, today(DayPeriod.MIDDAY)), precipitation(0 to rain, 1 to rain, 2 to rain))
    }

    @Test
    fun `a single dry hour does not end the rain`() {
        assertEquals(
            PrecipitationOutlook.Ongoing(RAIN, LIGHT, today(DayPeriod.AFTERNOON), SpellCharacter.SHOWERS),
            precipitation(0 to rain, 1 to rain, 3 to rain, 4 to rain),
        )
    }

    @Test
    fun `rain that lasts beyond the horizon has no end`() {
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, LIGHT, null, SpellCharacter.STEADY), precipitation(*(0..11).map { it to rain }.toTypedArray()))
    }

    @Test
    fun `rain to come in one part of the day`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, LIGHT, likely = true, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(6 to rain),
        )
    }

    @Test
    fun `rain to come that goes on into the evening`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, LIGHT, likely = true, start = today(DayPeriod.AFTERNOON), continues = true),
            precipitation(6 to rain, 7 to rain, 8 to rain, 9 to rain),
        )
    }

    @Test
    fun `the strongest hour sets the intensity`() {
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, HEAVY, likely = true, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(4 to Wet(0.3, 50.0), 5 to Wet(12.0, 90.0), 6 to rain),
        )
    }

    @Test
    fun `snow that is only possible is not called likely`() {
        assertEquals(
            PrecipitationOutlook.Coming(SNOW, SLIGHT, likely = false, start = today(DayPeriod.AFTERNOON), continues = false, snowfallCm = 0.3),
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
            PrecipitationOutlook.Coming(RAIN, SLIGHT, likely = false, start = TimeOfDay.Soon, continues = false),
            precipitation(0 to Wet(0.0, 60.0)),
        )
    }

    @Test
    fun `a mere chance needs more probability in the near hours`() {
        assertEquals(PrecipitationOutlook.Dry(today(DayPeriod.NIGHT)), precipitation(1 to Wet(0.0, 45.0)))
        assertEquals(
            PrecipitationOutlook.Coming(RAIN, SLIGHT, likely = false, start = today(DayPeriod.AFTERNOON), continues = false),
            precipitation(4 to Wet(0.0, 45.0)),
        )
    }

    @Test
    fun `snow when most of the spell is snow`() {
        val snow = Wet(2.0, 90.0, snowCm = 2.0)
        assertEquals(PrecipitationOutlook.Ongoing(SNOW, LIGHT, today(DayPeriod.MIDDAY), snowfallCm = 4.0), precipitation(0 to rain, 1 to snow, 2 to snow))
        assertEquals(PrecipitationOutlook.Ongoing(RAIN, LIGHT, today(DayPeriod.MIDDAY), snowfallCm = 2.0), precipitation(0 to rain, 1 to rain, 2 to snow))
    }

    @Test
    fun `six hours without a break are steady rain`() {
        val o = precipitation(*(3..8).map { it to rain }.toTypedArray())
        assertEquals(SpellCharacter.STEADY, (o as PrecipitationOutlook.Spell).character)
        val shorter = precipitation(*(3..7).map { it to rain }.toTypedArray())
        assertEquals(SpellCharacter.NORMAL, (shorter as PrecipitationOutlook.Spell).character)
    }

    @Test
    fun `another spell later makes showers`() {
        val o = precipitation(2 to rain, 6 to rain)
        assertEquals(SpellCharacter.SHOWERS, (o as PrecipitationOutlook.Spell).character)
    }

    @Test
    fun `fresh snow is said with likely snow only`() {
        fun snow(cm: Double, likely: Boolean = true) =
            PrecipitationOutlook.Coming(SNOW, LIGHT, likely, today(DayPeriod.EVENING), continues = true, snowfallCm = cm)
        assertEquals(
            listOf(Remark.TemperatureChange(-3), Remark.Precipitation(snow(4.0)), Remark.Snow(FreshSnow.SOME)),
            WeatherOutlook(-3, null, snow(4.0)).remarks(),
        )
        assertEquals(
            listOf(Remark.TemperatureChange(-3), Remark.Precipitation(snow(4.0, likely = false))),
            WeatherOutlook(-3, null, snow(4.0, likely = false)).remarks(),
        )
        assertEquals(
            listOf(Remark.TemperatureChange(0), Remark.Precipitation(snow(0.3))),
            WeatherOutlook(0, null, snow(0.3)).remarks(),
        )
        assertEquals(Remark.Snow(FreshSnow.LOTS), WeatherOutlook(0, null, snow(12.0)).remarks().last())
    }

    /** The outlook with the temperature [byHour] counted from now's hour, which starts at index 26. */
    private fun withTemperature(byHour: (Long) -> Double) = WeatherOutlook.of(forecast(temperature = { byHour(it - 26) }), now, zone)

    /** The outlook with the cloud cover at the full hours counted from now's hour; an hour has the mean of its start and end. */
    private fun withClouds(cloud: (Int) -> Double, vararg wet: Pair<Int, Wet>) =
        WeatherOutlook.of(forecast(cloud = cloud, wet = wet.toMap()), now, zone)

    @Test
    fun `clearing up after the clouds`() {
        assertEquals(CloudOutlook.Clearing(today(DayPeriod.AFTERNOON)), withClouds({ if (it < 5) 95.0 else 10.0 }).clouds)
    }

    @Test
    fun `clouding over after a clear morning`() {
        assertEquals(CloudOutlook.CloudingOver(today(DayPeriod.EVENING)), withClouds({ if (it < 9) 10.0 else 95.0 }).clouds)
    }

    @Test
    fun `a sky that stays the same`() {
        assertEquals(CloudOutlook.Overcast(today(DayPeriod.NIGHT)), withClouds({ 95.0 }).clouds)
        assertEquals(CloudOutlook.Clear(today(DayPeriod.NIGHT)), withClouds({ 5.0 }).clouds)
        assertNull(withClouds({ 50.0 }).clouds)
    }

    @Test
    fun `overcast is not worth saying next to rain`() {
        val o = withClouds({ 95.0 }, 3 to rain)
        assertTrue(o.remarks().none { it is Remark.Clouds })
        assertTrue(withClouds({ 95.0 }).remarks().any { it is Remark.Clouds })
    }

    @Test
    fun `heat at the warmest hour`() {
        assertEquals(TemperatureCourse.Heat(32, today(DayPeriod.AFTERNOON)), withTemperature { if (it == 5L) 32.0 else 25.0 }.temperatureCourse)
    }

    @Test
    fun `frost to come at the coldest hour`() {
        assertEquals(TemperatureCourse.Frost(-3, today(DayPeriod.EVENING)), withTemperature { if (it == 8L) -3.0 else 2.0 }.temperatureCourse)
        // Light frost that rounds to zero is not worth it.
        assertNull(withTemperature { if (it == 8L) -0.4 else 2.0 }.temperatureCourse)
    }

    @Test
    fun `lasting frost`() {
        assertEquals(TemperatureCourse.LastingFrost(-2, today(DayPeriod.NIGHT)), withTemperature { -2.0 }.temperatureCourse)
    }

    @Test
    fun `a large rise or fall`() {
        assertEquals(TemperatureCourse.Warming(12, today(DayPeriod.NIGHT)), withTemperature { 5.0 + it }.temperatureCourse)
        assertEquals(TemperatureCourse.Cooling(12, today(DayPeriod.NIGHT)), withTemperature { 15.0 - it }.temperatureCourse)
        assertNull(withTemperature { 10.0 + it / 4.0 }.temperatureCourse)
    }

    @Test
    fun `a small feels-like gap gives way to more notable remarks`() {
        val coming = PrecipitationOutlook.Coming(RAIN, LIGHT, likely = true, start = today(DayPeriod.EVENING), continues = true)
        val clearing = CloudOutlook.Clearing(today(DayPeriod.AFTERNOON))
        val frost = TemperatureCourse.Frost(-2, today(DayPeriod.NIGHT))
        assertEquals(
            listOf(Remark.Temperature(frost), Remark.Precipitation(coming), Remark.Clouds(clearing)),
            WeatherOutlook(1, -4, coming, clearing, frost).remarks(),
        )
        assertEquals(
            listOf(Remark.FeelsLike(-9), Remark.Temperature(frost), Remark.Precipitation(coming)),
            WeatherOutlook(1, -9, coming, clearing, frost).remarks(),
        )
    }

    @Test
    fun `heavy rain at its strongest level`() {
        val o = WeatherOutlook.of(forecast(wet = mapOf(4 to Wet(18.0, 90.0), 6 to Wet(30.0, 90.0))), now, zone)
        assertEquals(listOf(WeatherWarning.HeavyRain(HeavyRainWarning.SEVERE, today(DayPeriod.AFTERNOON))), o.warnings)
        assertEquals(Remark.Warning(o.warnings.single()), o.remarks().first())
    }

    @Test
    fun `no heavy rain warning for snow`() {
        assertTrue(WeatherOutlook.of(forecast(wet = mapOf(4 to Wet(18.0, 90.0, snowCm = 15.0))), now, zone).warnings.isEmpty())
    }

    @Test
    fun `rain at freezing is glaze ice`() {
        val o = WeatherOutlook.of(forecast(temperature = { -1.0 }, wet = mapOf(3 to rain)), now, zone)
        assertEquals(listOf(WeatherWarning.FreezingRain(today(DayPeriod.MIDDAY))), o.warnings)
    }

    private val evening: Instant = Instant.parse("2026-10-17T19:30:00Z")

    @Test
    fun `tomorrow in the evening only`() {
        // Index 25 is today 9:00, index 49 tomorrow 9:00.
        val f = forecast(temperature = { if (it in 49L..58L) 16.0 else 10.0 })
        assertEquals(TomorrowOutlook(6, 10, DaytimePrecipitation.DRY, RAIN), WeatherOutlook.of(f, evening, zone).tomorrow)
        assertNull(WeatherOutlook.of(f, now, zone).tomorrow)
    }

    @Test
    fun `how often it rains tomorrow`() {
        fun tomorrow(vararg wet: Pair<Int, Wet>) = WeatherOutlook.of(forecast(wet = wet.toMap()), evening, zone).tomorrow!!.precipitation
        // Tomorrow 12:00 is hour 26 from today 10:00.
        assertEquals(DaytimePrecipitation.POSSIBLE, tomorrow(26 to Wet(0.0, 60.0)))
        assertEquals(DaytimePrecipitation.AT_TIMES, tomorrow(26 to rain, 27 to rain))
        assertEquals(DaytimePrecipitation.OFTEN, tomorrow(*(24..27).map { it to rain }.toTypedArray()))
    }

    @Test
    fun `parts are unknown outside the forecast`() {
        val o = WeatherOutlook.of(forecast(), Instant.parse("2026-10-30T00:00:00Z"), zone)
        assertTrue(o.isEmpty)
    }

    @Test
    fun `remarks leave out the unremarkable when there is enough else to say`() {
        val dry = PrecipitationOutlook.Dry(today(DayPeriod.NIGHT))
        val coming = PrecipitationOutlook.Coming(RAIN, LIGHT, likely = true, start = today(DayPeriod.EVENING), continues = true)
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
