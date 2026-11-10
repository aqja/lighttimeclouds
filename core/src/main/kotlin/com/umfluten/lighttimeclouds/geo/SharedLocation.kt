package com.umfluten.lighttimeclouds.geo

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/** A point another app handed over, e.g. a map app's "Share" or "Open with"; [name] only if it came with one. */
data class SharedLocation(val latitude: Double, val longitude: Double, val name: String? = null) {

    companion object {
        /** Shared texts longer than this are not searched; a location link is far shorter. */
        private const val MAX_TEXT_LENGTH = 10_000

        /**
         * Finds the first location in [text]: a `geo:` URI, an Organic Maps short link, a map link
         * with the coordinates in it (OsmAnd, OpenStreetMap, Google Maps), or else a bare
         * "latitude, longitude" pair. Null if there are none; addresses alone are not looked up,
         * and short links that only redirect (such as maps.app.goo.gl) are not followed.
         */
        fun parse(text: String): SharedLocation? {
            if (text.length > MAX_TEXT_LENGTH) return null
            // A whole geo: URI may carry a label with spaces, as apps often leave it unencoded.
            val trimmed = text.trim()
            if (trimmed.startsWith("geo:", ignoreCase = true)) return parseGeoUri(trimmed.substring(4))
            GEO_URI.find(text)?.let { match -> return parseGeoUri(match.groupValues[1]) }
            GE0_LINK.find(text)?.let { match ->
                Ge0.decode(match.groupValues[1], match.groupValues[2])?.let { return it }
            }
            URL.findAll(text).forEach { match -> parseMapLink(match.value)?.let { return it } }
            return BARE_PAIR.find(text)?.let { pointOf(it.groupValues[1], it.groupValues[2]) }
        }

        /**
         * `geo:lat,lon[,alt][;params][?query]` after RFC 5870, plus Android's convention
         * `geo:0,0?q=lat,lon(Label)`: a query with coordinates wins over the path, whose 0,0 then
         * only stands for "none". A query without coordinates is a search, which needs no answer here.
         */
        private fun parseGeoUri(rest: String): SharedLocation? {
            val path = rest.substringBefore('?')
            val query = rest.substringAfter('?', "")
            val fromQuery = queryParams(query)["q"]?.let { q ->
                QUERY_POINT.matchEntire(q.trim())?.let { m ->
                    pointOf(m.groupValues[1], m.groupValues[2])?.copy(name = m.groupValues[3].trim().ifEmpty { null })
                }
            }
            if (fromQuery != null) return fromQuery
            val coordinates = path.substringBefore(';').split(',')
            if (coordinates.size < 2) return null
            val point = pointOf(coordinates[0], coordinates[1]) ?: return null
            return point.takeUnless { it.latitude == 0.0 && it.longitude == 0.0 }
        }

        /** Coordinates in a map link's query (`pin=`, `mlat=`/`mlon=`, `lat=`/`lon=`, `q=`, `ll=`), its path (`@lat,lon`) or its fragment (`map=z/lat/lon`). */
        private fun parseMapLink(url: String): SharedLocation? {
            val params = queryParams(url.substringAfter('?', "").substringBefore('#'))
            listOf("pin", "q", "query", "ll", "center").forEach { key ->
                params[key]?.let { value -> PAIR.matchEntire(value.trim())?.let { m -> pointOf(m.groupValues[1], m.groupValues[2])?.let { return it } } }
            }
            listOf("mlat" to "mlon", "lat" to "lon", "lat" to "lng").forEach { (latKey, lonKey) ->
                val lat = params[latKey]
                val lon = params[lonKey]
                if (lat != null && lon != null) pointOf(lat, lon)?.let { return it }
            }
            AT_PAIR.find(url)?.let { m -> pointOf(m.groupValues[1], m.groupValues[2])?.let { return it } }
            FRAGMENT_MAP.find(url)?.let { m -> pointOf(m.groupValues[1], m.groupValues[2])?.let { return it } }
            return null
        }

        private fun queryParams(query: String): Map<String, String> =
            query.split('&', ';').mapNotNull { part ->
                val key = part.substringBefore('=', "")
                if (key.isEmpty()) null else key.lowercase() to decode(part.substringAfter('='))
            }.toMap()

        private fun pointOf(latitude: String, longitude: String): SharedLocation? {
            val lat = latitude.trim().removePrefix("+").toDoubleOrNull() ?: return null
            val lon = longitude.trim().removePrefix("+").toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            return SharedLocation(lat, lon)
        }

        internal fun decode(text: String): String =
            runCatching { URLDecoder.decode(text, StandardCharsets.UTF_8.name()) }.getOrDefault(text)

        private const val NUMBER = """[+-]?\d{1,3}(?:\.\d+)?"""
        private val GEO_URI = Regex("""\bgeo:(\S+)""", RegexOption.IGNORE_CASE)
        private val QUERY_POINT = Regex("""($NUMBER)\s*,\s*($NUMBER)\s*(?:\((.*)\))?""")
        private val GE0_LINK = Regex("""(?:ge0://|https?://(?:omaps\.app|ge0\.me)/)([A-Za-z0-9_-]{5,11})(?:/([^\s?#]*))?""")
        private val URL = Regex("""https?://\S+""")
        private val PAIR = Regex("""($NUMBER)\s*,\s*($NUMBER)(?:,.*)?""")
        private val AT_PAIR = Regex("""@($NUMBER),($NUMBER)""")
        private val FRAGMENT_MAP = Regex("""#(?:map=)?\d{1,2}(?:\.\d+)?/($NUMBER)/($NUMBER)""")

        /** Decimal degrees with at least three decimals each, as locations are written, so that prices and versions in a text don't count. */
        private val BARE_PAIR = Regex("""(?<![\d.])([+-]?\d{1,2}\.\d{3,})\s*,\s*([+-]?\d{1,3}\.\d{3,})(?![\d.])""")
    }
}

/**
 * Organic Maps' short links (`omaps.app/<code>/<name>`, formerly `ge0://` from MAPS.ME): after a
 * zoom character, up to ten base64 characters, each holding three bits of latitude and three of
 * longitude, interleaved with latitude first, most significant bits first. Latitude spans -90..90
 * over 2³⁰ - 1 steps, longitude -180..180 over 2³⁰ steps. The name follows with spaces and
 * underscores swapped, then URL-encoded.
 */
internal object Ge0 {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val MAX_POINT_CHARS = 10
    private const val COORD_BITS = 3 * MAX_POINT_CHARS
    private const val MAX_VALUE = (1L shl COORD_BITS) - 1

    fun decode(code: String, encodedName: String = ""): SharedLocation? {
        val point = code.drop(1).take(MAX_POINT_CHARS)
        if (point.isEmpty()) return null
        var lat = 0L
        var lon = 0L
        var shift = COORD_BITS - 3
        for (char in point) {
            val bits = ALPHABET.indexOf(char).takeIf { it >= 0 } ?: return null
            val latBits = ((bits shr 5) and 1 shl 2) or ((bits shr 3) and 1 shl 1) or ((bits shr 1) and 1)
            val lonBits = ((bits shr 4) and 1 shl 2) or ((bits shr 2) and 1 shl 1) or (bits and 1)
            lat = lat or (latBits.toLong() shl shift)
            lon = lon or (lonBits.toLong() shl shift)
            shift -= 3
        }
        // A shorter code stands for a square; take its middle.
        if (point.length < MAX_POINT_CHARS) {
            val middle = 1L shl (3 * (MAX_POINT_CHARS - point.length) - 1)
            lat += middle
            lon += middle
        }
        val latitude = lat.toDouble() / MAX_VALUE * 180.0 - 90.0
        val longitude = lon.toDouble() / (MAX_VALUE + 1) * 360.0 - 180.0
        if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) return null
        return SharedLocation(latitude, longitude, decodeName(encodedName))
    }

    private fun decodeName(encoded: String): String? =
        SharedLocation.decode(encoded.replace("+", "%2B"))
            .map { if (it == '_') ' ' else if (it == ' ') '_' else it }
            .joinToString("")
            .trim()
            .ifEmpty { null }
}
