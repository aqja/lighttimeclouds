package com.umfluten.lighttimeclouds.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.lang.reflect.Modifier
import java.time.ZoneId

class SettingsFileTest {

    private val paris = Place("paris", "Paris", 48.857, 2.352, ZoneId.of("Europe/Paris"))
    private val places = listOf(Place.DEFAULT, paris)

    /** Every setting away from its default, so a field read from the wrong key shows. */
    private val changed = WidgetSettings(
        widgetId = 7,
        place = paris,
        displayMode = DisplayMode.TWENTY_FOUR_HOUR,
        colorMode = ColorMode.DARK,
        tapAction = TapAction.OWN_APP,
        switchStrategyType = SwitchStrategyType.TRAILING,
        switchTime1Minutes = 300,
        switchTime2Minutes = 1000,
        switchTime24hMinutes = 120,
        ringEndFadeDeg = 25,
        skyHorizon = SkyHorizon.OUTER,
        skyTopElevationDeg = 60,
        showSunInRing = false,
        showMoonInRing = false,
        showMoonPathInRing = false,
        showMinuteTicks = true,
        showSolarMarks = false,
        showSunriseSunsetMarks = false,
        showMoon = false,
        moonStyle = MoonStyle.SEPARATE_HAND,
        showMoonCompass = false,
        showNextAlarm = true,
        showWeather = true,
        weatherRingPosition = WeatherRingPosition.INSIDE,
        temperatureDisplay = TemperatureDisplay.APPARENT,
        temperaturePosition = TemperaturePosition.BETWEEN_NUMERALS,
        summaryPosition = SummaryPosition.BELOW,
        labelPosition = LabelPosition.ABOVE,
    )

    private fun fileWith(settings: String = "{}", place: String = """{"id":"a","name":"Berlin","latitude":52.5208,"longitude":13.4094,"zone":"Europe/Berlin"}""") =
        """{"format":"lighttimeclouds-settings","version":1,"places":[$place],"settings":$settings}"""

    @Test
    fun `settings and places come back as they were, for the widget reading the file`() {
        val contents = SettingsFile.decode(SettingsFile.encode(changed, places), widgetId = 9)
        assertEquals(changed.copy(widgetId = 9), contents.settings)
        assertEquals(places, contents.places)
    }

    @Test
    fun `every setting is in the file, and no widget id`() {
        val written = Json.parseToJsonElement(SettingsFile.encode(changed, places)).jsonObject.getValue("settings").jsonObject
        val expected = WidgetSettings::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
            .toSet() - "widgetId"
        assertEquals(expected, written.keys)
    }

    @Test
    fun `missing and invalid settings fall back to the defaults`() {
        val settings = SettingsFile.decode(
            fileWith("""{"displayMode":"SUNDIAL","ringEndFadeDeg":500,"skyTopElevationDeg":1,"switchTime24hMinutes":9999,"showMoon":"yes"}"""),
            widgetId = 1,
        ).settings
        val d = WidgetSettings(widgetId = 1)
        assertEquals(d.displayMode, settings.displayMode)
        assertEquals(WidgetSettings.MAX_RING_END_FADE_DEG, settings.ringEndFadeDeg)
        assertEquals(WidgetSettings.MIN_SKY_TOP_ELEVATION_DEG, settings.skyTopElevationDeg)
        assertEquals(d.switchTime24hMinutes, settings.switchTime24hMinutes)
        assertEquals(d.showMoon, settings.showMoon)
    }

    @Test
    fun `places get no more decimals than any other, and an unknown place falls back to the first`() {
        val contents = SettingsFile.decode(fileWith("""{"place":"gone"}"""), widgetId = 1)
        assertEquals(52.521, contents.places.single().latitude, 0.0)
        assertEquals(13.409, contents.places.single().longitude, 0.0)
        assertEquals("a", contents.settings.place.id)
    }

    @Test
    fun `other files are refused`() {
        val unusable = listOf(
            "not json",
            "[]",
            """{"format":"something-else","version":1,"places":[],"settings":{}}""",
            """{"format":"lighttimeclouds-settings","version":2,"places":[],"settings":{}}""",
            """{"format":"lighttimeclouds-settings","version":1,"places":[],"settings":{}}""",
            """{"format":"lighttimeclouds-settings","version":1,"settings":{}}""",
            """{"format":"lighttimeclouds-settings","version":1,"places":[{"id":"a","name":"Berlin","latitude":52.5,"longitude":13.4}]}""",
            fileWith(place = """{"id":"a","name":"Berlin","latitude":91.0,"longitude":13.4,"zone":"Europe/Berlin"}"""),
            fileWith(place = """{"id":"a","name":" ","latitude":52.5,"longitude":13.4,"zone":"Europe/Berlin"}"""),
        )
        for (text in unusable) {
            assertThrows(text, IllegalArgumentException::class.java) { SettingsFile.decode(text, widgetId = 1) }
        }
    }
}
