package com.umfluten.lighttimeclouds.astro

import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Apparent geocentric ecliptic coordinates, true equinox and ecliptic of date. */
internal class EclipticPosition(val longitude: Double, val latitude: Double, val distanceKm: Double)

/** Apparent geocentric equatorial coordinates, true equator and equinox of date. */
internal class EquatorialPosition(val rightAscension: Double, val declination: Double, val distanceKm: Double)

/**
 * Apparent geocentric positions of the Sun and the Moon from the truncated series in
 * [AstroSeries]: the Moon from ELP 2000-82B, the Sun from VSOP87D (Earth), both in the frame of
 * date, plus nutation (and for the Sun annual aberration) from [EarthOrientation]. The light time
 * of the Moon (1.3 s, under 1″) is left out.
 */
internal object Ephemeris {
    private const val ARCSEC = PI / 648000.0
    const val AU_KM = 149597870.7

    /** Constant of aberration (IAU 1976), radians at 1 au. */
    private const val ABERRATION = 20.49552 * ARCSEC

    /** One moment: the time arguments and the Earth's orientation, shared by everything computed for it. */
    class Epoch(val instant: Instant) {
        val earth: EarthOrientation = EarthOrientation.at(TimeScales.julianDateUt1(instant), TimeScales.centuriesTt(instant))
        val t: Double get() = earth.t
    }

    /** The Sun's apparent position: the series, plus nutation and annual aberration. */
    fun sun(epoch: Epoch): EclipticPosition {
        val (longitude, latitude, distanceAu) = SUN.at(epoch.t)
        val apparent = longitude + epoch.earth.nutationLongitude - ABERRATION / distanceAu
        return EclipticPosition(normalize(apparent), latitude, distanceAu * AU_KM)
    }

    /** The Moon's apparent position: the series, plus nutation. */
    fun moon(epoch: Epoch): EclipticPosition {
        val (longitude, latitude, distanceKm) = MOON.at(epoch.t)
        return EclipticPosition(normalize(longitude + epoch.earth.nutationLongitude), latitude, distanceKm)
    }

    /**
     * Geometric geocentric longitude and latitude of the Sun (rad, ecliptic and mean equinox of
     * date) and its distance (au) at [t] (Julian centuries TT), from VSOP87D: the Earth seen from
     * the Sun, turned around.
     */
    internal fun sunSeries(t: Double): Triple<Double, Double, Double> {
        val tau = t / 10.0
        val lbr = DoubleArray(3)
        with(AstroSeries) {
            for (k in VSOP_A.indices) {
                lbr[VSOP_COORDINATE[k]] += VSOP_A[k] * power(tau, VSOP_POWER[k]) * cos(VSOP_B[k] + VSOP_C[k] * tau)
            }
        }
        return Triple(lbr[0] + PI, -lbr[1], lbr[2])
    }

    /**
     * Geometric geocentric longitude and latitude of the Moon (rad, ecliptic and mean equinox of
     * date) and its distance (km) at [t] (Julian centuries TT), from ELP 2000-82B.
     */
    internal fun moonSeries(t: Double): Triple<Double, Double, Double> {
        val arguments = DoubleArray(ARGUMENT_COUNT) { i -> polynomial(AstroSeries.ELP_ARGUMENTS, i * 5, 5, t) }
        val sums = DoubleArray(3)
        with(AstroSeries) {
            for (k in ELP_AMPLITUDE.indices) {
                var angle = ELP_PHASE[k]
                val row = k * ARGUMENT_COUNT
                for (i in 0 until ARGUMENT_COUNT) {
                    val multiplier = ELP_MULTIPLIERS[row + i]
                    if (multiplier != 0) angle += multiplier * arguments[i]
                }
                sums[ELP_COORDINATE[k]] += ELP_AMPLITUDE[k] * power(t, ELP_POWER[k]) * sin(angle)
            }
        }
        // From the departure point of J2000 to the mean equinox of date (V_d = V + p_A, Laskar's
        // precession as given with ELP 2000-82B).
        val precession = (5029.0966 + (1.1120 + (0.000077 - 0.00002353 * t) * t) * t) * t * ARCSEC
        return Triple(polynomial(AstroSeries.ELP_W1, 0, 5, t) + sums[0] * ARCSEC + precession, sums[1] * ARCSEC, sums[2])
    }

    /**
     * Series values at nodes every [NODE_HOURS] hours, kept once computed, and interpolated with
     * the cubic through the four nearest nodes. Over three hours both bodies move so smoothly that
     * this adds less than 0.001″, while a minute's drawing (hundreds of positions over a day) needs
     * the series at only a dozen nodes, mostly the same as the minute before.
     */
    private class Interpolated(private val series: (Double) -> Triple<Double, Double, Double>) {
        private val nodes = ConcurrentHashMap<Long, Triple<Double, Double, Double>>()

        private fun node(index: Long): Triple<Double, Double, Double> {
            if (nodes.size > MAX_NODES) nodes.clear()
            return nodes.computeIfAbsent(index) { series(it * NODE_CENTURIES) }
        }

        fun at(t: Double): Triple<Double, Double, Double> {
            val x = t / NODE_CENTURIES
            val i = floor(x).toLong()
            val u = x - i
            val p = Array(4) { node(i - 1 + it) }
            // Longitudes continued across 2π from the second node on.
            val l = DoubleArray(4) { k -> p[1].first + angleBetween(p[1].first, p[k].first) }
            fun cubic(y0: Double, y1: Double, y2: Double, y3: Double): Double {
                // Lagrange through u = -1, 0, 1, 2.
                return -u * (u - 1) * (u - 2) / 6 * y0 + (u + 1) * (u - 1) * (u - 2) / 2 * y1 -
                    (u + 1) * u * (u - 2) / 2 * y2 + (u + 1) * u * (u - 1) / 6 * y3
            }
            return Triple(
                cubic(l[0], l[1], l[2], l[3]),
                cubic(p[0].second, p[1].second, p[2].second, p[3].second),
                cubic(p[0].third, p[1].third, p[2].third, p[3].third),
            )
        }

        private fun angleBetween(from: Double, to: Double): Double {
            val d = (to - from).mod(2 * PI)
            return if (d > PI) d - 2 * PI else d
        }
    }

    private const val NODE_HOURS = 3.0
    private const val NODE_CENTURIES = NODE_HOURS / 24.0 / TimeScales.DAYS_PER_CENTURY
    private const val MAX_NODES = 512

    private val SUN = Interpolated(::sunSeries)
    private val MOON = Interpolated(::moonSeries)

    fun equatorial(position: EclipticPosition, epoch: Epoch): EquatorialPosition {
        val e = epoch.earth.trueObliquity
        val l = position.longitude
        val b = position.latitude
        val rightAscension = atan2(sin(l) * cos(e) - tan(b) * sin(e), cos(l))
        val declination = asin(sin(b) * cos(e) + cos(b) * sin(e) * sin(l))
        return EquatorialPosition(normalize(rightAscension), declination, position.distanceKm)
    }

    private const val ARGUMENT_COUNT = 13

    private fun polynomial(coefficients: DoubleArray, offset: Int, count: Int, t: Double): Double {
        var value = 0.0
        for (k in count - 1 downTo 0) value = value * t + coefficients[offset + k]
        return value
    }

    private fun power(x: Double, n: Int): Double = when (n) {
        0 -> 1.0
        1 -> x
        2 -> x * x
        else -> Math.pow(x, n.toDouble())
    }

    fun normalize(angle: Double): Double = angle.mod(2 * PI)
}
