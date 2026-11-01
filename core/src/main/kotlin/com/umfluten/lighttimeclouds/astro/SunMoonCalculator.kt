package com.umfluten.lighttimeclouds.astro

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Positions, rise and set times and the phase of the Sun and the Moon for a place, from the
 * series in [Ephemeris] (ELP 2000-82B and VSOP87, truncated to about 10″) with [EarthOrientation]
 * and [Topocentric]. Azimuths count from south, positive toward west. The Sun's altitude is
 * geometric; the Moon's is topocentric with refraction, as one sees it.
 */
object SunMoonCalculator {

    private const val DEG = PI / 180.0

    data class HorizontalPosition(val azimuthRad: Double, val altitudeRad: Double) {
        val altitudeDegrees: Double get() = altitudeRad / DEG
        val azimuthDegrees: Double get() = azimuthRad / DEG
    }

    /** Illuminated fraction (0..1) and phase (0 new, 0.25 first quarter, 0.5 full, 0.75 last quarter). */
    data class MoonIllumination(val fraction: Double, val phase: Double)

    /** First moonrise and moonset within the 24 hours after the start; null if there is none. */
    data class MoonTimes(val rise: Instant?, val set: Instant?)

    /** The eight named moon phases, each covering one eighth of the synodic cycle. */
    enum class MoonPhase {
        NEW_MOON, WAXING_CRESCENT, FIRST_QUARTER, WAXING_GIBBOUS,
        FULL_MOON, WANING_GIBBOUS, LAST_QUARTER, WANING_CRESCENT,
    }

    /**
     * The Sun's events of one solar day, from the solar midnight before its noon to the solar
     * midnight after: rising before noon, setting after. Null where the Sun does not reach the
     * altitude that day (polar day and night, white nights).
     */
    data class SunTimes(
        val solarMidnight: Instant,
        val astronomicalDawn: Instant?,
        val nauticalDawn: Instant?,
        val civilDawn: Instant?,
        val sunrise: Instant?,
        val sunriseEnd: Instant?,
        val goldenHourEnd: Instant?,
        val solarNoon: Instant,
        val goldenHourStart: Instant?,
        val sunsetStart: Instant?,
        val sunset: Instant?,
        val civilDusk: Instant?,
        val nauticalDusk: Instant?,
        val astronomicalDusk: Instant?,
        val nextSolarMidnight: Instant,
    )

    /** Sky brightness bucket used to color the day/twilight/night ring. */
    enum class SkyState { NIGHT, ASTRONOMICAL_TWILIGHT, NAUTICAL_TWILIGHT, CIVIL_TWILIGHT, GOLDEN_HOUR, DAY }

    /** The Sun's center at −50′: the upper limb on the horizon, with 34′ refraction and 16′ radius. */
    const val SUNRISE_ALTITUDE = -0.833

    /** The Sun's lower limb leaving the horizon after sunrise (and touching it before sunset). */
    const val SUNRISE_END_ALTITUDE = -0.3

    const val GOLDEN_HOUR_ALTITUDE = 6.0
    const val CIVIL_TWILIGHT_ALTITUDE = -6.0
    const val NAUTICAL_TWILIGHT_ALTITUDE = -12.0
    const val ASTRONOMICAL_TWILIGHT_ALTITUDE = -18.0

    /** Classifies a sun altitude (degrees) into the sky-brightness bucket used for ring coloring. */
    fun classify(altitudeDegrees: Double): SkyState = when {
        altitudeDegrees < ASTRONOMICAL_TWILIGHT_ALTITUDE -> SkyState.NIGHT
        altitudeDegrees < NAUTICAL_TWILIGHT_ALTITUDE -> SkyState.ASTRONOMICAL_TWILIGHT
        altitudeDegrees < CIVIL_TWILIGHT_ALTITUDE -> SkyState.NAUTICAL_TWILIGHT
        altitudeDegrees < SUNRISE_ALTITUDE -> SkyState.CIVIL_TWILIGHT
        altitudeDegrees < GOLDEN_HOUR_ALTITUDE -> SkyState.GOLDEN_HOUR
        else -> SkyState.DAY
    }

    // --- Sun -------------------------------------------------------------------------------------

    /** The Sun's geometric altitude and azimuth at [instant] for [latitude]/[longitude] (degrees). */
    fun sunPosition(instant: Instant, latitude: Double, longitude: Double): HorizontalPosition {
        val epoch = Ephemeris.Epoch(instant)
        return Topocentric.horizontal(Ephemeris.equatorial(Ephemeris.sun(epoch), epoch), epoch, latitude, longitude)
    }

    fun sunAltitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double =
        sunPosition(instant, latitude, longitude).altitudeDegrees

    /**
     * The Sun's events of the solar day whose noon is nearest to [date] (pass local noon to get a
     * given calendar day). Noon and midnights are the meridian transits; the other events are the
     * Sun's center crossing their altitudes, found between midnight and noon (rising) or noon and
     * midnight (setting), where the altitude changes monotonically.
     */
    fun sunTimes(date: Instant, latitude: Double, longitude: Double): SunTimes {
        val noon = sunTransit(date, longitude, upper = true)
        val midnight = sunTransit(noon.minus(HALF_DAY), longitude, upper = false)
        val nextMidnight = sunTransit(noon.plus(HALF_DAY), longitude, upper = false)
        fun altitude(t: Instant) = sunAltitudeDegrees(t, latitude, longitude)
        val atMidnight = altitude(midnight)
        val atNoon = altitude(noon)
        val atNextMidnight = altitude(nextMidnight)
        fun rise(h: Double) = if (atMidnight < h && atNoon > h) crossing(midnight, noon, atMidnight - h, atNoon - h) { altitude(it) - h } else null
        fun set(h: Double) = if (atNoon > h && atNextMidnight < h) crossing(noon, nextMidnight, atNoon - h, atNextMidnight - h) { altitude(it) - h } else null
        return SunTimes(
            solarMidnight = midnight,
            astronomicalDawn = rise(ASTRONOMICAL_TWILIGHT_ALTITUDE),
            nauticalDawn = rise(NAUTICAL_TWILIGHT_ALTITUDE),
            civilDawn = rise(CIVIL_TWILIGHT_ALTITUDE),
            sunrise = rise(SUNRISE_ALTITUDE),
            sunriseEnd = rise(SUNRISE_END_ALTITUDE),
            goldenHourEnd = rise(GOLDEN_HOUR_ALTITUDE),
            solarNoon = noon,
            goldenHourStart = set(GOLDEN_HOUR_ALTITUDE),
            sunsetStart = set(SUNRISE_END_ALTITUDE),
            sunset = set(SUNRISE_ALTITUDE),
            civilDusk = set(CIVIL_TWILIGHT_ALTITUDE),
            nauticalDusk = set(NAUTICAL_TWILIGHT_ALTITUDE),
            astronomicalDusk = set(ASTRONOMICAL_TWILIGHT_ALTITUDE),
            nextSolarMidnight = nextMidnight,
        )
    }

    /** The upper (noon) or lower (midnight) meridian transit of the Sun nearest to [near]. */
    private fun sunTransit(near: Instant, longitude: Double, upper: Boolean): Instant {
        var t = near
        repeat(4) {
            val epoch = Ephemeris.Epoch(t)
            val hourAngle = Topocentric.hourAngle(Ephemeris.equatorial(Ephemeris.sun(epoch), epoch), epoch, longitude)
            val offset = if (upper) hourAngle else (hourAngle + 2 * PI).mod(2 * PI) - PI
            // The Sun's hour angle grows by a full turn per solar day.
            t = t.minusMillis((offset / (2 * PI) * DAY_MILLIS).toLong())
        }
        return t
    }

    // --- Moon ------------------------------------------------------------------------------------

    /** The Moon's altitude (with refraction) and azimuth at [instant] as seen from [latitude]/[longitude] (degrees). */
    fun moonPosition(instant: Instant, latitude: Double, longitude: Double): HorizontalPosition {
        val geometric = moonGeometric(Ephemeris.Epoch(instant), latitude, longitude).first
        return geometric.copy(altitudeRad = geometric.altitudeRad + Topocentric.refraction(geometric.altitudeRad))
    }

    fun moonAltitudeDegrees(instant: Instant, latitude: Double, longitude: Double): Double =
        moonPosition(instant, latitude, longitude).altitudeDegrees

    /** Geometric topocentric position, and the geocentric distance (km). */
    internal fun moonGeometric(epoch: Ephemeris.Epoch, latitude: Double, longitude: Double): Pair<HorizontalPosition, Double> {
        val moon = Ephemeris.equatorial(Ephemeris.moon(epoch), epoch)
        return Topocentric.horizontal(moon, epoch, latitude, longitude) to moon.distanceKm
    }

    /**
     * How many hours (0 until 24) the Moon trails the Sun on its daily path across the sky: the
     * difference of their right ascensions as time. ~0 at new moon, ~6 at first quarter, ~12 at
     * full moon. A dial position "now minus this lag" therefore shows where the Moon is in its
     * daily circuit, the same way the hour hand shows the Sun's.
     */
    fun moonLagHours(instant: Instant): Double {
        val epoch = Ephemeris.Epoch(instant)
        val moon = Ephemeris.equatorial(Ephemeris.moon(epoch), epoch)
        val sun = Ephemeris.equatorial(Ephemeris.sun(epoch), epoch)
        return (moon.rightAscension - sun.rightAscension).mod(2 * PI) / (2 * PI) * 24.0
    }

    /**
     * The Moon's illuminated fraction, from its phase angle (the angle Sun–Moon–Earth), and its
     * phase as the elongation in ecliptic longitude from the Sun, as a fraction of a full turn.
     */
    fun moonIllumination(instant: Instant): MoonIllumination {
        val epoch = Ephemeris.Epoch(instant)
        val moon = Ephemeris.moon(epoch)
        val sun = Ephemeris.sun(epoch)
        val elongation = acos((cos(moon.latitude) * cos(moon.longitude - sun.longitude)).coerceIn(-1.0, 1.0))
        val phaseAngle = atan2(sun.distanceKm * sin(elongation), moon.distanceKm - sun.distanceKm * cos(elongation))
        val phase = (moon.longitude - sun.longitude).mod(2 * PI) / (2 * PI)
        return MoonIllumination((1 + cos(phaseAngle)) / 2, phase)
    }

    /** Names the phase value of [moonIllumination] (0 = new, 0.5 = full); each name covers ±1/16 cycle around its center. */
    fun moonPhaseOf(phase: Double): MoonPhase {
        val normalized = ((phase % 1.0) + 1.0) % 1.0
        val index = ((normalized * 8.0 + 0.5).toInt()) % 8
        return MoonPhase.entries[index]
    }

    /**
     * The first moonrise and moonset in the 24 hours after [start]: the upper limb on the horizon,
     * i.e. the center at −34′ (refraction) minus the Moon's apparent radius, geometrically. The
     * altitude is sampled every half hour and each crossing then pinned down to a second.
     */
    fun moonTimes(start: Instant, latitude: Double, longitude: Double): MoonTimes {
        fun margin(t: Instant): Double {
            val (position, distance) = moonGeometric(Ephemeris.Epoch(t), latitude, longitude)
            val horizon = -MOONRISE_REFRACTION - asin(MOON_RADIUS_RATIO * EARTH_RADIUS_KM / distance)
            return position.altitudeRad - horizon
        }
        var rise: Instant? = null
        var set: Instant? = null
        var previous = start
        var previousMargin = margin(start)
        for (step in 1..MOON_SAMPLES) {
            val t = start.plus(MOON_SAMPLE_STEP.multipliedBy(step.toLong()))
            val m = margin(t)
            if (previousMargin < 0 && m >= 0 && rise == null) rise = crossing(previous, t, previousMargin, m, ::margin)
            if (previousMargin >= 0 && m < 0 && set == null) set = crossing(previous, t, previousMargin, m, ::margin)
            if (rise != null && set != null) break
            previous = t
            previousMargin = m
        }
        return MoonTimes(rise, set)
    }

    private const val MOONRISE_REFRACTION = 34.0 / 60.0 * DEG
    private const val MOON_RADIUS_RATIO = 0.2725
    private const val EARTH_RADIUS_KM = 6378.137
    private const val MOON_SAMPLES = 48
    private val MOON_SAMPLE_STEP: Duration = Duration.ofMinutes(30)

    private val HALF_DAY: Duration = Duration.ofHours(12)
    private const val DAY_MILLIS = 86_400_000.0

    /**
     * The moment between [a] and [b] where [f] changes sign, given [fa] and [fb] of opposite sign:
     * regula falsi with the Illinois modification, to within half a second.
     */
    private fun crossing(a: Instant, b: Instant, fa: Double, fb: Double, f: (Instant) -> Double): Instant {
        var low = a.toEpochMilli().toDouble()
        var high = b.toEpochMilli().toDouble()
        var fLow = fa
        var fHigh = fb
        var side = 0
        repeat(60) {
            val t = (low * fHigh - high * fLow) / (fHigh - fLow)
            if (high - low < 500.0) return Instant.ofEpochMilli(t.toLong())
            val ft = f(Instant.ofEpochMilli(t.toLong()))
            if ((ft < 0) == (fLow < 0)) {
                low = t
                fLow = ft
                if (side == -1) fHigh /= 2
                side = -1
            } else {
                high = t
                fHigh = ft
                if (side == 1) fLow /= 2
                side = 1
            }
            if (abs(ft) < 1e-9) return Instant.ofEpochMilli(t.toLong())
        }
        return Instant.ofEpochMilli(((low + high) / 2).toLong())
    }
}
