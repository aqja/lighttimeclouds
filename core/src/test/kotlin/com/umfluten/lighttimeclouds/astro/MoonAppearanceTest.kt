package com.umfluten.lighttimeclouds.astro

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class MoonAppearanceTest {

    @Test
    fun `refraction lifts a body at -34 arcminutes onto the horizon`() {
        val horizon = -34.0 / 60.0 * DEG
        val apparent = horizon + Topocentric.refraction(horizon)
        assertEquals(0.0, apparent / DEG * 60.0, 0.5)
        // At the geometric horizon the formula gives less, since the body then appears higher.
        assertEquals(29.0, Topocentric.refraction(0.0) / DEG * 60.0, 0.5)
    }

    @Test
    fun `moon's disc stays within what its distance allows`() {
        // A month covers one perigee and one apogee: about 0.276° and 0.245°.
        val sizes = (0..30).map { SunMoonCalculator.moonSemiDiameterDegrees(START.plus(Duration.ofDays(it.toLong()))) }
        assertTrue("smallest ${sizes.min()}°", sizes.min() in 0.24..0.26)
        assertTrue("largest ${sizes.max()}°", sizes.max() in 0.26..0.28)
    }

    @Test
    fun `bright limb points along the great circle towards the sun`() {
        var checked = 0
        for ((lat, lon) in PLACES) {
            var t = START
            while (t.isBefore(START.plus(Duration.ofDays(60)))) {
                val expected = limbTowardsSun(t, lat, lon)
                if (expected != null) {
                    val actual = SunMoonCalculator.moonBrightLimbAngle(t, lat, lon)
                    val difference = abs(((actual - expected) / DEG + 540.0) % 360.0 - 180.0)
                    assertTrue("$t at $lat/$lon: ${actual / DEG}° vs ${expected / DEG}°", difference < 0.5)
                    checked++
                }
                t = t.plus(Duration.ofHours(7))
            }
        }
        assertTrue(checked > 300)
    }

    @Test
    fun `waxing moon is lit on the west side, which is left in the south`() {
        // First quarter, 2024-01-18; near the Moon's upper transit in the evening.
        val firstQuarter = Instant.parse("2024-01-18T03:53:00Z")
        val berlin = SunMoonCalculator.moonBrightLimbAngle(transit(firstQuarter, 13.4094), 52.5208, 13.4094) / DEG
        val canberra = SunMoonCalculator.moonBrightLimbAngle(transit(firstQuarter, 149.13), -35.2809, 149.13) / DEG
        assertTrue("Berlin: $berlin°", berlin in 225.0..315.0) // right
        assertTrue("Canberra: $canberra°", canberra in 45.0..135.0) // left
    }

    /** The Moon's upper transit nearest to [near] at [longitude], to within a few minutes. */
    private fun transit(near: Instant, longitude: Double): Instant {
        var t = near
        repeat(5) {
            val epoch = Ephemeris.Epoch(t)
            val moon = Ephemeris.equatorial(Ephemeris.moon(epoch), epoch)
            val hourAngle = Topocentric.hourAngle(moon, epoch, longitude)
            t = t.minusSeconds((hourAngle / (2 * PI) * 89_400.0).toLong())
        }
        return t
    }

    /**
     * Independent of Meeus' formulas: the direction on the sky from the Moon to the Sun, in the
     * horizon frame, measured from the zenith counterclockwise. Geocentric positions (put far away
     * so that no parallax enters), as in the formulas under test. Null where the Moon is too close
     * to the Sun or to the zenith for the direction to be well defined.
     */
    private fun limbTowardsSun(t: Instant, lat: Double, lon: Double): Double? {
        val epoch = Ephemeris.Epoch(t)
        fun horizonVector(position: EclipticPosition): DoubleArray {
            val e = Ephemeris.equatorial(position, epoch)
            val far = EquatorialPosition(e.rightAscension, e.declination, 1e15)
            val h = Topocentric.horizontal(far, epoch, lat, lon)
            // East, north, up; azimuth from south, positive toward west.
            return doubleArrayOf(
                -sin(h.azimuthRad) * cos(h.altitudeRad),
                -cos(h.azimuthRad) * cos(h.altitudeRad),
                sin(h.altitudeRad),
            )
        }
        val m = horizonVector(Ephemeris.moon(epoch))
        val s = horizonVector(Ephemeris.sun(epoch))
        if (dot(m, s) > cos(20 * DEG) || m[2] > cos(10 * DEG)) return null
        val towardsSun = minus(s, scale(m, dot(s, m)))
        val up = minus(doubleArrayOf(0.0, 0.0, 1.0), scale(m, m[2]))
        val right = cross(m, up)
        return atan2(-dot(towardsSun, right) / norm(right), dot(towardsSun, up) / norm(up)).mod(2 * PI)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun norm(a: DoubleArray) = sqrt(dot(a, a))
    private fun scale(a: DoubleArray, k: Double) = DoubleArray(3) { a[it] * k }
    private fun minus(a: DoubleArray, b: DoubleArray) = DoubleArray(3) { a[it] - b[it] }
    private fun cross(a: DoubleArray, b: DoubleArray) =
        doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])

    private companion object {
        const val DEG = PI / 180.0
        val START: Instant = Instant.parse("2024-01-01T00:00:00Z")

        // Berlin, Quito, Canberra
        val PLACES = listOf(52.5208 to 13.4094, -0.2202 to -78.5123, -35.2809 to 149.13)
    }
}
