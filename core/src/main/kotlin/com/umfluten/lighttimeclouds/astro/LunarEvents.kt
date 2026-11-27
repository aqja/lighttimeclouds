package com.umfluten.lighttimeclouds.astro

import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Something out of the ordinary the Moon does, at [instant]. */
sealed interface LunarEvent {
    val instant: Instant
}

/** The Moon nearest to the Earth in its orbit ([perigee]) or farthest from it (apogee), [distanceKm] center to center. */
data class Apsis(override val instant: Instant, val perigee: Boolean, val distanceKm: Double) : LunarEvent

/** A full moon near perigee ([supermoon]) or near apogee (micromoon), see [LunarEvents.notableFullMoon]. */
data class NotableFullMoon(override val instant: Instant, val supermoon: Boolean, val distanceKm: Double) : LunarEvent

enum class EclipseKind { PENUMBRAL, PARTIAL, ANNULAR, TOTAL }

/**
 * A lunar eclipse: the Moon in the Earth's penumbra from [penumbralStart] to [penumbralEnd], in the
 * umbra from [partialStart] to [partialEnd] and wholly in it from [totalStart] to [totalEnd], null
 * where it does not get that far. The magnitudes are the parts of the Moon's diameter in the
 * umbra and penumbra at [greatest], above 1 for the depth of a total eclipse.
 */
data class LunarEclipse(
    val kind: EclipseKind,
    val greatest: Instant,
    val umbralMagnitude: Double,
    val penumbralMagnitude: Double,
    val penumbralStart: Instant,
    val partialStart: Instant?,
    val totalStart: Instant?,
    val totalEnd: Instant?,
    val partialEnd: Instant?,
    val penumbralEnd: Instant,
) : LunarEvent {
    override val instant: Instant get() = greatest

    /** Beginning of what one sees: the Moon entering the umbra, or for a penumbral eclipse the penumbra. */
    val start: Instant get() = partialStart ?: penumbralStart

    val end: Instant get() = partialEnd ?: penumbralEnd
}

/**
 * A solar eclipse as seen from one place: the Moon's disc touches the Sun's at [start] and [end],
 * and while the eclipse is [EclipseKind.TOTAL] or [EclipseKind.ANNULAR] there, lies wholly over it or
 * within it from [centralStart] to [centralEnd]. At [greatest] it covers [magnitude] of the Sun's
 * diameter (above 1 in a total eclipse) and [obscuration] of its disc.
 */
data class SolarEclipse(
    val kind: EclipseKind,
    val greatest: Instant,
    val magnitude: Double,
    val obscuration: Double,
    val start: Instant,
    val centralStart: Instant?,
    val centralEnd: Instant?,
    val end: Instant,
) : LunarEvent {
    override val instant: Instant get() = greatest
}

/**
 * A lunar standstill: over the 18.6 years in which the Moon's orbit turns around, the range of its
 * declination in a month is widest ([major], up to ±[declinationDeg]) or narrowest. At [instant] the
 * mean ascending node is at the vernal (major) or autumnal equinox (minor); the Moon comes close to
 * those extremes every month for a year or so around it.
 */
data class LunarStandstill(override val instant: Instant, val major: Boolean, val declinationDeg: Double) : LunarEvent

/** How much of an eclipse a place has in view: the body above the horizon all along, for part of it, or not at all. */
enum class Visibility { WHOLE, PART, NONE }

/** A [LunarEvent] at a place: [visibility] for eclipses, null for the rest, which do not need the Moon in view. */
data class LocalLunarEvent(val event: LunarEvent, val visibility: Visibility?)

/**
 * Perigees and apogees, supermoons and micromoons, lunar and solar eclipses and lunar standstills,
 * from the positions of [Ephemeris]. Eclipses follow the conventions of the NASA eclipse canons:
 * the Earth's shadow enlarged by its atmosphere after Danjon, the Moon's mean radius for the
 * outer contacts and that of the valleys of its limb for the inner ones of a solar eclipse.
 */
object LunarEvents {

    /**
     * The perigees and apogees in [[from], [to]], in order of time. The distance, sampled every
     * [APSIS_STEP], is pinned down around each extreme to [SEARCH_TOLERANCE].
     */
    fun apsides(from: Instant, to: Instant): List<Apsis> {
        val times = generateSequence(from.minus(APSIS_STEP)) { it.plus(APSIS_STEP) }
            .takeWhile { !it.isAfter(to.plus(APSIS_STEP)) }
            .toList()
        val distances = times.map(::moonDistanceKm)
        val found = ArrayList<Apsis>()
        for (i in 1 until times.size - 1) {
            val perigee = distances[i] < distances[i - 1] && distances[i] <= distances[i + 1]
            val apogee = distances[i] > distances[i - 1] && distances[i] >= distances[i + 1]
            if (!perigee && !apogee) continue
            val sign = if (perigee) 1.0 else -1.0
            val instant = minimum(times[i - 1], times[i + 1]) { sign * moonDistanceKm(it) }
            if (!instant.isBefore(from) && !instant.isAfter(to)) found += Apsis(instant, perigee, moonDistanceKm(instant))
        }
        return found
    }

    /**
     * Whether the full moon at [fullMoon] is a supermoon or a micromoon, given [apsides] around it,
     * after R. Nolle's definition: with the Moon within a tenth of the range between the nearest
     * perigee and apogee from the nearer end, so at least 90% of the way to perigee (or apogee).
     * Null for an ordinary full moon or without a perigee and an apogee in [apsides].
     */
    fun notableFullMoon(fullMoon: Instant, apsides: List<Apsis>): NotableFullMoon? {
        fun nearest(perigee: Boolean) = apsides.filter { it.perigee == perigee }
            .minByOrNull { Duration.between(it.instant, fullMoon).abs() }
        val perigee = nearest(true) ?: return null
        val apogee = nearest(false) ?: return null
        val distance = moonDistanceKm(fullMoon)
        val margin = NOLLE_FRACTION * (apogee.distanceKm - perigee.distanceKm)
        return when {
            distance <= perigee.distanceKm + margin -> NotableFullMoon(fullMoon, supermoon = true, distance)
            distance >= apogee.distanceKm - margin -> NotableFullMoon(fullMoon, supermoon = false, distance)
            else -> null
        }
    }

    /** The lunar eclipse at the full moon at [fullMoon] (as from [SunMoonCalculator.nextMoonPhase]), or null if there is none. */
    fun lunarEclipse(fullMoon: Instant): LunarEclipse? {
        if (abs(Ephemeris.moon(Ephemeris.Epoch(fullMoon)).latitude) > ECLIPSE_LATITUDE_LIMIT) return null
        // The Moon passes closest to the shadow's axis within an hour or so of opposition.
        val greatest = minimum(fullMoon.minus(GREATEST_WINDOW), fullMoon.plus(GREATEST_WINDOW)) { shadow(it).separation }
        val g = shadow(greatest)
        if (g.separation >= g.penumbra + g.moonRadius) return null

        /** When [margin] turns negative before [greatest] and positive again after it; null if it stays positive. */
        fun contacts(margin: (Shadow) -> Double): Pair<Instant, Instant>? {
            val atGreatest = margin(g)
            if (atGreatest >= 0) return null
            val f = { t: Instant -> margin(shadow(t)) }
            val before = greatest.minus(CONTACT_WINDOW)
            val after = greatest.plus(CONTACT_WINDOW)
            return SunMoonCalculator.crossing(before, greatest, f(before), atGreatest, f) to
                SunMoonCalculator.crossing(greatest, after, atGreatest, f(after), f)
        }
        val penumbral = contacts { it.separation - it.penumbra - it.moonRadius } ?: return null
        val partial = contacts { it.separation - it.umbra - it.moonRadius }
        val total = contacts { it.separation - it.umbra + it.moonRadius }
        return LunarEclipse(
            kind = when {
                total != null -> EclipseKind.TOTAL
                partial != null -> EclipseKind.PARTIAL
                else -> EclipseKind.PENUMBRAL
            },
            greatest = greatest,
            umbralMagnitude = (g.umbra + g.moonRadius - g.separation) / (2 * g.moonRadius),
            penumbralMagnitude = (g.penumbra + g.moonRadius - g.separation) / (2 * g.moonRadius),
            penumbralStart = penumbral.first,
            partialStart = partial?.first,
            totalStart = total?.first,
            totalEnd = total?.second,
            partialEnd = partial?.second,
            penumbralEnd = penumbral.second,
        )
    }

    /**
     * The solar eclipse at the new moon at [newMoon] as seen from [latitude]/[longitude] (degrees),
     * whether or not the Sun is up there; null if the Moon misses the Sun's disc there.
     */
    fun solarEclipse(newMoon: Instant, latitude: Double, longitude: Double): SolarEclipse? {
        if (abs(Ephemeris.moon(Ephemeris.Epoch(newMoon)).latitude) > ECLIPSE_LATITUDE_LIMIT) return null
        fun discs(t: Instant) = discs(t, latitude, longitude)
        // The parallax can shift the conjunction as seen from the place by up to two hours.
        val grid = (-SOLAR_GRID_STEPS..SOLAR_GRID_STEPS).map { newMoon.plus(SOLAR_GRID_STEP.multipliedBy(it.toLong())) }
        val closest = grid.indices.minBy { discs(grid[it]).separation }
        if (closest == 0 || closest == grid.lastIndex) return null
        val greatest = minimum(grid[closest - 1], grid[closest + 1]) { discs(it).separation }
        val g = discs(greatest)
        val outer = { d: Discs -> d.separation - d.sunRadius - d.moonOuter }
        if (outer(g) >= 0) return null

        fun crossings(margin: (Discs) -> Double, window: Duration): Pair<Instant, Instant> {
            val f = { t: Instant -> margin(discs(t)) }
            val before = greatest.minus(window)
            val after = greatest.plus(window)
            return SunMoonCalculator.crossing(before, greatest, f(before), margin(g), f) to
                SunMoonCalculator.crossing(greatest, after, margin(g), f(after), f)
        }
        val (start, end) = crossings(outer, CONTACT_WINDOW)
        val inner = { d: Discs -> d.separation - abs(d.moonInner - d.sunRadius) }
        val central = if (inner(g) < 0) crossings(inner, CENTRAL_WINDOW) else null
        return SolarEclipse(
            kind = when {
                central == null -> EclipseKind.PARTIAL
                g.moonInner > g.sunRadius -> EclipseKind.TOTAL
                else -> EclipseKind.ANNULAR
            },
            greatest = greatest,
            magnitude = (g.sunRadius + g.moonOuter - g.separation) / (2 * g.sunRadius),
            obscuration = obscuration(g.sunRadius, g.moonOuter, g.separation),
            start = start,
            centralStart = central?.first,
            centralEnd = central?.second,
            end = end,
        )
    }

    /** How much of [eclipse] the place sees: the Moon's upper limb above the horizon, from [LunarEclipse.start] to its end. */
    fun visibility(eclipse: LunarEclipse, latitude: Double, longitude: Double): Visibility =
        visibility(eclipse.start, eclipse.end) { SunMoonCalculator.moonRiseMargin(it, latitude, longitude) > 0 }

    /** How much of [eclipse] the place sees: the Sun's upper limb above the horizon, from its start to its end. */
    fun visibility(eclipse: SolarEclipse, latitude: Double, longitude: Double): Visibility =
        visibility(eclipse.start, eclipse.end) {
            SunMoonCalculator.sunAltitudeDegrees(it, latitude, longitude) > SunMoonCalculator.SUNRISE_ALTITUDE
        }

    /**
     * The first lunar standstill, [major] or minor, after [after]: when the mean ascending node of
     * the Moon's orbit, which regresses by a full turn in 18.6 years, reaches the vernal or the
     * autumnal equinox.
     */
    fun nextStandstill(after: Instant, major: Boolean): LunarStandstill {
        val target = if (major) 0.0 else PI
        fun ahead(t: Instant) = (meanNode(t) - target).mod(2 * PI)
        var t = after.plusMillis((ahead(after) / (2 * PI) * NODE_PERIOD_MILLIS).toLong())
        repeat(3) {
            val offset = (ahead(t) + PI).mod(2 * PI) - PI
            t = t.plusMillis((offset / (2 * PI) * NODE_PERIOD_MILLIS).toLong())
        }
        val obliquity = Ephemeris.Epoch(t).earth.trueObliquity / DEG
        return LunarStandstill(t, major, obliquity + if (major) MEAN_INCLINATION_DEG else -MEAN_INCLINATION_DEG)
    }

    /**
     * The next of each kind after [after] as seen from [latitude]/[longitude] (degrees), in order of
     * time: perigee and apogee, supermoon and micromoon, the next lunar eclipse in which the Moon
     * enters the umbra while it is up at the place, and if that one is partial also the next total
     * one ("blood moon"), the next solar eclipse with the Sun up, and both kinds of standstill. An
     * eclipse under way at [after] counts as the next. Penumbral lunar eclipses, hard to notice,
     * are left out, as are kinds that do not occur within the search limits.
     */
    fun upcoming(after: Instant, latitude: Double, longitude: Double): List<LocalLunarEvent> {
        val events = ArrayList<LocalLunarEvent>()
        val apsides = apsides(after.minus(APSIS_MARGIN), after.plus(NOTABLE_FULL_MOON_SEARCH).plus(APSIS_MARGIN))
        listOf(true, false).forEach { perigee ->
            apsides.firstOrNull { it.perigee == perigee && it.instant.isAfter(after) }?.let { events += LocalLunarEvent(it, null) }
        }
        val notable = fullMoons(after, after.plus(NOTABLE_FULL_MOON_SEARCH)).mapNotNull { notableFullMoon(it, apsides) }
        listOf(true, false).forEach { supermoon ->
            notable.firstOrNull { it.supermoon == supermoon }?.let { events += LocalLunarEvent(it, null) }
        }

        // From a few hours back, so that an eclipse under way is found too.
        val lunar = fullMoons(after.minus(CONTACT_WINDOW), after.plus(ECLIPSE_SEARCH))
            .mapNotNull(::lunarEclipse)
            .filter { it.kind != EclipseKind.PENUMBRAL && it.end.isAfter(after) }
            .map { it to visibility(it, latitude, longitude) }
            .filter { it.second != Visibility.NONE }
        val firstLunar = lunar.firstOrNull()
        firstLunar?.let { events += LocalLunarEvent(it.first, it.second) }
        if (firstLunar != null && firstLunar.first.kind != EclipseKind.TOTAL) {
            lunar.firstOrNull { it.first.kind == EclipseKind.TOTAL }?.let { events += LocalLunarEvent(it.first, it.second) }
        }

        newMoons(after.minus(CONTACT_WINDOW), after.plus(ECLIPSE_SEARCH))
            .mapNotNull { solarEclipse(it, latitude, longitude) }
            .filter { it.end.isAfter(after) }
            .map { it to visibility(it, latitude, longitude) }
            .firstOrNull { it.second != Visibility.NONE }
            ?.let { events += LocalLunarEvent(it.first, it.second) }

        events += LocalLunarEvent(nextStandstill(after, major = true), null)
        events += LocalLunarEvent(nextStandstill(after, major = false), null)
        return events.sortedBy { it.event.instant }
    }

    /** The full moons from [from] on, lazily, up to [to]. */
    fun fullMoons(from: Instant, to: Instant): Sequence<Instant> = phases(from, to, FULL)

    /** The new moons from [from] on, lazily, up to [to]. */
    fun newMoons(from: Instant, to: Instant): Sequence<Instant> = phases(from, to, NEW)

    private fun phases(from: Instant, to: Instant, phase: Double): Sequence<Instant> =
        generateSequence(SunMoonCalculator.nextMoonPhase(from, phase)) {
            SunMoonCalculator.nextMoonPhase(it.plus(PHASE_STEP), phase)
        }.takeWhile { !it.isAfter(to) }

    private fun moonDistanceKm(t: Instant): Double = Ephemeris.moon(Ephemeris.Epoch(t)).distanceKm

    /** Geocentric: the Moon's center from the axis of the Earth's shadow, and the radii of penumbra, umbra and Moon (rad). */
    private class Shadow(val separation: Double, val penumbra: Double, val umbra: Double, val moonRadius: Double)

    private fun shadow(t: Instant): Shadow {
        val epoch = Ephemeris.Epoch(t)
        val sun = Ephemeris.sun(epoch)
        val moon = Ephemeris.moon(epoch)
        val separation = PI - angle(unit(sun.longitude, sun.latitude), unit(moon.longitude, moon.latitude))
        val parallax = SHADOW_ENLARGEMENT * asin(EARTH_RADIUS_KM / moon.distanceKm) + asin(EARTH_RADIUS_KM / sun.distanceKm)
        val sunRadius = asin(SUN_RADIUS_KM / sun.distanceKm)
        return Shadow(separation, parallax + sunRadius, parallax - sunRadius, asin(MOON_K_OUTER * EARTH_RADIUS_KM / moon.distanceKm))
    }

    /** As seen from a place: the centers of Sun and Moon apart, and the radii of the Sun and of the Moon's outer and inner limb (rad). */
    private class Discs(val separation: Double, val sunRadius: Double, val moonOuter: Double, val moonInner: Double)

    private fun discs(t: Instant, latitude: Double, longitude: Double): Discs {
        val epoch = Ephemeris.Epoch(t)
        val sun = Topocentric.vector(Ephemeris.equatorial(Ephemeris.sun(epoch), epoch), epoch, latitude, longitude)
        val moon = Topocentric.vector(Ephemeris.equatorial(Ephemeris.moon(epoch), epoch), epoch, latitude, longitude)
        val sunDistance = length(sun)
        val moonDistance = length(moon)
        return Discs(
            separation = angle(sun, moon),
            sunRadius = asin(SUN_RADIUS_KM / sunDistance),
            moonOuter = asin(MOON_K_OUTER * EARTH_RADIUS_KM / moonDistance),
            moonInner = asin(MOON_K_INNER * EARTH_RADIUS_KM / moonDistance),
        )
    }

    /** The part of a disc of radius [sun] covered by one of radius [moon] whose center is [distance] away. */
    internal fun obscuration(sun: Double, moon: Double, distance: Double): Double {
        if (distance >= sun + moon) return 0.0
        if (distance <= abs(sun - moon)) return min(sun, moon).let { it * it } / (sun * sun)
        val d = distance
        val a1 = sun * sun * acos(((d * d + sun * sun - moon * moon) / (2 * d * sun)).coerceIn(-1.0, 1.0))
        val a2 = moon * moon * acos(((d * d + moon * moon - sun * sun) / (2 * d * moon)).coerceIn(-1.0, 1.0))
        val a3 = 0.5 * sqrt(((-d + sun + moon) * (d + sun - moon) * (d - sun + moon) * (d + sun + moon)).coerceAtLeast(0.0))
        return (a1 + a2 - a3) / (PI * sun * sun)
    }

    private fun visibility(start: Instant, end: Instant, inView: (Instant) -> Boolean): Visibility {
        val steps = (Duration.between(start, end).toMillis() / VISIBILITY_STEP.toMillis()).coerceAtLeast(1)
        val seen = (0..steps).map { inView(start.plusMillis((end.toEpochMilli() - start.toEpochMilli()) * it / steps)) }
        return when {
            seen.all { it } -> Visibility.WHOLE
            seen.any { it } -> Visibility.PART
            else -> Visibility.NONE
        }
    }

    /** Mean longitude of the Moon's ascending node (rad), Meeus, Astronomical Algorithms, eq. 47.7. */
    private fun meanNode(t: Instant): Double {
        val c = TimeScales.centuriesTt(t)
        val degrees = 125.0445479 + c * (-1934.1362891 + c * (0.0020754 + c * (1.0 / 467441 - c / 60616000)))
        return degrees * DEG
    }

    /** Where [f] is least between [a] and [b], f having a single minimum there: golden-section search to [SEARCH_TOLERANCE]. */
    private fun minimum(a: Instant, b: Instant, f: (Instant) -> Double): Instant {
        val ratio = (sqrt(5.0) - 1) / 2
        var low = a.toEpochMilli().toDouble()
        var high = b.toEpochMilli().toDouble()
        fun at(ms: Double) = f(Instant.ofEpochMilli(ms.toLong()))
        var c = high - ratio * (high - low)
        var d = low + ratio * (high - low)
        var fc = at(c)
        var fd = at(d)
        while (high - low > SEARCH_TOLERANCE.toMillis()) {
            if (fc < fd) {
                high = d
                d = c
                fd = fc
                c = high - ratio * (high - low)
                fc = at(c)
            } else {
                low = c
                c = d
                fc = fd
                d = low + ratio * (high - low)
                fd = at(d)
            }
        }
        return Instant.ofEpochMilli(((low + high) / 2).toLong())
    }

    private fun unit(longitude: Double, latitude: Double) =
        Triple(cos(latitude) * cos(longitude), cos(latitude) * sin(longitude), sin(latitude))

    private fun length(v: Triple<Double, Double, Double>) = sqrt(v.first * v.first + v.second * v.second + v.third * v.third)

    /** Angle between two vectors, accurate also when it is small. */
    private fun angle(a: Triple<Double, Double, Double>, b: Triple<Double, Double, Double>): Double {
        val cx = a.second * b.third - a.third * b.second
        val cy = a.third * b.first - a.first * b.third
        val cz = a.first * b.second - a.second * b.first
        val dot = a.first * b.first + a.second * b.second + a.third * b.third
        return atan2(sqrt(cx * cx + cy * cy + cz * cz), dot)
    }

    private const val DEG = PI / 180.0
    private const val EARTH_RADIUS_KM = Topocentric.EARTH_RADIUS_KM

    /** IAU 1976, as in the eclipse canons (959.63″ at 1 au). */
    private const val SUN_RADIUS_KM = 696000.0

    /** The Moon's mean radius in Earth radii, and that of the valleys of its limb (IAU 1982). */
    private const val MOON_K_OUTER = 0.2725076
    private const val MOON_K_INNER = 0.2722810

    /** Danjon: the Earth's radius enlarged by its atmosphere (1/85), less its flattening, applied to the Moon's parallax. */
    private const val SHADOW_ENLARGEMENT = 1.01

    /** Nolle's supermoon: within 10% of the perigee–apogee range from perigee. */
    private const val NOLLE_FRACTION = 0.1

    /**
     * Beyond this ecliptic latitude (rad) at the full or new moon neither a lunar eclipse nor a solar
     * one anywhere on Earth can occur; the widest reach about 1.6°.
     */
    private const val ECLIPSE_LATITUDE_LIMIT = 1.8 * DEG

    private const val MEAN_INCLINATION_DEG = 5.145
    private const val NODE_PERIOD_MILLIS = 6798.38 * 86_400_000.0
    private const val FULL = 0.5
    private const val NEW = 0.0

    private val APSIS_STEP: Duration = Duration.ofHours(6)

    /** Around the time searched, so the perigee and apogee nearest to each full moon are known. */
    private val APSIS_MARGIN: Duration = Duration.ofDays(20)
    private val PHASE_STEP: Duration = Duration.ofDays(1)
    private val SEARCH_TOLERANCE: Duration = Duration.ofSeconds(1)
    private val GREATEST_WINDOW: Duration = Duration.ofHours(3)

    /** Half the longest eclipse with margin: the Moon is out of the shadow (and off the Sun) this far from the greatest eclipse. */
    private val CONTACT_WINDOW: Duration = Duration.ofHours(5)
    private val CENTRAL_WINDOW: Duration = Duration.ofMinutes(30)
    private val SOLAR_GRID_STEP: Duration = Duration.ofMinutes(10)
    private const val SOLAR_GRID_STEPS = 36
    private val VISIBILITY_STEP: Duration = Duration.ofMinutes(2)

    private val NOTABLE_FULL_MOON_SEARCH: Duration = Duration.ofDays(3 * 366)
    private val ECLIPSE_SEARCH: Duration = Duration.ofDays(25 * 366)
}
