package com.umfluten.lighttimeclouds.astro

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Orientation of the Earth at one moment, after the IERS Conventions (2010), IERS Technical
 * Note 36: nutation from the largest lunisolar terms of IAU 2000, five in longitude and four in
 * obliquity (to about 0.2″), mean
 * obliquity of IAU 2006, and the Greenwich apparent sidereal time from the Earth rotation angle.
 * Polar motion (< 0.5″) is left out.
 */
internal class EarthOrientation private constructor(
    /** Julian centuries of TT since J2000. */
    val t: Double,
    /** Nutation in longitude (rad). */
    val nutationLongitude: Double,
    /** True obliquity of the ecliptic: mean obliquity plus nutation in obliquity (rad). */
    val trueObliquity: Double,
    /** Greenwich apparent sidereal time (rad): the hour angle of the true equinox of date. */
    val siderealTime: Double,
) {
    companion object {
        private const val ARCSEC = PI / 648000.0

        fun at(julianDateUt1: Double, t: Double): EarthOrientation {
            // Fundamental arguments (Simon et al. 1994; IERS TN36 eq. 5.43), arcseconds.
            val lPrime = (1287104.79305 + (129596581.0481 - 0.5532 * t) * t) * ARCSEC
            val f = (335779.526232 + (1739527262.8478 - 12.7512 * t) * t) * ARCSEC
            val d = (1072260.70369 + (1602961601.2090 - 6.3706 * t) * t) * ARCSEC
            val omega = (450160.398036 + (-6962890.5431 + 7.4722 * t) * t) * ARCSEC

            // Largest terms of the IAU 2000A lunisolar nutation (IERS TN36 tables 5.3a and 5.3b), arcseconds.
            val a1 = omega
            val a2 = 2 * f - 2 * d + 2 * omega
            val a3 = 2 * f + 2 * omega
            val a4 = 2 * omega
            val dPsi = (-17.2064161 - 0.0174666 * t) * sin(a1) + (-1.3170906 - 0.0001675 * t) * sin(a2) -
                0.2276413 * sin(a3) + 0.2074554 * sin(a4) + 0.1475877 * sin(lPrime)
            val dEps = (9.2052331 + 0.0009086 * t) * cos(a1) + (0.5730336 - 0.0003015 * t) * cos(a2) +
                0.0978459 * cos(a3) - 0.0897492 * cos(a4)

            // Mean obliquity, IAU 2006 (IERS TN36 eq. 5.40).
            val meanObliquity = (84381.406 + (-46.836769 + (-0.0001831 + 0.00200340 * t) * t) * t) * ARCSEC

            // Earth rotation angle (eq. 5.15) and GMST (eq. 5.32), then the equation of the equinoxes.
            val du = julianDateUt1 - TimeScales.J2000
            val era = 2 * PI * ((0.7790572732640 + 0.00273781191135448 * du + du).mod(1.0))
            val gmst = era + (0.014506 + (4612.156534 + 1.3915817 * t) * t) * ARCSEC
            val gast = gmst + dPsi * ARCSEC * cos(meanObliquity)
            return EarthOrientation(t, dPsi * ARCSEC, meanObliquity + dEps * ARCSEC, gast)
        }
    }
}
