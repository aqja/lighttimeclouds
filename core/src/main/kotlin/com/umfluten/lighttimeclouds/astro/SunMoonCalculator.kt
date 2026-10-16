package com.umfluten.lighttimeclouds.astro

import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Low-precision solar and lunar position/time calculations.
 *
 * Ported from the SunCalc algorithm, (c) 2014 Vladimir Agafonkin
 * (https://github.com/mourner/suncalc, BSD-2-Clause), itself based on
 * low-precision formulas from "Astronomy on the Personal Computer"
 * (Montenbruck & Pfleger) and Jean Meeus' "Astronomical Algorithms".
 * Accurate to roughly one arcminute, which is more than sufficient for
 * a clock-face scale rendering.
 */
object SunMoonCalculator {

    private const val RAD = PI / 180.0
    private const val DAY_MS = 1000.0 * 60.0 * 60.0 * 24.0
    private const val J1970 = 2440588.0
    private const val J2000 = 2451545.0
    private val OBLIQUITY_OF_EARTH = 23.4397 * RAD
    private val PERIHELION_OF_EARTH = 102.9372 * RAD
    private const val J0 = 0.0009

    private data class EquatorialCoords(val rightAscension: Double, val declination: Double, val distanceKm: Double = 0.0)
    data class HorizontalPosition(val azimuthRad: Double, val altitudeRad: Double) {
        val altitudeDegrees: Double get() = altitudeRad / RAD
        val azimuthDegrees: Double get() = azimuthRad / RAD
    }
    data class MoonIllumination(val fraction: Double, val phase: Double, val angle: Double)

    /** Moonrise/moonset within the 24h after the start instant; null if it does not happen in that span. */
    data class MoonTimes(val rise: Instant?, val set: Instant?, val alwaysUp: Boolean, val alwaysDown: Boolean)

    /** The eight named moon phases, each covering one eighth of the synodic cycle. */
    enum class MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
        FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
    }

    data class SunTimes(
        val sunrise: Instant?,
        val sunriseEnd: Instant?,
        val goldenHourEnd: Instant?,
        val solarNoon: Instant?,
        val goldenHour: Instant?,
        val sunsetStart: Instant?,
        val sunset: Instant?,
        val dusk: Instant?,
        val nauticalDusk: Instant?,
        val night: Instant?,
        val nadir: Instant?,
        val nightEnd: Instant?,
        val nauticalDawn: Instant?,
        val dawn: Instant?,
    )

    /** Sky brightness bucket used to color the day/twilight/night ring. */
    enum class SkyState { NIGHT, ASTRONOMICAL_TWILIGHT, NAUTICAL_TWILIGHT, CIVIL_TWILIGHT, GOLDEN_HOUR, DAY }

    private fun toDays(instant: Instant): Double {
        val julian = instant.toEpochMilli() / DAY_MS - 0.5 + J1970
        return julian - J2000
    }

    /** [d] is days since J2000 (as returned by [toDays]). */
    private fun fromDays(d: Double): Instant {
        val julian = d + J2000
        val millis = ((julian - J1970 + 0.5) * DAY_MS).toLong()
        return Instant.ofEpochMilli(millis)
    }

    private fun safeInstant(d: Double): Instant? = if (d.isNaN()) null else fromDays(d)

    private fun rightAscension(l: Double, b: Double): Double =
        atan2(sin(l) * cos(OBLIQUITY_OF_EARTH) - tan(b) * sin(OBLIQUITY_OF_EARTH), cos(l))

    private fun declination(l: Double, b: Double): Double =
        asin(sin(b) * cos(OBLIQUITY_OF_EARTH) + cos(b) * sin(OBLIQUITY_OF_EARTH) * sin(l))

    private fun azimuthOf(hourAngle: Double, phi: Double, dec: Double): Double =
        atan2(sin(hourAngle), cos(hourAngle) * sin(phi) - tan(dec) * cos(phi))

    private fun altitudeOf(hourAngle: Double, phi: Double, dec: Double): Double =
        asin(sin(phi) * sin(dec) + cos(phi) * cos(dec) * cos(hourAngle))

    private fun siderealTime(d: Double, lw: Double): Double = RAD * (280.16 + 360.9856235 * d) - lw

    private fun astroRefraction(altitudeIn: Double): Double {
        val h = if (altitudeIn < 0) 0.0 else altitudeIn
        return 0.0002967 / tan(h + 0.00312536 / (h + 0.08901179))
    }

    private fun solarMeanAnomaly(d: Double): Double = RAD * (357.5291 + 0.98560028 * d)

    private fun eclipticLongitude(meanAnomaly: Double): Double {
        val c = RAD * (1.9148 * sin(meanAnomaly) + 0.02 * sin(2 * meanAnomaly) + 0.0003 * sin(3 * meanAnomaly))
        return meanAnomaly + c + PERIHELION_OF_EARTH + PI
    }

    private fun sunCoords(d: Double): EquatorialCoords {
        val m = solarMeanAnomaly(d)
        val l = eclipticLongitude(m)
        return EquatorialCoords(rightAscension(l, 0.0), declination(l, 0.0))
    }

    /** Sun azimuth/altitude at [instant] for the given [latitude]/[longitude] (degrees). */
    fun sunPosition(instant: Instant, latitude: Double, longitude: Double): HorizontalPosition {
        val lw = RAD * -longitude
        val phi = RAD * latitude
        val d = toDays(instant)
        val c = sunCoords(d)
        val hourAngle = siderealTime(d, lw) - c.rightAscension
        return HorizontalPosition(azimuthOf(hourAngle, phi, c.declination), altitudeOf(hourAngle, phi, c.declination))
    }

    fun sunAltitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double =
        sunPosition(instant, latitude, longitude).altitudeDegrees

    /** Classifies a sun altitude (degrees) into the sky-brightness bucket used for ring coloring. */
    fun classify(altitudeDegrees: Double): SkyState = when {
        altitudeDegrees < -18.0 -> SkyState.NIGHT
        altitudeDegrees < -12.0 -> SkyState.ASTRONOMICAL_TWILIGHT
        altitudeDegrees < -6.0 -> SkyState.NAUTICAL_TWILIGHT
        altitudeDegrees < -0.833 -> SkyState.CIVIL_TWILIGHT
        altitudeDegrees < 6.0 -> SkyState.GOLDEN_HOUR
        else -> SkyState.DAY
    }

    private fun julianCycle(d: Double, lw: Double): Double = Math.round(d - J0 - lw / (2 * PI)).toDouble()

    private fun approxTransit(hourAngle: Double, lw: Double, n: Double): Double = J0 + (hourAngle + lw) / (2 * PI) + n

    private fun solarTransitJ(ds: Double, m: Double, l: Double): Double =
        J2000 + ds + 0.0053 * sin(m) - 0.0069 * sin(2 * l)

    /** Hour angle (radians) at which the sun reaches altitude [h] (radians); NaN if it never does that day (polar day/night). */
    private fun hourAngleForAltitude(h: Double, phi: Double, dec: Double): Double {
        val cosH = (sin(h) - sin(phi) * sin(dec)) / (cos(phi) * cos(dec))
        return if (cosH < -1.0 || cosH > 1.0) Double.NaN else acos(cosH)
    }

    private fun setJulian(h: Double, lw: Double, phi: Double, dec: Double, n: Double, m: Double, l: Double): Double {
        val w = hourAngleForAltitude(h, phi, dec)
        if (w.isNaN()) return Double.NaN
        val a = approxTransit(w, lw, n)
        return solarTransitJ(a, m, l)
    }

    /**
     * Sun event times (sunrise/sunset/twilight/golden hour) for the solar day whose noon is nearest
     * to [date], at [latitude]/[longitude]. Pass local noon to get a given calendar day reliably.
     * Events the sun never reaches that day (polar day/night) are null.
     */
    fun sunTimes(date: Instant, latitude: Double, longitude: Double): SunTimes {
        val lw = RAD * -longitude
        val phi = RAD * latitude
        val d = toDays(date)
        val n = julianCycle(d, lw)
        val ds = approxTransit(0.0, lw, n)
        val m = solarMeanAnomaly(ds)
        val l = eclipticLongitude(m)
        val dec = declination(l, 0.0)
        val jNoon = solarTransitJ(ds, m, l)

        fun eventPair(angleDeg: Double): Pair<Instant?, Instant?> {
            val jSet = setJulian(RAD * angleDeg, lw, phi, dec, n, m, l)
            if (jSet.isNaN()) return null to null
            val jRise = 2 * jNoon - jSet
            return safeInstant(jRise - J2000) to safeInstant(jSet - J2000)
        }

        val (sunriseEnd, sunsetStart) = eventPair(-0.3)
        val (sunrise, sunset) = eventPair(-0.833)
        val (goldenHourEnd, goldenHour) = eventPair(6.0)
        val (dawn, dusk) = eventPair(-6.0)
        val (nauticalDawn, nauticalDusk) = eventPair(-12.0)
        val (nightEnd, night) = eventPair(-18.0)

        return SunTimes(
            sunrise = sunrise,
            sunriseEnd = sunriseEnd,
            goldenHourEnd = goldenHourEnd,
            solarNoon = safeInstant(jNoon - J2000),
            goldenHour = goldenHour,
            sunsetStart = sunsetStart,
            sunset = sunset,
            dusk = dusk,
            nauticalDusk = nauticalDusk,
            night = night,
            nadir = safeInstant(jNoon - J2000 - 0.5),
            nightEnd = nightEnd,
            nauticalDawn = nauticalDawn,
            dawn = dawn,
        )
    }

    private fun moonCoords(d: Double): EquatorialCoords {
        val l = RAD * (218.316 + 13.176396 * d)
        val m = RAD * (134.963 + 13.064993 * d)
        val f = RAD * (93.272 + 13.229350 * d)
        val lon = l + RAD * 6.289 * sin(m)
        val lat = RAD * 5.128 * sin(f)
        val dist = 385001.0 - 20905.0 * cos(m)
        return EquatorialCoords(rightAscension(lon, lat), declination(lon, lat), dist)
    }

    fun moonPosition(instant: Instant, latitude: Double, longitude: Double): HorizontalPosition {
        val lw = RAD * -longitude
        val phi = RAD * latitude
        val d = toDays(instant)
        val c = moonCoords(d)
        val hourAngle = siderealTime(d, lw) - c.rightAscension
        val h = altitudeOf(hourAngle, phi, c.declination)
        return HorizontalPosition(azimuthOf(hourAngle, phi, c.declination), h + astroRefraction(h))
    }

    fun moonAltitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double =
        moonPosition(instant, latitude, longitude).altitudeDegrees

    /**
     * How many hours (0 until 24) the moon trails the sun on its daily path across the sky:
     * the right-ascension difference expressed as time. ~0 at new moon, ~6 at first quarter,
     * ~12 at full moon. A dial position "now minus this lag" therefore shows where the moon is
     * in its daily circuit, the same way the hour hand shows the sun's.
     */
    fun moonLagHours(instant: Instant): Double {
        val d = toDays(instant)
        val diff = moonCoords(d).rightAscension - sunCoords(d).rightAscension
        val normalized = ((diff % (2 * PI)) + 2 * PI) % (2 * PI)
        return normalized / (2 * PI) * 24.0
    }

    /** Illuminated fraction (0..1), phase (0=new, 0.5=full, wrapping to 1=new again) and bright-limb angle, at [instant]. */
    fun moonIllumination(instant: Instant): MoonIllumination {
        val d = toDays(instant)
        val s = sunCoords(d)
        val m = moonCoords(d)
        val sunDistanceKm = 149598000.0
        val phi = acos(
            (sin(s.declination) * sin(m.declination) +
                cos(s.declination) * cos(m.declination) * cos(s.rightAscension - m.rightAscension)).coerceIn(-1.0, 1.0)
        )
        val inc = atan2(sunDistanceKm * sin(phi), m.distanceKm - sunDistanceKm * cos(phi))
        val angle = atan2(
            cos(s.declination) * sin(s.rightAscension - m.rightAscension),
            sin(s.declination) * cos(m.declination) - cos(s.declination) * sin(m.declination) * cos(s.rightAscension - m.rightAscension)
        )
        val fraction = (1 + cos(inc)) / 2
        val phase = 0.5 + 0.5 * inc * (if (angle < 0) -1.0 else 1.0) / PI
        return MoonIllumination(fraction, phase, angle)
    }

    /** Names the phase value of [moonIllumination] (0 = new, 0.5 = full); each name covers ±1/16 cycle around its center. */
    fun moonPhaseOf(phase: Double): MoonPhase {
        val normalized = ((phase % 1.0) + 1.0) % 1.0
        val index = ((normalized * 8.0 + 0.5).toInt()) % 8
        return MoonPhase.entries[index]
    }

    /**
     * Moonrise and moonset in the 24 hours following [start] (SunCalc's getMoonTimes): samples the
     * moon's altitude every hour and finds horizon crossings by quadratic interpolation over each
     * two-hour span.
     */
    fun moonTimes(start: Instant, latitude: Double, longitude: Double): MoonTimes {
        val hc = 0.133 * RAD
        fun altitudeAt(hours: Int): Double =
            moonPosition(start.plusSeconds(hours * 3600L), latitude, longitude).altitudeRad - hc

        var h0 = altitudeAt(0)
        var rise: Double? = null
        var set: Double? = null
        var ye = 0.0
        for (i in 1..24 step 2) {
            val h1 = altitudeAt(i)
            val h2 = altitudeAt(i + 1)
            val a = (h0 + h2) / 2 - h1
            val b = (h2 - h0) / 2
            val xe = -b / (2 * a)
            ye = (a * xe + b) * xe + h1
            val d = b * b - 4 * a * h1
            var roots = 0
            var x1 = 0.0
            var x2 = 0.0
            if (d >= 0) {
                val dx = sqrt(d) / (abs(a) * 2)
                x1 = xe - dx
                x2 = xe + dx
                if (abs(x1) <= 1) roots++
                if (abs(x2) <= 1) roots++
                if (x1 < -1) x1 = x2
            }
            if (roots == 1) {
                if (h0 < 0) rise = i + x1 else set = i + x1
            } else if (roots == 2) {
                rise = i + if (ye < 0) x2 else x1
                set = i + if (ye < 0) x1 else x2
            }
            if (rise != null && set != null) break
            h0 = h2
        }

        fun at(hours: Double?) = hours?.let { start.plusMillis((it * 3600_000.0).toLong()) }
        return MoonTimes(
            rise = at(rise),
            set = at(set),
            alwaysUp = rise == null && set == null && ye > 0,
            alwaysDown = rise == null && set == null && ye <= 0,
        )
    }
}
