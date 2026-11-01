package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * Compares the ephemeris with reference values from JPL's DE440s, computed by
 * tools/reference_ephemeris.py (Skyfield) for 2000-2050 (geocentric) and 2000-2025 (as seen from
 * Berlin, Quito, Canberra and Longyearbyen).
 */
class EphemerisReferenceTest {

    private fun rows(name: String): List<Map<String, String>> {
        val lines = requireNotNull(javaClass.getResourceAsStream("/astro/$name")).bufferedReader().readLines()
        val header = lines.first().split(",")
        return lines.drop(1).filter { it.isNotBlank() }.map { line -> header.zip(line.split(",", limit = header.size)).toMap() }
    }

    private val places = mapOf(
        "berlin" to (52.5208 to 13.4094),
        "quito" to (-0.2202 to -78.5123),
        "canberra" to (-35.2809 to 149.1300),
        "longyearbyen" to (78.2232 to 15.6267),
    )

    private fun arcsec(radians: Double) = radians * 648000.0 / PI

    private fun angleDifference(a: Double, b: Double): Double {
        val d = (a - b).mod(2 * PI)
        return if (d > PI) d - 2 * PI else d
    }

    private class Worst(val label: String) {
        var value = 0.0
        var where = ""
        fun add(v: Double, at: String) {
            if (abs(v) > value) {
                value = abs(v)
                where = at
            }
        }
        override fun toString() = "$label %.2f at %s".format(value, where)
    }

    @Test
    fun `geocentric positions match DE440s`() {
        val worst = listOf("moon longitude \"", "moon latitude \"", "moon distance km", "sun longitude \"", "sun latitude \"").map(::Worst)
        for (row in rows("geocentric.csv")) {
            val epoch = Ephemeris.Epoch(Instant.parse(row.getValue("utc")))
            val moon = row["body"] == "moon"
            val position = if (moon) Ephemeris.moon(epoch) else Ephemeris.sun(epoch)
            val lon = row.getValue("longitude_deg").toDouble() * PI / 180
            val lat = row.getValue("latitude_deg").toDouble() * PI / 180
            val offset = if (moon) 0 else 3
            worst[offset].add(arcsec(angleDifference(position.longitude, lon)) * cos(lat), row.getValue("utc"))
            worst[offset + 1].add(arcsec(position.latitude - lat), row.getValue("utc"))
            if (moon) worst[2].add(position.distanceKm - row.getValue("distance_km").toDouble(), row.getValue("utc"))
        }
        println(worst.joinToString("\n"))
        val limits = listOf(LIMIT_ARCSEC, LIMIT_ARCSEC, 10.0, LIMIT_ARCSEC, LIMIT_ARCSEC)
        worst.zip(limits).forEach { (w, limit) -> assertTrue(w.toString(), w.value <= limit) }
    }

    @Test
    fun `altitude and azimuth match DE440s`() {
        val worst = listOf("sun altitude \"", "sun azimuth \"", "moon altitude \"", "moon azimuth \"").map(::Worst)
        for (row in rows("topocentric.csv")) {
            val instant = Instant.parse(row.getValue("utc"))
            val (lat, lon) = places.getValue(row.getValue("place"))
            val position = if (row["body"] == "sun") {
                SunMoonCalculator.sunPosition(instant, lat, lon)
            } else {
                SunMoonCalculator.moonGeometric(Ephemeris.Epoch(instant), lat, lon).first
            }
            val altitude = row.getValue("altitude_deg").toDouble() * PI / 180
            val azimuth = row.getValue("azimuth_deg").toDouble() * PI / 180
            val offset = if (row["body"] == "sun") 0 else 2
            val at = "${row["place"]} ${row["utc"]}"
            worst[offset].add(arcsec(position.altitudeRad - altitude), at)
            worst[offset + 1].add(arcsec(angleDifference(position.azimuthRad, azimuth)) * cos(altitude), at)
        }
        println(worst.joinToString("\n"))
        worst.forEach { assertTrue(it.toString(), it.value <= LIMIT_TOPOCENTRIC_ARCSEC) }
    }

    @Test
    fun `rise, set, twilight and transit times match DE440s`() {
        val worstSun = Worst("sun events s")
        val worstMoon = Worst("moon events s")
        for (row in rows("events.csv")) {
            val expected = Instant.parse(row.getValue("utc"))
            val (lat, lon) = places.getValue(row.getValue("place"))
            val event = row.getValue("event")
            val actual: Instant? = if (row["body"] == "sun") {
                val times = SunMoonCalculator.sunTimes(expected, lat, lon)
                when (event) {
                    "noon" -> times.solarNoon
                    "midnight" -> listOf(times.solarMidnight, times.nextSolarMidnight).minBy { Duration.between(it, expected).abs() }
                    else -> sunEvent(times, event == "rise", row.getValue("altitude_deg").toDouble())
                }
            } else {
                val times = SunMoonCalculator.moonTimes(expected.minus(Duration.ofHours(1)), lat, lon)
                if (event == "rise") times.rise else times.set
            }
            val at = "${row["place"]} ${row["body"]} $event ${row["altitude_deg"]} $expected"
            assertNotNull("missing $at", actual)
            val seconds = Duration.between(expected, actual).toMillis() / 1000.0
            if (row["body"] == "sun") worstSun.add(seconds, at) else worstMoon.add(seconds, at)
        }
        println("$worstSun\n$worstMoon")
        assertTrue(worstSun.toString(), worstSun.value <= LIMIT_SUN_SECONDS)
        assertTrue(worstMoon.toString(), worstMoon.value <= LIMIT_MOON_SECONDS)
    }

    @Test
    fun `interpolation between the nodes adds nothing visible to the series`() {
        var worst = 0.0
        for (i in 0 until 2000) {
            val instant = Instant.parse("2000-01-01T00:00:00Z").plusSeconds(i * 787_331L)
            val epoch = Ephemeris.Epoch(instant)
            val nutation = epoch.earth.nutationLongitude
            for ((interpolated, series) in listOf(
                Ephemeris.moon(epoch) to Ephemeris.moonSeries(epoch.t),
                Ephemeris.sun(epoch) to Ephemeris.sunSeries(epoch.t),
            )) {
                val aberration = if (series.third < 10.0) 20.49552 / 648000.0 * PI / series.third else 0.0
                worst = maxOf(worst, abs(arcsec(angleDifference(interpolated.longitude, series.first + nutation - aberration))))
                worst = maxOf(worst, abs(arcsec(interpolated.latitude - series.second)))
            }
        }
        assertTrue("interpolation error $worst\"", worst < 0.01)
    }

    private fun sunEvent(times: SunMoonCalculator.SunTimes, rise: Boolean, altitude: Double): Instant? = when (altitude) {
        SunMoonCalculator.SUNRISE_ALTITUDE -> if (rise) times.sunrise else times.sunset
        SunMoonCalculator.SUNRISE_END_ALTITUDE -> if (rise) times.sunriseEnd else times.sunsetStart
        SunMoonCalculator.GOLDEN_HOUR_ALTITUDE -> if (rise) times.goldenHourEnd else times.goldenHourStart
        SunMoonCalculator.CIVIL_TWILIGHT_ALTITUDE -> if (rise) times.civilDawn else times.civilDusk
        SunMoonCalculator.NAUTICAL_TWILIGHT_ALTITUDE -> if (rise) times.nauticalDawn else times.nauticalDusk
        SunMoonCalculator.ASTRONOMICAL_TWILIGHT_ALTITUDE -> if (rise) times.astronomicalDawn else times.astronomicalDusk
        else -> error("unknown altitude $altitude")
    }

    private companion object {
        /** Truncation (up to 4″) and the other approximations; the theories themselves agree with DE440s to well below 1″. */
        const val LIMIT_ARCSEC = 5.0

        /** As seen from a place, also UT1 ≈ UTC (less than 0.9 s, up to 14″). */
        const val LIMIT_TOPOCENTRIC_ARCSEC = 15.0

        const val LIMIT_SUN_SECONDS = 3.0
        const val LIMIT_MOON_SECONDS = 3.0
    }
}
