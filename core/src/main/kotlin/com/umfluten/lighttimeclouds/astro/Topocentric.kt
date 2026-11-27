package com.umfluten.lighttimeclouds.astro

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Positions as seen from a place on the WGS84 ellipsoid (at sea level): the parallax follows from
 * subtracting the observer's geocentric position from the body's, which matters for the Moon (up
 * to about 1°). Altitudes are geometric unless refraction is added with [refraction].
 */
internal object Topocentric {
    const val EARTH_RADIUS_KM = 6378.137
    private const val FLATTENING = 1 / 298.257223563
    private const val DEG = PI / 180.0

    /** Geometric altitude and azimuth (from south, positive toward west), radians. */
    fun horizontal(body: EquatorialPosition, epoch: Ephemeris.Epoch, latitude: Double, longitude: Double): SunMoonCalculator.HorizontalPosition {
        val phi = latitude * DEG
        val (x, y, z) = vector(body, epoch, latitude, longitude)
        val declination = atan2(z, sqrt(x * x + y * y))
        val hourAngle = localSiderealTime(epoch, longitude) - atan2(y, x)

        val altitude = asin((sin(phi) * sin(declination) + cos(phi) * cos(declination) * cos(hourAngle)).coerceIn(-1.0, 1.0))
        val azimuth = atan2(cos(declination) * sin(hourAngle), cos(declination) * cos(hourAngle) * sin(phi) - sin(declination) * cos(phi))
        return SunMoonCalculator.HorizontalPosition(azimuth, altitude)
    }

    /** [body] as seen from the place: x, y, z (km) in the equatorial frame of date, x toward the equinox. */
    fun vector(body: EquatorialPosition, epoch: Ephemeris.Epoch, latitude: Double, longitude: Double): Triple<Double, Double, Double> {
        val phi = latitude * DEG
        val localSiderealTime = localSiderealTime(epoch, longitude)
        // Observer, geocentric, equatorial frame of date.
        val c = 1 / sqrt(cos(phi) * cos(phi) + (1 - FLATTENING) * (1 - FLATTENING) * sin(phi) * sin(phi))
        val s = (1 - FLATTENING) * (1 - FLATTENING) * c
        val ox = EARTH_RADIUS_KM * c * cos(phi) * cos(localSiderealTime)
        val oy = EARTH_RADIUS_KM * c * cos(phi) * sin(localSiderealTime)
        val oz = EARTH_RADIUS_KM * s * sin(phi)
        val r = body.distanceKm
        return Triple(
            r * cos(body.declination) * cos(body.rightAscension) - ox,
            r * cos(body.declination) * sin(body.rightAscension) - oy,
            r * sin(body.declination) - oz,
        )
    }

    private fun localSiderealTime(epoch: Ephemeris.Epoch, longitude: Double): Double = epoch.earth.siderealTime + longitude * DEG

    /**
     * Atmospheric refraction (rad) for a geometric altitude [altitude] (rad), after Sæmundsson
     * (1986) for 10 °C and 1010 hPa; below −1° held at its value there. At a geometric altitude of
     * 0 it is about 29′; a body appears on the horizon at about −34′, where it is the usual 34′.
     */
    fun refraction(altitude: Double): Double {
        val h = (altitude / DEG).coerceAtLeast(-1.0)
        return 1.02 / tan((h + 10.3 / (h + 5.11)) * DEG) / 60.0 * DEG
    }

    /** Hour angle (rad, −π..π) of [body] at Greenwich meridian plus [longitude] east (degrees), geocentric. */
    fun hourAngle(body: EquatorialPosition, epoch: Ephemeris.Epoch, longitude: Double): Double {
        val h = (epoch.earth.siderealTime + longitude * DEG - body.rightAscension).mod(2 * PI)
        return if (h > PI) h - 2 * PI else h
    }
}
