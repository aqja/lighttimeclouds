package com.umfluten.lighttimeclouds.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import java.time.ZoneId

/**
 * The settings of one widget and all saved places as a JSON file, the way to move to another device
 * or to copy settings between widgets. Widget ids are not in it, since they only mean something on
 * the device that gave them. Settings missing from a file, or no longer valid, fall back to the
 * [WidgetSettings] defaults, so a file outlives changes to the options; its places must be sound.
 */
object SettingsFile {
    const val FILE_NAME = "lighttimeclouds-settings.json"
    private const val FORMAT = "lighttimeclouds-settings"
    private const val VERSION = 1
    private val LATITUDES = -90.0..90.0
    private val LONGITUDES = -180.0..180.0
    private const val MINUTES_PER_DAY = 24 * 60
    private val PRETTY = Json { prettyPrint = true }

    /** What a file holds: its [places], and its settings for the widget they were decoded for. */
    class Contents(val places: List<Place>, val settings: WidgetSettings)

    fun encode(settings: WidgetSettings, places: List<Place>): String {
        val root = buildJsonObject {
            put("format", FORMAT)
            put("version", VERSION)
            putJsonArray("places") {
                for (place in places) add(encode(place))
            }
            putJsonObject("settings") {
                put("place", settings.place.id)
                put("displayMode", settings.displayMode.name)
                put("colorMode", settings.colorMode.name)
                put("tapAction", settings.tapAction.name)
                put("switchStrategyType", settings.switchStrategyType.name)
                put("switchTime1Minutes", settings.switchTime1Minutes)
                put("switchTime2Minutes", settings.switchTime2Minutes)
                put("switchTime24hMinutes", settings.switchTime24hMinutes)
                put("ringEndFadeDeg", settings.ringEndFadeDeg)
                put("ringStyle", settings.ringStyle.name)
                put("skyHorizon", settings.skyHorizon.name)
                put("skyTopElevationDeg", settings.skyTopElevationDeg)
                put("showSunInRing", settings.showSunInRing)
                put("showMoonInRing", settings.showMoonInRing)
                put("showMoonPathInRing", settings.showMoonPathInRing)
                put("showMinuteTicks", settings.showMinuteTicks)
                put("showSolarMarks", settings.showSolarMarks)
                put("showSunriseSunsetMarks", settings.showSunriseSunsetMarks)
                put("showMoon", settings.showMoon)
                put("moonStyle", settings.moonStyle.name)
                put("showMoonCompass", settings.showMoonCompass)
                put("showNextAlarm", settings.showNextAlarm)
                put("showWeather", settings.showWeather)
                put("weatherRingPosition", settings.weatherRingPosition.name)
                put("temperatureDisplay", settings.temperatureDisplay.name)
                put("temperaturePosition", settings.temperaturePosition.name)
                put("summaryPosition", settings.summaryPosition.name)
                put("labelPosition", settings.labelPosition.name)
            }
        }
        return PRETTY.encodeToString(JsonElement.serializer(), root)
    }

    private fun encode(place: Place): JsonObject = buildJsonObject {
        put("id", place.id)
        put("name", place.name)
        put("latitude", place.latitude)
        put("longitude", place.longitude)
        put("zone", place.zone.id)
    }

    /** Reads [text] for the widget [widgetId]; throws [IllegalArgumentException] if it is no settings file this app can use. */
    fun decode(text: String, widgetId: Int): Contents {
        val root = Json.parseToJsonElement(text).jsonObject
        require(root.string("format") == FORMAT) { "Not a settings file" }
        require(root.int("version") == VERSION) { "Unknown version" }
        val places = root["places"]?.jsonArray.orEmpty().map { decodePlace(it.jsonObject) }
        require(places.isNotEmpty()) { "No places" }
        val s = requireNotNull(root["settings"]) { "No settings" }.jsonObject
        val d = WidgetSettings(widgetId = widgetId)
        fun minutes(name: String, fallback: Int) = s.int(name)?.takeIf { it in 0 until MINUTES_PER_DAY } ?: fallback
        val settings = WidgetSettings(
            widgetId = widgetId,
            place = places.firstOrNull { it.id == s.string("place") } ?: places.first(),
            displayMode = s.enumOr("displayMode", d.displayMode),
            colorMode = s.enumOr("colorMode", d.colorMode),
            tapAction = s.enumOr("tapAction", d.tapAction),
            switchStrategyType = s.enumOr("switchStrategyType", d.switchStrategyType),
            switchTime1Minutes = minutes("switchTime1Minutes", d.switchTime1Minutes),
            switchTime2Minutes = minutes("switchTime2Minutes", d.switchTime2Minutes),
            switchTime24hMinutes = minutes("switchTime24hMinutes", d.switchTime24hMinutes),
            ringEndFadeDeg = (s.int("ringEndFadeDeg") ?: d.ringEndFadeDeg).coerceIn(0, WidgetSettings.MAX_RING_END_FADE_DEG),
            ringStyle = s.enumOr("ringStyle", d.ringStyle),
            skyHorizon = s.enumOr("skyHorizon", d.skyHorizon),
            skyTopElevationDeg = (s.int("skyTopElevationDeg") ?: d.skyTopElevationDeg)
                .coerceIn(WidgetSettings.MIN_SKY_TOP_ELEVATION_DEG, WidgetSettings.MAX_SKY_TOP_ELEVATION_DEG),
            showSunInRing = s.boolean("showSunInRing") ?: d.showSunInRing,
            showMoonInRing = s.boolean("showMoonInRing") ?: d.showMoonInRing,
            showMoonPathInRing = s.boolean("showMoonPathInRing") ?: d.showMoonPathInRing,
            showMinuteTicks = s.boolean("showMinuteTicks") ?: d.showMinuteTicks,
            showSolarMarks = s.boolean("showSolarMarks") ?: d.showSolarMarks,
            showSunriseSunsetMarks = s.boolean("showSunriseSunsetMarks") ?: d.showSunriseSunsetMarks,
            showMoon = s.boolean("showMoon") ?: d.showMoon,
            moonStyle = s.enumOr("moonStyle", d.moonStyle),
            showMoonCompass = s.boolean("showMoonCompass") ?: d.showMoonCompass,
            showNextAlarm = s.boolean("showNextAlarm") ?: d.showNextAlarm,
            showWeather = s.boolean("showWeather") ?: d.showWeather,
            weatherRingPosition = s.enumOr("weatherRingPosition", d.weatherRingPosition),
            temperatureDisplay = s.enumOr("temperatureDisplay", d.temperatureDisplay),
            temperaturePosition = s.enumOr("temperaturePosition", d.temperaturePosition),
            summaryPosition = s.enumOr("summaryPosition", d.summaryPosition),
            labelPosition = s.enumOr("labelPosition", d.labelPosition),
        )
        return Contents(places, settings)
    }

    /** An invalid zone falls back to the device zone, as for a stored place. */
    private fun decodePlace(place: JsonObject): Place {
        val id = requireNotNull(place.string("id")?.takeIf { it.isNotBlank() }) { "Place without id" }
        val name = requireNotNull(place.string("name")?.takeIf { it.isNotBlank() }) { "Place without name" }
        val latitude = requireNotNull(place.double("latitude")?.takeIf { it in LATITUDES }) { "Invalid latitude" }
        val longitude = requireNotNull(place.double("longitude")?.takeIf { it in LONGITUDES }) { "Invalid longitude" }
        val zone = place.string("zone")?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: ZoneId.systemDefault()
        return Place(id, name, roundCoordinate(latitude), roundCoordinate(longitude), zone)
    }

    private fun JsonObject.string(name: String) = this[name]?.jsonPrimitive?.takeIf { it.isString }?.content
    private fun JsonObject.int(name: String) = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.double(name: String) = this[name]?.jsonPrimitive?.doubleOrNull
    private fun JsonObject.boolean(name: String) = this[name]?.jsonPrimitive?.booleanOrNull

    private inline fun <reified E : Enum<E>> JsonObject.enumOr(name: String, fallback: E): E =
        string(name)?.let { value -> enumValues<E>().firstOrNull { it.name == value } } ?: fallback
}
