package com.umfluten.lighttimeclouds.data

import android.content.Context
import com.umfluten.lighttimeclouds.weather.OpenMeteo
import com.umfluten.lighttimeclouds.weather.WeatherForecast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.Duration
import java.time.Instant

/**
 * Forecasts per place, cached as one small JSON file per [Place.id] so all widgets of a place share
 * one download. Widgets only ever read the cache; [refresh] is the only network access.
 */
class WeatherRepository(context: Context) {
    private val dir = File(context.filesDir, "weather")

    private fun cacheFile(placeId: String) = File(dir, "$placeId.json")

    /** Cached forecast for [place]; null if there is none yet or it was fetched before the place's coordinates changed. */
    suspend fun cached(place: Place): WeatherForecast? = withContext(Dispatchers.IO) {
        val file = cacheFile(place.id)
        if (!file.exists()) return@withContext null
        runCatching { file.readText() }.getOrNull()
            ?.let(OpenMeteo::decodeCache)
            ?.takeIf { it.isFor(place.latitude, place.longitude) }
    }

    /**
     * Downloads a new forecast for [place] unless the cached one is younger than [maxAge]; returns
     * whether the cache changed. Throws [IOException] on network errors and
     * [IllegalArgumentException] on an unusable response.
     */
    suspend fun refresh(place: Place, maxAge: Duration): Boolean = withContext(Dispatchers.IO) {
        val now = Instant.now()
        val current = cached(place)
        if (current != null && Duration.between(current.fetchedAt, now) < maxAge) return@withContext false

        val body = download(OpenMeteo.forecastUrl(place.latitude, place.longitude))
        val forecast = OpenMeteo.parse(body, place.latitude, place.longitude, now)
        dir.mkdirs()
        // Write-then-rename, so a widget redraw never reads a half-written file. A unique temporary
        // file keeps two concurrent refreshes (immediate and periodic job) from clobbering each other.
        val tmp = File.createTempFile("forecast_${place.id}_", TMP_SUFFIX, dir)
        try {
            tmp.writeText(OpenMeteo.encodeCache(forecast))
            if (!tmp.renameTo(cacheFile(place.id))) throw IOException("Could not store forecast for place ${place.id}")
        } finally {
            tmp.delete()
        }
        true
    }

    /**
     * Removes cached forecasts of places not in [placeIds], e.g. deleted places or ones without
     * weather widgets, plus temporary files left over from an interrupted write.
     */
    suspend fun deleteAllExcept(placeIds: Set<String>) = withContext(Dispatchers.IO) {
        val staleBefore = System.currentTimeMillis() - STALE_TMP_MS
        dir.listFiles()?.forEach { file ->
            val obsolete = when {
                file.name.endsWith(TMP_SUFFIX) -> file.lastModified() < staleBefore
                else -> file.nameWithoutExtension !in placeIds
            }
            if (obsolete) file.delete()
        }
    }

    private fun download(url: String): String {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("User-Agent", "LightTimeClouds (Android widget)")
            val code = connection.responseCode
            // Open-Meteo reports bad requests as HTTP 400 with a JSON reason, which parse() turns into an exception.
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                ?: throw IOException("HTTP $code from Open-Meteo")
            val body = stream.use { readLimited(it) }
            if (code >= 500) throw IOException("HTTP $code from Open-Meteo")
            return body
        } finally {
            connection.disconnect()
        }
    }

    /** Reads at most [MAX_RESPONSE_BYTES] (a real response is about 5 KB), so a broken server cannot exhaust memory. */
    private fun readLimited(stream: InputStream): String {
        val out = ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        while (true) {
            val n = stream.read(buffer)
            if (n < 0) break
            out.write(buffer, 0, n)
            if (out.size() > MAX_RESPONSE_BYTES) throw IOException("Open-Meteo response larger than $MAX_RESPONSE_BYTES bytes")
        }
        return out.toString(Charsets.UTF_8.name())
    }

    private companion object {
        const val TIMEOUT_MS = 20_000
        const val MAX_RESPONSE_BYTES = 256 * 1024
        const val TMP_SUFFIX = ".tmp"
        const val STALE_TMP_MS = 60 * 60 * 1000L
    }
}
