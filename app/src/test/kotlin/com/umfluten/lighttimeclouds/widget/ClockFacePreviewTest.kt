package com.umfluten.lighttimeclouds.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import androidx.core.graphics.createBitmap
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.ColorMode
import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.MoonStyle
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.SkyHorizon
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.TemperatureDisplay
import com.umfluten.lighttimeclouds.data.TemperaturePosition
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.SkyProjection
import com.umfluten.lighttimeclouds.weather.HourlyWeather
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.sin

/**
 * Renders the clock face for a few fixed weather scenarios into `app/build/previews/` so layout
 * and colors can be checked without a device. Real Android graphics via Robolectric's native mode.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36])
class ClockFacePreviewTest {

    private val now: ZonedDateTime = ZonedDateTime.of(2026, 10, 17, 14, 20, 0, 0, Place.DEFAULT.zone)
    private val outDir = File("build/previews").apply { mkdirs() }

    /** Showers in the morning, a chance of rain, then a heavy downpour, clearing up overnight. */
    private fun mixedForecast(): WeatherForecast {
        val base = now.truncatedTo(ChronoUnit.HOURS)
        val hours = (-24..48).map { k ->
            val (cloud, mm, probability) = when (k) {
                in -6..-3 -> Triple(100.0, 0.4, 90.0)
                in -2..0 -> Triple(60.0, 0.0, 10.0)
                in 1..2 -> Triple(80.0, 0.0, 40.0)
                in 3..4 -> Triple(100.0, 2.5, 80.0)
                5 -> Triple(100.0, 12.0, 100.0)
                in 6..7 -> Triple(30.0, 0.0, 0.0)
                in 8..10 -> Triple(0.0, 0.0, 0.0)
                else -> Triple(50.0, 0.0, 0.0)
            }
            val temperature = 10.0 + 6.0 * sin((base.hour + k - 9) / 24.0 * 2 * Math.PI)
            HourlyWeather(base.plusHours(k.toLong()).toInstant(), cloud, mm, 0.0, probability, temperature, temperature - 3.5)
        }
        return WeatherForecast(Place.DEFAULT.latitude, Place.DEFAULT.longitude, now.toInstant(), hours)
    }

    /** Frost with snowfall of increasing intensity. */
    private fun snowForecast(): WeatherForecast {
        val base = now.truncatedTo(ChronoUnit.HOURS)
        val hours = (-24..48).map { k ->
            val mm = when (k) {
                in 1..2 -> 0.3
                in 3..4 -> 1.5
                in 5..6 -> 6.0
                else -> 0.0
            }
            val probability = if (k in 0..8) 70.0 else 0.0
            HourlyWeather(base.plusHours(k.toLong()).toInstant(), 95.0, mm, mm * 0.7, probability, -3.0 - k * 0.2, -8.0 - k * 0.2)
        }
        return WeatherForecast(Place.DEFAULT.latitude, Place.DEFAULT.longitude, now.toInstant(), hours)
    }

    /** Rain through every intensity class and heavy rain warning level, one step per hour. */
    private fun intensityForecast(): WeatherForecast {
        val base = now.truncatedTo(ChronoUnit.HOURS)
        val amounts = listOf(0.0, 0.2, 1.0, 5.0, 12.0, 18.0, 30.0, 45.0)
        val hours = (-24..48).map { k ->
            val mm = amounts.getOrElse(k) { 0.0 }
            val probability = if (k == 0) 50.0 else if (mm > 0.0) 90.0 else 0.0
            HourlyWeather(base.plusHours(k.toLong()).toInstant(), 70.0, mm, 0.0, probability, 15.0, 14.0)
        }
        return WeatherForecast(Place.DEFAULT.latitude, Place.DEFAULT.longitude, now.toInstant(), hours)
    }

    private fun render(
        name: String,
        settings: WidgetSettings,
        weather: WeatherForecast?,
        darkBackground: Boolean,
        at: ZonedDateTime = now,
        alarm: ZonedDateTime? = null,
    ) {
        val context = RuntimeEnvironment.getApplication()
        val clock = ClockFaceRenderer.render(context, 600, settings, weather, at, alarm?.toInstant())
        val sheet = createBitmap(clock.width, clock.height)
        Canvas(sheet).apply {
            drawColor(if (darkBackground) Color.rgb(32, 33, 36) else Color.rgb(236, 236, 232))
            drawBitmap(clock, 0f, 0f, null)
        }
        File(outDir, "$name.png").outputStream().use { sheet.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue("$name is blank", (0 until clock.width step 7).any { Color.alpha(clock.getPixel(it, clock.height / 2)) > 0 })
    }

    private val weatherSettings = WidgetSettings(widgetId = 1, showWeather = true, temperatureDisplay = TemperatureDisplay.AIR)

    @Test
    fun skyPreviews() {
        val plain = WidgetSettings(widgetId = 1, colorMode = ColorMode.DARK, showMoon = false)
        val fullDay = plain.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR)
        render("sky_24h", fullDay, null, darkBackground = true)
        render("sky_24h_old_gradient", fullDay.copy(ringStyle = RingStyle.GRADIENT), null, darkBackground = true)
        // Late evening with the seam trailing the hour hand: tomorrow's sunrise already shows ahead of it.
        val lateEvening = ZonedDateTime.of(2026, 10, 17, 22, 0, 0, 0, Place.DEFAULT.zone)
        render("sky_24h_trailing", fullDay.copy(switchStrategyType = SwitchStrategyType.TRAILING_OFFSET), null, darkBackground = true, at = lateEvening)
        // Evening in the 18:00-06:00 window: sunset around 18:30 with the twilight right after.
        val evening = ZonedDateTime.of(2026, 10, 17, 19, 10, 0, 0, Place.DEFAULT.zone)
        render("sky_12h_evening", plain, null, darkBackground = true, at = evening)
        render("sky_12h_evening_light", plain.copy(colorMode = ColorMode.LIGHT), null, darkBackground = false, at = evening)
        // Morning in the 06:00-18:00 window: blue hour, dawn and sunrise around 07:46.
        val morning = ZonedDateTime.of(2026, 10, 17, 8, 30, 0, 0, Place.DEFAULT.zone)
        render("sky_12h_morning", plain, null, darkBackground = true, at = morning)
        render("sky_12h_morning_horizon_outer", plain.copy(skyHorizon = SkyHorizon.OUTER), null, darkBackground = true, at = morning)
        val outside = weatherSettings.copy(colorMode = ColorMode.DARK, weatherRingPosition = WeatherRingPosition.OUTSIDE)
        render("weather_outside_12h_morning", outside, mixedForecast(), darkBackground = true, at = morning)
        render("weather_outside_12h_light", outside.copy(colorMode = ColorMode.LIGHT), mixedForecast(), darkBackground = false)
        val scale = weatherSettings.copy(colorMode = ColorMode.DARK, temperaturePosition = TemperaturePosition.OUTSIDE)
        render("temperature_scale_inside_weather", scale, mixedForecast(), darkBackground = true)
        render("temperature_scale_outside_weather", scale.copy(weatherRingPosition = WeatherRingPosition.OUTSIDE), mixedForecast(), darkBackground = true)
        render("temperature_scale_light_snow", scale.copy(colorMode = ColorMode.LIGHT), snowForecast(), darkBackground = false)
        render("temperature_scale_24h", scale.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR), mixedForecast(), darkBackground = true)
        // Trailing window with its seam just before a label (14:40, offset 30 min: seam at "2:10").
        render(
            "temperature_trailing_seam",
            weatherSettings.copy(colorMode = ColorMode.DARK, switchStrategyType = SwitchStrategyType.TRAILING_OFFSET),
            mixedForecast(),
            darkBackground = true,
            at = now.withMinute(40),
        )
        // Widest labels: two-digit frost all around.
        val frost = mixedForecast().let { f -> f.copy(hours = f.hours.map { it.copy(temperatureC = it.temperatureC!! - 25.0) }) }
        render("temperature_scale_frost", scale.copy(weatherRingPosition = WeatherRingPosition.OUTSIDE), frost, darkBackground = true)
        render(
            "weather_outside_24h_horizon_outer",
            outside.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR, skyHorizon = SkyHorizon.OUTER),
            mixedForecast(),
            darkBackground = true,
        )
    }

    @Test
    fun skyBodyPreviews() {
        val plain = WidgetSettings(widgetId = 1, colorMode = ColorMode.DARK, showMoon = false)
        val fullDay = plain.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR)
        // Afternoon, sun about 25° high, waxing moon low in the south-east.
        render("ring_bodies_24h", fullDay, null, darkBackground = true)
        render("ring_bodies_24h_top_80", fullDay.copy(skyTopElevationDeg = 80), null, darkBackground = true)
        render("ring_bodies_24h_top_15", fullDay.copy(skyTopElevationDeg = 15), null, darkBackground = true)
        render("ring_bodies_24h_horizon_outer", fullDay.copy(skyHorizon = SkyHorizon.OUTER), null, darkBackground = true)
        render("ring_bodies_24h_gradient", fullDay.copy(ringStyle = RingStyle.GRADIENT), null, darkBackground = true)
        // Midsummer noon: the sun stands higher than the far edge.
        val midsummer = ZonedDateTime.of(2026, 6, 21, 13, 15, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_24h_midsummer", fullDay, null, darkBackground = true, at = midsummer)
        // Just after sunset: no sun in the ring.
        val evening = ZonedDateTime.of(2026, 10, 17, 19, 10, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_12h_evening", plain, null, darkBackground = true, at = evening)
        // Sunrise: the sun half above the horizon edge.
        val sunrise = ZonedDateTime.of(2026, 10, 17, 7, 47, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_12h_sunrise", plain, null, darkBackground = true, at = sunrise)
        // New moon night: both below the horizon, so neither in the ring.
        val newMoonNight = ZonedDateTime.of(2026, 11, 9, 2, 0, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_12h_new_moon_night", plain, null, darkBackground = true, at = newMoonNight)
        // Full moon night with the moon high.
        val fullMoonNight = ZonedDateTime.of(2026, 10, 26, 23, 30, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_24h_full_moon_night", fullDay, null, darkBackground = true, at = fullMoonNight)
        val outer = plain.copy(skyHorizon = SkyHorizon.OUTER)
        val waningNight = ZonedDateTime.of(2026, 11, 1, 1, 0, 0, 0, Place.DEFAULT.zone)
        render("ring_bodies_12h_horizon_outer_night", outer, null, darkBackground = true, at = waningNight)
        render("ring_bodies_24h_horizon_outer_night", outer.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR), null, darkBackground = true, at = waningNight)
        render("ring_bodies_12h_horizon_outer_day", outer, null, darkBackground = true, at = waningNight.withHour(11))
        render("ring_bodies_12h_weather_light", weatherSettings.copy(colorMode = ColorMode.LIGHT), mixedForecast(), darkBackground = false)
    }

    /**
     * The sun disc (found by its color) lies as far from the horizon edge as the sun is high, on the
     * sky colors' scale up to the far edge, whichever edge the horizon is on: day/night ring from
     * 0.82 to 1 of the dial radius, which is 0.96 of half the size.
     */
    @Test
    fun sunInRingFollowsTheHorizonEdge() {
        val sunOnly = WidgetSettings(
            widgetId = 1, displayMode = DisplayMode.TWENTY_FOUR_HOUR, showMoon = false, showMoonInRing = false,
            showSolarMarks = false, showSunriseSunsetMarks = false,
        )
        val altitude = SunMoonCalculator.sunAltitudeDegrees(now.toInstant(), Place.DEFAULT.latitude, Place.DEFAULT.longitude)
        val fraction = SkyProjection.fraction(altitude, 45.0).toFloat()
        val expected = mapOf(SkyHorizon.INNER to 0.82f + 0.18f * fraction, SkyHorizon.OUTER to 1f - 0.18f * fraction)
        // Only pixels toward the current time on the 24h dial: elsewhere the horizon glow can be as yellow.
        val nowAngle = Math.toRadians((now.hour + now.minute / 60.0) * 15.0 - 90.0)
        for ((horizon, radius) in expected) {
            val size = 600
            val clock = ClockFaceRenderer.render(RuntimeEnvironment.getApplication(), size, sunOnly.copy(skyHorizon = horizon), null, now)
            var sumX = 0.0
            var sumY = 0.0
            var count = 0
            for (y in 0 until size) for (x in 0 until size) {
                val c = clock.getPixel(x, y)
                val offAngle = abs(Math.IEEEremainder(atan2(y - size / 2.0, x - size / 2.0) - nowAngle, 2 * Math.PI))
                if (offAngle < Math.toRadians(8.0) && Color.red(c) > 240 && Color.green(c) in 215..250 && Color.blue(c) < 90) {
                    sumX += x
                    sumY += y
                    count++
                }
            }
            assertTrue("no sun disc with horizon $horizon", count > 50)
            val distance = hypot(sumX / count - size / 2.0, sumY / count - size / 2.0) / (size / 2.0 * 0.96)
            assertEquals("sun distance with horizon $horizon", radius.toDouble(), distance, 0.01)
        }
    }

    @Test
    fun moonPreviews() {
        val onHand = WidgetSettings(widgetId = 1, colorMode = ColorMode.DARK, moonStyle = MoonStyle.ON_HOUR_HAND)
        val separate = onHand.copy(moonStyle = MoonStyle.SEPARATE_HAND)
        // Two days after full moon, risen at 17:39: the two styles point about 14h apart.
        val waningGibbous = ZonedDateTime.of(2026, 10, 28, 20, 0, 0, 0, Place.DEFAULT.zone)
        render("moon_on_hour_hand_12h", onHand, null, darkBackground = true, at = waningGibbous)
        render("moon_separate_hand_12h", separate, null, darkBackground = true, at = waningGibbous)
        render("moon_on_hour_hand_24h", onHand.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR), null, darkBackground = true, at = waningGibbous)
        render("moon_on_hour_hand_light", onHand.copy(colorMode = ColorMode.LIGHT), null, darkBackground = false, at = waningGibbous)
        // Thin crescent below the horizon: the frame keeps it visible on the hand.
        val newMoon = ZonedDateTime.of(2026, 11, 11, 20, 0, 0, 0, Place.DEFAULT.zone)
        render("moon_on_hour_hand_new_moon", onHand, null, darkBackground = true, at = newMoon)
        render("moon_separate_hand_new_moon", separate, null, darkBackground = true, at = newMoon)
        // Low-running moon near the equinox: rises south of east, sets south of west.
        val lowMoon = ZonedDateTime.of(2026, 9, 23, 20, 0, 0, 0, Place.DEFAULT.zone)
        render("moon_on_hour_hand_24h_low", onHand.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR), null, darkBackground = true, at = lowMoon)
        render("moon_separate_hand_24h", separate.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR), null, darkBackground = true, at = waningGibbous)
        render("moon_on_hour_hand_no_compass", onHand.copy(showMoonCompass = false), null, darkBackground = true, at = waningGibbous)
    }

    @Test
    fun alarmPreviews() {
        val plain = WidgetSettings(widgetId = 1, colorMode = ColorMode.DARK, showNextAlarm = true)
        val evening = ZonedDateTime.of(2026, 10, 17, 19, 10, 0, 0, Place.DEFAULT.zone)
        // 12h, window 18:00-06:00: an alarm at 05:45 the next morning is shown.
        render("alarm_12h_evening", plain, null, darkBackground = true, at = evening, alarm = evening.plusDays(1).withHour(5).withMinute(45))
        // 24h: an alarm later today is shown.
        render(
            "alarm_24h",
            plain.copy(displayMode = DisplayMode.TWENTY_FOUR_HOUR, colorMode = ColorMode.LIGHT),
            null,
            darkBackground = false,
            alarm = now.withHour(16).withMinute(0),
        )
        // 12h, window 06:00-18:00: an alarm at 06:30 tomorrow is outside and must not appear.
        render("alarm_12h_outside", plain, null, darkBackground = true, alarm = now.plusDays(1).withHour(6).withMinute(30))
        // With weather and temperatures between the numerals.
        render("alarm_12h_weather", weatherSettings.copy(colorMode = ColorMode.DARK, showNextAlarm = true), mixedForecast(), darkBackground = true, alarm = now.withHour(17).withMinute(15))
    }

    @Test
    fun previews() {
        render("12h_dark_temperature", weatherSettings.copy(colorMode = ColorMode.DARK), mixedForecast(), darkBackground = true)
        render("12h_light_temperature", weatherSettings.copy(colorMode = ColorMode.LIGHT), mixedForecast(), darkBackground = false)
        render(
            "12h_dark_apparent_minuteticks",
            weatherSettings.copy(colorMode = ColorMode.DARK, temperatureDisplay = TemperatureDisplay.APPARENT, showMinuteTicks = true),
            mixedForecast(),
            darkBackground = true,
        )
        render(
            "24h_dark_temperature",
            weatherSettings.copy(colorMode = ColorMode.DARK, displayMode = DisplayMode.TWENTY_FOUR_HOUR),
            mixedForecast(),
            darkBackground = true,
        )
        render("12h_dark_snow", weatherSettings.copy(colorMode = ColorMode.DARK), snowForecast(), darkBackground = true)
        render("24h_dark_intensities", weatherSettings.copy(colorMode = ColorMode.DARK, displayMode = DisplayMode.TWENTY_FOUR_HOUR), intensityForecast(), darkBackground = true)
        render("24h_light_intensities", weatherSettings.copy(colorMode = ColorMode.LIGHT, displayMode = DisplayMode.TWENTY_FOUR_HOUR), intensityForecast(), darkBackground = false)
        render(
            "12h_dark_weather_only",
            weatherSettings.copy(colorMode = ColorMode.DARK, temperatureDisplay = TemperatureDisplay.NONE),
            mixedForecast(),
            darkBackground = true,
        )
        render("12h_dark_no_weather", WidgetSettings(widgetId = 1, colorMode = ColorMode.DARK), null, darkBackground = true)
        render(
            "12h_dark_stale",
            weatherSettings.copy(colorMode = ColorMode.DARK),
            mixedForecast().copy(fetchedAt = now.toInstant().minus(Duration.ofHours(9))),
            darkBackground = true,
        )
    }
}
