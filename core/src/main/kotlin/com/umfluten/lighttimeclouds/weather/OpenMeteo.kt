package com.umfluten.lighttimeclouds.weather

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.Instant
import java.util.Locale

/**
 * Request URL and response parsing for the Open-Meteo forecast API (https://open-meteo.com),
 * plus the cache format. Open-Meteo is free without API key for non-commercial use and returns
 * model data for any coordinates worldwide; its data is licensed CC BY 4.0, see [ATTRIBUTION].
 */
object OpenMeteo {

    const val ATTRIBUTION = "Wetterdaten von Open-Meteo.com (CC BY 4.0)"

    /**
     * Hourly cloud cover, precipitation (amount, snow, probability) and air/apparent temperature
     * from 24 h back to 48 h ahead, timestamps as
     * Unix seconds. That covers the 12 h window (which can start up to 12 h before now) and the
     * whole local day in 24 h mode even for a cache that is half a day old.
     */
    fun forecastUrl(latitude: Double, longitude: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f".format(Locale.ROOT, latitude, longitude) +
            "&hourly=cloud_cover,precipitation,snowfall,precipitation_probability,temperature_2m,apparent_temperature" +
            "&timeformat=unixtime&past_hours=24&forecast_hours=48"

    /**
     * Parses a forecast response for the requested [latitude]/[longitude]. Hours missing cloud
     * cover or precipitation are dropped; probability and temperatures may be null. Throws
     * [IllegalArgumentException] for error responses or unexpected JSON.
     */
    fun parse(body: String, latitude: Double, longitude: Double, fetchedAt: Instant): WeatherForecast {
        val root = runCatching { Json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw IllegalArgumentException("Open-Meteo response is not a JSON object", it) }
        if (root["error"]?.jsonPrimitive?.content == "true") {
            throw IllegalArgumentException("Open-Meteo error: ${root["reason"]?.jsonPrimitive?.content}")
        }
        val hourly = root["hourly"] as? JsonObject ?: throw IllegalArgumentException("Open-Meteo response has no hourly data")
        fun column(name: String): JsonArray =
            hourly[name] as? JsonArray ?: throw IllegalArgumentException("Open-Meteo response lacks hourly.$name")

        val times = column("time")
        val cloudCover = column("cloud_cover")
        val precipitation = column("precipitation")
        val snowfall = column("snowfall")
        val probability = hourly["precipitation_probability"] as? JsonArray
        val temperature = hourly["temperature_2m"] as? JsonArray
        val apparent = hourly["apparent_temperature"] as? JsonArray
        fun JsonArray?.at(i: Int): Double? = this?.getOrNull(i)?.jsonPrimitive?.doubleOrNull
        val hours = times.indices.mapNotNull { i ->
            HourlyWeather(
                time = Instant.ofEpochSecond(times[i].jsonPrimitive.longOrNull ?: return@mapNotNull null),
                cloudCoverPercent = cloudCover.at(i) ?: return@mapNotNull null,
                precipitationMm = precipitation.at(i) ?: return@mapNotNull null,
                snowfallCm = snowfall.at(i) ?: return@mapNotNull null,
                precipitationProbabilityPercent = probability.at(i),
                temperatureC = temperature.at(i),
                apparentTemperatureC = apparent.at(i),
            )
        }.sortedBy { it.time }
        return WeatherForecast(latitude, longitude, fetchedAt, hours)
    }

    /** Serializes [forecast] for the local cache; read back with [decodeCache]. */
    fun encodeCache(forecast: WeatherForecast): String = buildJsonObject {
        put("latitude", forecast.latitude)
        put("longitude", forecast.longitude)
        put("fetched_at", forecast.fetchedAt.epochSecond)
        putJsonArray("hours") {
            for (hour in forecast.hours) {
                add(
                    buildJsonObject {
                        put("time", hour.time.epochSecond)
                        put("cloud_cover", hour.cloudCoverPercent)
                        put("precipitation", hour.precipitationMm)
                        put("snowfall", hour.snowfallCm)
                        hour.precipitationProbabilityPercent?.let { put("precipitation_probability", it) }
                        hour.temperatureC?.let { put("temperature", it) }
                        hour.apparentTemperatureC?.let { put("apparent_temperature", it) }
                    },
                )
            }
        }
    }.toString()

    /** Inverse of [encodeCache]; null if [text] is not a valid cache entry (e.g. truncated). Optional values may be absent. */
    fun decodeCache(text: String): WeatherForecast? = runCatching {
        val root = Json.parseToJsonElement(text).jsonObject
        fun JsonObject.double(name: String) = getValue(name).jsonPrimitive.doubleOrNull!!
        fun JsonObject.long(name: String) = getValue(name).jsonPrimitive.longOrNull!!
        fun JsonObject.optionalDouble(name: String) = get(name)?.jsonPrimitive?.doubleOrNull
        WeatherForecast(
            latitude = root.double("latitude"),
            longitude = root.double("longitude"),
            fetchedAt = Instant.ofEpochSecond(root.long("fetched_at")),
            hours = root.getValue("hours").jsonArray.map {
                val hour = it.jsonObject
                HourlyWeather(
                    time = Instant.ofEpochSecond(hour.long("time")),
                    cloudCoverPercent = hour.double("cloud_cover"),
                    precipitationMm = hour.double("precipitation"),
                    snowfallCm = hour.double("snowfall"),
                    precipitationProbabilityPercent = hour.optionalDouble("precipitation_probability"),
                    temperatureC = hour.optionalDouble("temperature"),
                    apparentTemperatureC = hour.optionalDouble("apparent_temperature"),
                )
            },
        )
    }.getOrNull()
}
