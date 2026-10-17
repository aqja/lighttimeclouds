package com.umfluten.lighttimeclouds.sky

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Physically based sky colors (single scattering: Rayleigh, Mie, ozone absorption) for a given sun
 * altitude, as a vertical gradient from the horizon up to [TOP_ELEVATION_DEG]. [gradient] is the
 * original: looking at right angles to the sun with a fixed exposure. [averagedGradient] is our
 * extension for the dial ring, which has no compass direction: the sky averaged all around, with
 * a partial, capped eye adaptation as it gets darker.
 *
 * Port of "Horizon" by Daniel Lazaro (https://github.com/dnlzro/horizon, MIT License), whose
 * implementation derives from "Production Sky Rendering" by Andrew Helmer (MIT License) and whose
 * physical model and parameters come from "A Scalable and Production Ready Sky and Atmosphere
 * Rendering Technique" (Sébastien Hillaire, 2020).
 */
object SkyModel {

    // Coefficients of media components (1/m)
    private val RAYLEIGH_SCATTER = doubleArrayOf(5.802e-6, 13.558e-6, 33.1e-6)
    private const val MIE_SCATTER = 3.996e-6
    private const val MIE_ABSORB = 4.44e-6
    private val OZONE_ABSORB = doubleArrayOf(0.65e-6, 1.881e-6, 0.085e-6)

    // Altitude density distribution
    private const val RAYLEIGH_SCALE_HEIGHT = 8e3
    private const val MIE_SCALE_HEIGHT = 1.2e3

    private const val GROUND_RADIUS = 6_360e3
    private const val TOP_RADIUS = 6_460e3
    private const val SUN_INTENSITY = 1.0

    /** Integration steps along each ray. */
    private const val SAMPLES = 32

    /** Vertical field of view of the original; the gradient spans half of it above the horizon. */
    private const val FOV_DEG = 75.0
    const val TOP_ELEVATION_DEG = FOV_DEG / 2.0

    // Post-processing
    private const val EXPOSURE = 25.0

    /** [averagedGradient]: azimuths averaged, and the partial eye adaptation (exposure ∝ (reference / L)^exponent, capped). */
    private const val AVERAGE_AZIMUTHS = 12
    private const val ADAPTATION_EXPONENT = 0.5
    private const val MAX_ADAPTATION = 10.0
    private const val ADAPTATION_REFERENCE_ALTITUDE = 10.0
    private const val GAMMA = 2.2
    private const val SUNSET_BIAS_STRENGTH = 0.1

    /**
     * [stops] sRGB colors (packed 0xRRGGBB) from the horizon (index 0) up to [TOP_ELEVATION_DEG]
     * (last index), evenly spaced in the original's projection (not in elevation angle), for the
     * sun at [sunAltitudeDeg].
     */
    fun gradient(sunAltitudeDeg: Double, stops: Int): IntArray =
        linearGradient(sunAltitudeDeg, stops, azimuthDeg = 90.0).map { toSrgb(it, EXPOSURE) }.toIntArray()

    /**
     * Like [gradient], but averaged over all view azimuths (the glow on the sun's side included,
     * without the sun itself dominating), and with an exposure that rises as the horizon darkens:
     * by (reference / L)^[ADAPTATION_EXPONENT], at most [MAX_ADAPTATION]-fold, where L is the
     * horizon luminance and the reference that at [ADAPTATION_REFERENCE_ALTITUDE]. The cap keeps
     * the faint red residual glow of deep twilight from being lifted into an early dawn.
     */
    fun averagedGradient(sunAltitudeDeg: Double, stops: Int): IntArray {
        val linear = averagedLinear(sunAltitudeDeg, stops)
        val horizon = luminance(linear.first()).coerceAtLeast(1e-12)
        val adaptation = (adaptationReference / horizon).pow(ADAPTATION_EXPONENT).coerceIn(1.0, MAX_ADAPTATION)
        return linear.map { toSrgb(it, EXPOSURE * adaptation) }.toIntArray()
    }

    private val adaptationReference: Double by lazy {
        luminance(averagedLinear(ADAPTATION_REFERENCE_ALTITUDE, 2).first())
    }

    /** Mean of [linearGradient] over [AVERAGE_AZIMUTHS] view azimuths; mirror-symmetric about the sun, so half are computed. */
    private fun averagedLinear(sunAltitudeDeg: Double, stops: Int): List<DoubleArray> {
        val sum = List(stops) { DoubleArray(3) }
        val half = AVERAGE_AZIMUTHS / 2
        for (i in 0 until half) {
            val azimuth = (i + 0.5) * 360.0 / AVERAGE_AZIMUTHS
            linearGradient(sunAltitudeDeg, stops, azimuth).forEachIndexed { k, c ->
                for (j in 0..2) sum[k][j] += c[j] / half
            }
        }
        return sum
    }

    /** Linear inscattered light per stop (horizon first), viewing [azimuthDeg] away from the sun's direction. */
    private fun linearGradient(sunAltitudeDeg: Double, stops: Int, azimuthDeg: Double): List<DoubleArray> {
        require(stops >= 2) { "need at least two stops" }
        val altitude = Math.toRadians(sunAltitudeDeg)
        val azimuth = Math.toRadians(azimuthDeg)
        val sunDirection = normalize(doubleArrayOf(cos(altitude), sin(altitude), 0.0))
        val camera = doubleArrayOf(0.0, GROUND_RADIUS, 0.0)
        val focalZ = 1.0 / tan(Math.toRadians(FOV_DEG / 2.0))

        return List(stops) { i ->
            val s = i.toDouble() / (stops - 1)
            val view = normalize(doubleArrayOf(cos(azimuth) * focalZ, s, sin(azimuth) * focalZ))
            inscattered(camera, view, sunDirection)
        }
    }

    private fun luminance(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    /** Light scattered towards the camera along [view] (linear RGB, before exposure). */
    private fun inscattered(camera: DoubleArray, view: DoubleArray, sunDirection: DoubleArray): DoubleArray {
        val result = DoubleArray(3)
        val tExitTop = intersectSphere(camera, view, TOP_RADIUS) ?: return result
        if (tExitTop <= 0.0) return result

        val segmentLength = tExitTop / SAMPLES
        var tRay = segmentLength * 0.5

        val originRadius = length(camera)
        val downward = dot(camera, view) / originRadius < 0.0
        val startHeight = originRadius - GROUND_RADIUS
        val startCos = (dot(camera, view) / originRadius).coerceIn(-1.0, 1.0)
        val cameraToSpace = transmittance(startHeight, acos(abs(startCos)))

        val sunViewAngle = acos(dot(sunDirection, view).coerceIn(-1.0, 1.0))
        val phaseR = rayleighPhase(sunViewAngle)
        val phaseM = miePhase(sunViewAngle)

        repeat(SAMPLES) {
            val pos = DoubleArray(3) { k -> camera[k] + view[k] * tRay }
            val radius = length(pos)
            val up = DoubleArray(3) { k -> pos[k] / radius }
            val height = radius - GROUND_RADIUS

            val viewAngle = acos(abs(dot(up, view).coerceIn(-1.0, 1.0)))
            val sunAngle = acos(dot(up, sunDirection).coerceIn(-1.0, 1.0))

            val toSpace = transmittance(height, viewAngle)
            val light = transmittance(height, sunAngle)
            val densityR = exp(-height / RAYLEIGH_SCALE_HEIGHT)
            val densityM = exp(-height / MIE_SCALE_HEIGHT)

            for (k in 0..2) {
                val cameraToSample = if (downward) toSpace[k] / cameraToSpace[k] else cameraToSpace[k] / toSpace[k]
                val scattered = light[k] * (RAYLEIGH_SCATTER[k] * densityR * phaseR + MIE_SCATTER * densityM * phaseM)
                result[k] += cameraToSample * scattered * segmentLength
            }
            tRay += segmentLength
        }
        for (k in 0..2) result[k] *= SUN_INTENSITY
        return result
    }

    /** Transmittance from [height] to the top of the atmosphere along a ray at [angle] from the vertical. */
    private fun transmittance(height: Double, angle: Double): DoubleArray {
        val origin = doubleArrayOf(0.0, GROUND_RADIUS + height, 0.0)
        val direction = doubleArrayOf(sin(angle), cos(angle), 0.0)
        val distance = intersectSphere(origin, direction, TOP_RADIUS)
        if (distance == null || distance == 0.0) return doubleArrayOf(1.0, 1.0, 1.0)

        val segmentLength = distance / SAMPLES
        var t = 0.5 * segmentLength
        var odRayleigh = 0.0
        var odMie = 0.0
        var odOzone = 0.0
        repeat(SAMPLES) {
            val x = origin[0] + direction[0] * t
            val y = origin[1] + direction[1] * t
            val h = sqrt(x * x + y * y) - GROUND_RADIUS
            odRayleigh += exp(-h / RAYLEIGH_SCALE_HEIGHT) * segmentLength
            odMie += exp(-h / MIE_SCALE_HEIGHT) * segmentLength
            // Ozone: triangular profile centered at 25 km, 30 km wide.
            odOzone += (1.0 - min(abs(h - 25e3) / 15e3, 1.0)) * segmentLength
            t += segmentLength
        }
        return DoubleArray(3) { k ->
            exp(-(RAYLEIGH_SCATTER[k] * odRayleigh + MIE_ABSORB * odMie + OZONE_ABSORB[k] * odOzone))
        }
    }

    /** Exposure, a gentle warm bias for dark (twilight) skies, ACES tone mapping and gamma, to packed sRGB. */
    private fun toSrgb(linear: DoubleArray, exposure: Double): Int {
        var r = linear[0] * exposure
        var g = linear[1] * exposure
        var b = linear[2] * exposure

        val luminance = 0.2126 * r + 0.7152 * g + 0.0722 * b
        val w = 1.0 / (1.0 + 2.0 * luminance)
        r = max(0.0, r * (1.0 + 0.5 * SUNSET_BIAS_STRENGTH * w))
        g = max(0.0, g * (1.0 - 0.5 * SUNSET_BIAS_STRENGTH * w))
        b = max(0.0, b * (1.0 + 1.0 * SUNSET_BIAS_STRENGTH * w))

        fun channel(c: Double): Int {
            val toneMapped = ((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14)).coerceIn(0.0, 1.0)
            return (toneMapped.pow(1.0 / GAMMA) * 255.0).roundToInt().coerceIn(0, 255)
        }
        return (channel(r) shl 16) or (channel(g) shl 8) or channel(b)
    }

    private fun rayleighPhase(angle: Double): Double = 3.0 * (1.0 + cos(angle).pow(2)) / (16.0 * Math.PI)

    private fun miePhase(angle: Double): Double {
        val g = 0.8
        val num = (1 - g * g) * (1 + cos(angle).pow(2))
        val denom = (2 + g * g) * (1 + g * g - 2 * g * cos(angle)).pow(1.5)
        return 3.0 / (8.0 * Math.PI) * num / denom
    }

    /** Distance along [d] from [p] to the sphere of radius [r] around the origin (far hit if inside); null if missed. */
    private fun intersectSphere(p: DoubleArray, d: DoubleArray, r: Double): Double? {
        val b = dot(p, d)
        val c = dot(p, p) - r * r
        val discriminant = b * b - c
        if (discriminant < 0) return null
        val t = -b - sqrt(discriminant)
        return if (t < 0) -b + sqrt(discriminant) else t
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun length(v: DoubleArray) = sqrt(dot(v, v))
    private fun normalize(v: DoubleArray): DoubleArray {
        val l = length(v).takeIf { it > 0 } ?: 1.0
        return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}
