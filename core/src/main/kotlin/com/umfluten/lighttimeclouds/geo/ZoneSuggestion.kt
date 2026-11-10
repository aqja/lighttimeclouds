package com.umfluten.lighttimeclouds.geo

import java.time.ZoneId
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Suggests a time zone for coordinates: the zone whose principal city lies nearest, from the
 * `zone.tab` of the IANA time zone database. It knows no borders, so near one it can pick the
 * neighbor (usually with the same offset); it is only a suggestion to check. `zone.tab` rather
 * than `zone1970.tab`, because it keeps a zone per country (Amsterdam instead of Brussels for the
 * Netherlands), which is what people expect to read.
 */
object ZoneSuggestion {
    internal const val RESOURCE = "/com/umfluten/lighttimeclouds/geo/zone.tab"

    internal data class Entry(val zone: String, val latitude: Double, val longitude: Double)

    internal val entries: List<Entry> by lazy {
        val text = ZoneSuggestion::class.java.getResourceAsStream(RESOURCE)?.use { it.readBytes().decodeToString() }
            ?: error("Missing resource $RESOURCE")
        text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val columns = line.split('\t')
                if (columns.size < 3) return@mapNotNull null
                val (lat, lon) = parseIso6709(columns[1]) ?: return@mapNotNull null
                Entry(columns[2], lat, lon)
            }
            .toList()
    }

    /** The nearest zone among [available] (by default those this device knows); null if none is. */
    fun nearest(latitude: Double, longitude: Double, available: Set<String> = ZoneId.getAvailableZoneIds()): ZoneId? =
        entries.asSequence()
            .filter { it.zone in available }
            .minByOrNull { distance(latitude, longitude, it.latitude, it.longitude) }
            ?.let { ZoneId.of(it.zone) }

    /** `±DDMM±DDDMM` or `±DDMMSS±DDDMMSS` to decimal degrees; null if malformed. */
    internal fun parseIso6709(text: String): Pair<Double, Double>? {
        val match = ISO_6709.matchEntire(text) ?: return null
        val (latSign, latDigits, lonSign, lonDigits) = match.destructured
        val lat = sexagesimal(latDigits, degreeDigits = 2) ?: return null
        val lon = sexagesimal(lonDigits, degreeDigits = 3) ?: return null
        return (if (latSign == "-") -lat else lat) to (if (lonSign == "-") -lon else lon)
    }

    private fun sexagesimal(digits: String, degreeDigits: Int): Double? {
        val rest = digits.substring(degreeDigits)
        if (rest.length != 2 && rest.length != 4) return null
        val degrees = digits.substring(0, degreeDigits).toInt()
        val minutes = rest.substring(0, 2).toInt()
        val seconds = if (rest.length == 4) rest.substring(2).toInt() else 0
        return degrees + minutes / 60.0 + seconds / 3600.0
    }

    /** Central angle between two points (haversine); only compared, so no Earth radius. */
    private fun distance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val h = sin((p2 - p1) / 2).pow(2) + cos(p1) * cos(p2) * sin(Math.toRadians(lon2 - lon1) / 2).pow(2)
        return 2 * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    private val ISO_6709 = Regex("""([+-])(\d{4}|\d{6})([+-])(\d{5}|\d{7})""")
}
