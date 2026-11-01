package com.umfluten.lighttimeclouds.sky

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Physically based sky colors (Rayleigh and Mie scattering, ozone absorption) for a given sun
 * altitude, as a vertical gradient upward from the horizon. [gradient] is the original: looking at
 * right angles to the sun with a fixed exposure, up to [TOP_ELEVATION_DEG]; three wavelengths
 * (one per RGB channel), single scattering. [averagedGradient] is our extension for the dial
 * ring, which has no compass direction: the sky at given elevations (spread across the ring by
 * [SkyProjection]), averaged all around, with a partial, capped eye adaptation as it gets darker;
 * computed over the visible [Spectrum] and with multiple scattering, which both matter in
 * twilight: when only the high atmosphere is still sunlit, its light reaches the shadowed air
 * below by scattering more than once, and the blue of the sky above comes from ozone absorbing
 * across a broad band of the spectrum, which three wavelengths render as a purple.
 *
 * Port of "Horizon" by Daniel Lazaro (https://github.com/dnlzro/horizon, MIT License), whose
 * implementation derives from "Production Sky Rendering" by Andrew Helmer
 * (https://www.shadertoy.com/view/slSXRW, MIT License as stated by its author in the shader's
 * comments) and whose physical model and parameters come from "A Scalable and Production Ready Sky
 * and Atmosphere Rendering Technique" (Sébastien Hillaire, 2020); the multiple scattering follows
 * section 5.5 of that paper.
 */
object SkyModel {

    // Coefficients of media components (1/m); Rayleigh and ozone per RGB channel (680, 550, 440 nm).
    private val RAYLEIGH_SCATTER = doubleArrayOf(5.802e-6, 13.558e-6, 33.1e-6)
    private const val MIE_SCATTER = 3.996e-6

    /** Mie extinction (scattering plus absorption). */
    private const val MIE_EXTINCTION = 4.44e-6
    private val OZONE_ABSORB = doubleArrayOf(0.65e-6, 1.881e-6, 0.085e-6)

    // Altitude density distribution
    private const val RAYLEIGH_SCALE_HEIGHT = 8e3
    private const val MIE_SCALE_HEIGHT = 1.2e3

    private const val GROUND_RADIUS = 6_360e3
    private const val TOP_RADIUS = 6_460e3

    /** Share of the light reaching the ground that it reflects (diffusely); for multiple scattering. */
    private const val GROUND_ALBEDO = 0.3

    /**
     * [Physics.multipleScatteringTable]: sun altitudes (local, at the scattering point) from
     * [MS_MIN_SUN_ALTITUDE] to 90° in [MS_SUN_STEP] steps × [MS_HEIGHTS] heights (denser near the
     * ground); each entry integrated over [MS_DIRECTIONS] directions with [MS_SAMPLES] steps.
     */
    private const val MS_MIN_SUN_ALTITUDE = -30.0
    private const val MS_SUN_STEP = 1.0
    private const val MS_HEIGHTS = 32
    private const val MS_DIRECTIONS = 64
    private const val MS_SAMPLES = 24

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
    private const val MAX_ADAPTATION = 1000.0
    private const val ADAPTATION_REFERENCE_ALTITUDE = 10.0
    private const val GAMMA = 2.2
    private const val SUNSET_BIAS_STRENGTH = 0.1

    private const val ISOTROPIC_PHASE = 1.0 / (4.0 * Math.PI)

    /**
     * What a model run computes: the light at the wavelengths with these [rayleigh] and [ozone]
     * coefficients, from a sun of [sun] irradiance, turned into linear RGB by [toLinearRgb]; with
     * [multiple] scattering or single scattering only.
     */
    private class Physics(
        val rayleigh: DoubleArray,
        val ozone: DoubleArray,
        val sun: DoubleArray,
        val multiple: Boolean,
        val toLinearRgb: (DoubleArray) -> DoubleArray,
    ) {
        val bands get() = rayleigh.size

        /** [multipleScatteringAt] per sun altitude (outer) and height (inner, square-root spaced). */
        val multipleScatteringTable: Array<DoubleArray> by lazy {
            val altitudes = ((90.0 - MS_MIN_SUN_ALTITUDE) / MS_SUN_STEP).roundToInt() + 1
            Array(altitudes * MS_HEIGHTS) { index ->
                val altitude = MS_MIN_SUN_ALTITUDE + index / MS_HEIGHTS * MS_SUN_STEP
                val f = (index % MS_HEIGHTS).toDouble() / (MS_HEIGHTS - 1)
                multipleScatteringAt(this, f * f * (TOP_RADIUS - GROUND_RADIUS), altitude)
            }
        }

        val adaptationReference: Double by lazy {
            luminance(averagedLinear(this, ADAPTATION_REFERENCE_ALTITUDE, doubleArrayOf(0.0)).first())
        }

        /**
         * [averagedGradient]'s exposure: [EXPOSURE], which was tuned for the [ORIGINAL] model,
         * scaled so that the horizon at [ADAPTATION_REFERENCE_ALTITUDE] is as bright as there.
         */
        val exposure: Double by lazy { EXPOSURE * ORIGINAL.adaptationReference / adaptationReference }
    }

    /** The original's: three wavelengths standing for the RGB channels, a white sun, single scattering. */
    private val ORIGINAL = Physics(RAYLEIGH_SCATTER, OZONE_ABSORB, doubleArrayOf(1.0, 1.0, 1.0), multiple = false) { it }

    /** The dial ring's: the visible spectrum, multiple scattering. */
    private val RING = Physics(
        Spectrum.rayleighScattering, Spectrum.ozoneAbsorption, Spectrum.solarIrradiance, multiple = true,
        Spectrum::toLinearSrgb,
    )

    /**
     * [stops] sRGB colors (packed 0xRRGGBB) from the horizon (index 0) up to [TOP_ELEVATION_DEG]
     * (last index), evenly spaced in the original's projection (not in elevation angle), for the
     * sun at [sunAltitudeDeg].
     */
    fun gradient(sunAltitudeDeg: Double, stops: Int): IntArray {
        require(stops >= 2) { "need at least two stops" }
        val elevations = DoubleArray(stops) { i -> SkyProjection.elevation(i.toDouble() / (stops - 1), TOP_ELEVATION_DEG) }
        return linearGradient(ORIGINAL, sunAltitudeDeg, elevations, azimuthDeg = 90.0).map { toSrgb(it, EXPOSURE) }.toIntArray()
    }

    /**
     * Sky colors (packed 0xRRGGBB) at [elevationsDeg] above the horizon, whose first entry must be
     * the horizon (0°), for the sun at [sunAltitudeDeg]: averaged over all view azimuths (the glow
     * on the sun's side included, without the sun itself dominating), and with an exposure that
     * rises as the horizon darkens: by (reference / L)^[ADAPTATION_EXPONENT], at most
     * [MAX_ADAPTATION]-fold, where L is the horizon luminance and the reference that at
     * [ADAPTATION_REFERENCE_ALTITUDE]. The adaptation lifts the blue of deep twilight into view
     * while the sky still darkens towards night. Over the visible spectrum and with multiple
     * scattering (see [RING]).
     */
    fun averagedGradient(sunAltitudeDeg: Double, elevationsDeg: DoubleArray): IntArray {
        require(elevationsDeg.firstOrNull() == 0.0) { "the first elevation must be the horizon" }
        val linear = averagedLinear(RING, sunAltitudeDeg, elevationsDeg)
        val horizon = luminance(linear.first()).coerceAtLeast(1e-12)
        val adaptation = (RING.adaptationReference / horizon).pow(ADAPTATION_EXPONENT).coerceIn(1.0, MAX_ADAPTATION)
        return linear.map { toSrgb(it, RING.exposure * adaptation) }.toIntArray()
    }

    /**
     * Mean of [linearGradient] over [AVERAGE_AZIMUTHS] view azimuths, as linear RGB;
     * mirror-symmetric about the sun, so half are computed.
     */
    private fun averagedLinear(physics: Physics, sunAltitudeDeg: Double, elevationsDeg: DoubleArray): List<DoubleArray> {
        val sum = List(elevationsDeg.size) { DoubleArray(3) }
        val half = AVERAGE_AZIMUTHS / 2
        for (i in 0 until half) {
            val azimuth = (i + 0.5) * 360.0 / AVERAGE_AZIMUTHS
            linearGradient(physics, sunAltitudeDeg, elevationsDeg, azimuth).forEachIndexed { k, c ->
                for (j in 0..2) sum[k][j] += c[j] / half
            }
        }
        return sum
    }

    /** Linear RGB of the inscattered light at each of [elevationsDeg], viewing [azimuthDeg] away from the sun's direction. */
    private fun linearGradient(
        physics: Physics,
        sunAltitudeDeg: Double,
        elevationsDeg: DoubleArray,
        azimuthDeg: Double,
    ): List<DoubleArray> {
        val altitude = Math.toRadians(sunAltitudeDeg)
        val azimuth = Math.toRadians(azimuthDeg)
        val sunDirection = normalize(doubleArrayOf(cos(altitude), sin(altitude), 0.0))
        val camera = doubleArrayOf(0.0, GROUND_RADIUS, 0.0)

        return elevationsDeg.map { elevationDeg ->
            val elevation = Math.toRadians(elevationDeg)
            val view = doubleArrayOf(cos(elevation) * cos(azimuth), sin(elevation), cos(elevation) * sin(azimuth))
            physics.toLinearRgb(inscattered(physics, camera, view, sunDirection))
        }
    }

    private fun luminance(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    /** Light scattered towards the camera along [view], per band of [physics] (before exposure). */
    private fun inscattered(physics: Physics, camera: DoubleArray, view: DoubleArray, sunDirection: DoubleArray): DoubleArray {
        val bands = physics.bands
        val result = DoubleArray(bands)
        val tExitTop = intersectSphere(camera, view, TOP_RADIUS) ?: return result
        if (tExitTop <= 0.0) return result

        val segmentLength = tExitTop / SAMPLES
        var tRay = segmentLength * 0.5

        val originRadius = length(camera)
        val downward = dot(camera, view) / originRadius < 0.0
        val startHeight = originRadius - GROUND_RADIUS
        val startCos = (dot(camera, view) / originRadius).coerceIn(-1.0, 1.0)
        val cameraToSpace = transmittance(physics, startHeight, acos(abs(startCos)))

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

            val toSpace = transmittance(physics, height, viewAngle)
            val light = transmittance(physics, height, sunAngle)
            val higher = if (physics.multiple) multipleScattering(physics, height, 90.0 - Math.toDegrees(sunAngle)) else null
            val densityR = exp(-height / RAYLEIGH_SCALE_HEIGHT)
            val densityM = exp(-height / MIE_SCALE_HEIGHT)

            for (k in 0 until bands) {
                val cameraToSample = if (downward) toSpace[k] / cameraToSpace[k] else cameraToSpace[k] / toSpace[k]
                val scatteringR = physics.rayleigh[k] * densityR
                val scatteringM = MIE_SCATTER * densityM
                val single = light[k] * (scatteringR * phaseR + scatteringM * phaseM)
                val higherOrders = if (higher == null) 0.0 else higher[k] * (scatteringR + scatteringM)
                result[k] += cameraToSample * (single + higherOrders) * segmentLength
            }
            tRay += segmentLength
        }
        for (k in 0 until bands) result[k] *= physics.sun[k]
        return result
    }

    /**
     * Light of second and higher scattering orders arriving at [height] (per unit sun irradiance,
     * folded with an isotropic phase function), for the sun at [sunAltitudeDeg] there; bilinear in
     * the [Physics.multipleScatteringTable].
     */
    private fun multipleScattering(physics: Physics, height: Double, sunAltitudeDeg: Double): DoubleArray {
        val table = physics.multipleScatteringTable
        val altitudes = table.size / MS_HEIGHTS
        val a = ((sunAltitudeDeg - MS_MIN_SUN_ALTITUDE) / MS_SUN_STEP).coerceIn(0.0, altitudes - 1.0)
        val h = sqrt((height / (TOP_RADIUS - GROUND_RADIUS)).coerceIn(0.0, 1.0)) * (MS_HEIGHTS - 1)
        val a0 = floor(a).toInt().coerceAtMost(altitudes - 2)
        val h0 = floor(h).toInt().coerceAtMost(MS_HEIGHTS - 2)
        val ta = a - a0
        val th = h - h0
        fun at(i: Int, j: Int) = table[i * MS_HEIGHTS + j]
        return DoubleArray(physics.bands) { k ->
            val low = at(a0, h0)[k] + (at(a0 + 1, h0)[k] - at(a0, h0)[k]) * ta
            val high = at(a0, h0 + 1)[k] + (at(a0 + 1, h0 + 1)[k] - at(a0, h0 + 1)[k]) * ta
            low + (high - low) * th
        }
    }

    /** Evenly spread directions (Fibonacci sphere) for [multipleScatteringAt]. */
    private val sphereDirections: List<DoubleArray> = List(MS_DIRECTIONS) { i ->
        val y = 1.0 - 2.0 * (i + 0.5) / MS_DIRECTIONS
        val r = sqrt(1.0 - y * y)
        val phi = i * Math.PI * (3.0 - sqrt(5.0))
        doubleArrayOf(r * cos(phi), y, r * sin(phi))
    }

    /**
     * Hillaire's multiple scattering at [height] for the sun at [sunAltitudeDeg]: the second order
     * L2 (sunlight scattered once, isotropically, plus light from the sunlit ground, arriving from
     * all directions) and the share f of light that is scattered back to the point, summed over
     * all orders as L2 / (1 - f).
     */
    private fun multipleScatteringAt(physics: Physics, height: Double, sunAltitudeDeg: Double): DoubleArray {
        val bands = physics.bands
        val origin = doubleArrayOf(0.0, GROUND_RADIUS + height, 0.0)
        val a = Math.toRadians(sunAltitudeDeg)
        val sun = doubleArrayOf(cos(a), sin(a), 0.0)
        val second = DoubleArray(bands)
        val transfer = DoubleArray(bands)
        for (direction in sphereDirections) {
            val ground = intersectGround(origin, direction)
            val distance = ground ?: intersectSphere(origin, direction, TOP_RADIUS) ?: continue
            val step = distance / MS_SAMPLES
            val opticalDepth = DoubleArray(bands)
            for (i in 0 until MS_SAMPLES) {
                val pos = DoubleArray(3) { k -> origin[k] + direction[k] * (i + 0.5) * step }
                val h = length(pos) - GROUND_RADIUS
                val densityR = exp(-h / RAYLEIGH_SCALE_HEIGHT)
                val densityM = exp(-h / MIE_SCALE_HEIGHT)
                val densityO = ozoneDensity(h)
                val toSun = sunTransmittance(physics, pos, sun)
                for (k in 0 until bands) {
                    val extinction = physics.rayleigh[k] * densityR + MIE_EXTINCTION * densityM + physics.ozone[k] * densityO
                    val scattering = physics.rayleigh[k] * densityR + MIE_SCATTER * densityM
                    val toOrigin = exp(-(opticalDepth[k] + extinction * step / 2))
                    second[k] += toOrigin * scattering * toSun[k] * ISOTROPIC_PHASE * step
                    transfer[k] += toOrigin * scattering * step
                    opticalDepth[k] += extinction * step
                }
            }
            if (ground != null) {
                val pos = DoubleArray(3) { k -> origin[k] + direction[k] * ground }
                val incidence = dot(pos, sun) / length(pos)
                if (incidence > 0) {
                    val toSun = sunTransmittance(physics, pos, sun)
                    for (k in 0 until bands) second[k] += exp(-opticalDepth[k]) * toSun[k] * incidence * GROUND_ALBEDO / Math.PI
                }
            }
        }
        return DoubleArray(bands) { k ->
            val l2 = second[k] / sphereDirections.size
            val f = transfer[k] / sphereDirections.size
            l2 / (1.0 - f)
        }
    }

    /** Transmittance from [pos] towards the sun along [sun]; zero where the ground is in the way. */
    private fun sunTransmittance(physics: Physics, pos: DoubleArray, sun: DoubleArray): DoubleArray {
        if (intersectGround(pos, sun) != null) return DoubleArray(physics.bands)
        val radius = length(pos)
        return transmittance(physics, radius - GROUND_RADIUS, acos((dot(pos, sun) / radius).coerceIn(-1.0, 1.0)))
    }

    /** Transmittance from [height] to the top of the atmosphere along a ray at [angle] from the vertical, per band. */
    private fun transmittance(physics: Physics, height: Double, angle: Double): DoubleArray {
        val origin = doubleArrayOf(0.0, GROUND_RADIUS + height, 0.0)
        val direction = doubleArrayOf(sin(angle), cos(angle), 0.0)
        val distance = intersectSphere(origin, direction, TOP_RADIUS)
        if (distance == null || distance == 0.0) return DoubleArray(physics.bands) { 1.0 }

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
            odOzone += ozoneDensity(h) * segmentLength
            t += segmentLength
        }
        return DoubleArray(physics.bands) { k ->
            exp(-(physics.rayleigh[k] * odRayleigh + MIE_EXTINCTION * odMie + physics.ozone[k] * odOzone))
        }
    }

    /** Ozone: triangular profile centered at 25 km, 30 km wide, relative to its peak. */
    private fun ozoneDensity(height: Double) = 1.0 - min(abs(height - 25e3) / 15e3, 1.0)

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

    /** Distance along [d] from [p] (on or above the ground) to the ground; null if it misses it. */
    private fun intersectGround(p: DoubleArray, d: DoubleArray): Double? {
        val b = dot(p, d)
        if (b >= 0) return null
        val discriminant = b * b - (dot(p, p) - GROUND_RADIUS * GROUND_RADIUS)
        if (discriminant < 0) return null
        return (-b - sqrt(discriminant)).coerceAtLeast(0.0)
    }

    private fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    private fun length(v: DoubleArray) = sqrt(dot(v, v))
    private fun normalize(v: DoubleArray): DoubleArray {
        val l = length(v).takeIf { it > 0 } ?: 1.0
        return doubleArrayOf(v[0] / l, v[1] / l, v[2] / l)
    }
}
