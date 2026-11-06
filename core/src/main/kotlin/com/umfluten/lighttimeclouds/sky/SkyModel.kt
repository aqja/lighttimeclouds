package com.umfluten.lighttimeclouds.sky

import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * Physically based sky colors (Rayleigh and Mie scattering, ozone absorption) for a given sun
 * altitude, as a vertical gradient upward from the horizon, the sun's disc and aureole, the moon's
 * disc, and the sky's light for [MoonlitSky].
 *
 * Over the visible [Spectrum], with multiple scattering, with the aerosols of an urban [Aerosol]
 * (Mie scattering), averaged over all view azimuths (the ring has no compass direction) and seen
 * by a human eye adapted to the whole sky ([ringGradient]): cones and rods, the hue shift of dim
 * light and the brightness the eye perceives, mapped onto a display. The physics is described in
 * docs/PHYSICS.md, the vision in docs/PHYSIOLOGY.md.
 *
 * Extends a port of "Horizon" by Daniel Lazaro (https://github.com/dnlzro/horizon, MIT License), whose
 * implementation derives from "Production Sky Rendering" by Andrew Helmer
 * (https://www.shadertoy.com/view/slSXRW, MIT License as stated by its author in the shader's
 * comments) and whose physical model and parameters come from "A Scalable and Production Ready Sky
 * and Atmosphere Rendering Technique" (Sébastien Hillaire, 2020); the multiple scattering follows
 * section 5.5 of that paper.
 */
object SkyModel {

    // Altitude density distribution
    private const val RAYLEIGH_SCALE_HEIGHT = 8e3
    private const val MIE_SCALE_HEIGHT = 1.2e3

    private const val GROUND_RADIUS = 6_360e3
    private const val TOP_RADIUS = 6_460e3

    /** Share of the light reaching the ground that it reflects (diffusely); for multiple scattering. */
    private const val GROUND_ALBEDO = 0.3

    /**
     * [multipleScatteringTable]: sun altitudes (local, at the scattering point) from
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

    /** Integration steps along the line of sight to the sun ([sunDisc]): enough for the long, low path at the horizon. */
    private const val SUN_SAMPLES = 512

    private const val GAMMA = 2.2

    /** View azimuths averaged for the ring; mirror-symmetric about the sun, so half are computed. */
    private const val AVERAGE_AZIMUTHS = 12

    private const val ISOTROPIC_PHASE = 1.0 / (4.0 * Math.PI)

    /**
     * Aerosol optical thickness at 440 nm of the atmosphere: a fairly clear day, below the
     * mean of 0.26 that AERONET measured at Créteil near Paris in 1999 and within the range its size
     * distribution was derived from (0.1 to 0.9; Dubovik et al. 2002).
     */
    private const val AEROSOL_TAU_440 = 0.2

    private val BANDS = Spectrum.wavelengthsNm.size

    /** The [Aerosol] of optical thickness [AEROSOL_TAU_440], spread over the height with [MIE_SCALE_HEIGHT]. */
    private val aerosol: Aerosol by lazy { Aerosol(AEROSOL_TAU_440) }

    /** Aerosol scattering (1/m at the ground) per band. */
    private val mieScattering: DoubleArray by lazy { DoubleArray(BANDS) { aerosol.scatteringDepth[it] / MIE_SCALE_HEIGHT } }

    /** Aerosol extinction, scattering plus absorption (1/m at the ground), per band. */
    private val mieExtinction: DoubleArray by lazy { DoubleArray(BANDS) { aerosol.extinctionDepth[it] / MIE_SCALE_HEIGHT } }

    /** [multipleScatteringAt] per sun altitude (outer) and height (inner, square-root spaced). */
    private val multipleScatteringTable: Array<DoubleArray> by lazy {
        val altitudes = ((90.0 - MS_MIN_SUN_ALTITUDE) / MS_SUN_STEP).roundToInt() + 1
        Array(altitudes * MS_HEIGHTS) { index ->
            val altitude = MS_MIN_SUN_ALTITUDE + index / MS_HEIGHTS * MS_SUN_STEP
            val f = (index % MS_HEIGHTS).toDouble() / (MS_HEIGHTS - 1)
            multipleScatteringAt(f * f * (TOP_RADIUS - GROUND_RADIUS), altitude)
        }
    }

    // --- Seeing the sky ------------------------------------------------------------------

    /**
     * Luminance (cd/m²) of the natural night sky without moon: 22.0 mag/arcsec², as Falchi et al.
     * (2016) assume for the background away from the Milky Way at low solar activity. Taken as
     * the same everywhere in the sky and with the sun's spectrum (stars and zodiacal light), not
     * the airglow's lines.
     */
    private const val NIGHT_SKY_LUMINANCE = 174e-6

    /** Spectral radiance of the natural night sky: the sun's spectrum scaled to [NIGHT_SKY_LUMINANCE]. */
    private val nightSky: DoubleArray by lazy {
        val scale = NIGHT_SKY_LUMINANCE / Spectrum.photopicLuminance(Spectrum.solarIrradiance)
        DoubleArray(Spectrum.solarIrradiance.size) { Spectrum.solarIrradiance[it] * scale }
    }

    /**
     * The display the sky is shown on and the eye looking at it, as in the revised operator of
     * Tumblin and Rushmeier (Tumblin, Hodgins and Guenter 1999): maximum luminance (cd/m²), the
     * luminance the eye is adapted to there (a little below half the maximum, which Ward and
     * Ferwerda et al. take), and the display's contrast ratio, as of a common LCD panel. The
     * contrast sets how far the night lies below the day: at 1/√[DISPLAY_CONTRAST] of the
     * adaptation level.
     */
    private const val DISPLAY_MAX_LUMINANCE = 100.0
    private const val DISPLAY_ADAPTATION = 40.0
    private const val DISPLAY_CONTRAST = 1000.0

    /** Elevations (degrees) at which the whole sky's mean luminance is sampled. */
    private const val HEMISPHERE_ELEVATIONS = 12

    /**
     * What the eye has adapted to with the sun at [sunAltitudeDeg]: the mean luminance (cd/m²) of
     * the whole sky, weighted by solid angle.
     */
    internal fun skyLuminance(sunAltitudeDeg: Double): Double = skyLuminances.getOrPut(sunAltitudeDeg) {
        val n = HEMISPHERE_ELEVATIONS
        val elevations = DoubleArray(n) { (it + 0.5) * 90.0 / n }
        val hemisphere = skySpectra(sunAltitudeDeg, elevations)
        val weights = elevations.map { cos(Math.toRadians(it)) }
        hemisphere.indices.sumOf { Spectrum.photopicLuminance(hemisphere[it]) * weights[it] } / weights.sum()
    }

    /** [skyLuminance] by sun altitude: the moon's table asks for the same few again and again. */
    private val skyLuminances = ConcurrentHashMap<Double, Double>()

    /** [averagedSpectra] with the night sky's glow added. */
    private fun skySpectra(sunAltitudeDeg: Double, elevationsDeg: DoubleArray): List<DoubleArray> =
        averagedSpectra(sunAltitudeDeg, elevationsDeg).map { s -> DoubleArray(s.size) { s[it] + nightSky[it] } }

    /**
     * Sky colors (packed 0xRRGGBB) at [elevationsDeg] above the horizon for the sun at
     * [sunAltitudeDeg]: the atmosphere averaged over all view azimuths (the glow on the
     * sun's side included, without the sun itself), with the night sky's glow, as an eye adapted
     * to the whole sky sees it ([perceive]).
     */
    fun ringGradient(sunAltitudeDeg: Double, elevationsDeg: DoubleArray): IntArray {
        val adaptation = skyLuminance(sunAltitudeDeg)
        return skySpectra(sunAltitudeDeg, elevationsDeg).map { perceive(it, adaptation) }.toIntArray()
    }

    /**
     * The spectral [radiance] (W/(m²·sr·nm) per [Spectrum.wavelengthsNm]) as packed sRGB on the
     * display, for an eye adapted to the luminance [adaptation] (cd/m²):
     *  - the hue from the cones, with the rods' signal entering their channels as Cao et al.
     *    (2008) measured ([rodIntrusion]), which shifts it towards blue as the light fades;
     *  - colors fading as the cones lose sensitivity (Wanat and Mantiuk 2014, [saturationFactor]),
     *    and growing stronger in bright light (the Hunt effect, [huntFactor]);
     *  - the brightness from Stevens' brightness law, matched between the sky and the display by
     *    the revised operator of Tumblin and Rushmeier (Tumblin, Hodgins and Guenter 1999);
     *  - highlights compressed by the ACES curve on the brightest channel, with the others in
     *    proportion so that it keeps the hue, then gamma-encoded.
     *
     * Rods and fading colors go by [colorLuminance]: for the sky that of the whole sky, for a small
     * bright body in a dark sky its own, which the cones see in full. [hunt] is the [huntFactor].
     */
    private fun perceive(
        radiance: DoubleArray,
        adaptation: Double,
        colorLuminance: Double = adaptation,
        hunt: Double = huntFactor(adaptation),
    ): Int = perceiveLight(light(radiance), adaptation, colorLuminance, hunt)

    /** Values in a [light] array. */
    internal const val LIGHT_CHANNELS = 5

    /** Index of the rods' signal in a [light] array, after the three of linear RGB. */
    private const val LIGHT_RODS = 3

    /** Index of the photopic luminance (cd/m²) in a [light] array. */
    internal const val LIGHT_LUMINANCE = 4

    /**
     * All [perceive] needs of a spectral [radiance]: linear RGB, the rods' signal and the photopic
     * luminance. All linear in the radiance, so the light of two skies adds up like their radiance.
     */
    private fun light(radiance: DoubleArray): DoubleArray {
        val rgb = Spectrum.toLinearSrgb(radiance)
        return doubleArrayOf(rgb[0], rgb[1], rgb[2], Spectrum.rodExcitation(radiance), Spectrum.photopicLuminance(radiance))
    }

    /** [perceive] of [light]. */
    internal fun perceiveLight(
        light: DoubleArray,
        adaptation: Double,
        colorLuminance: Double = adaptation,
        hunt: Double = huntFactor(adaptation),
    ): Int {
        val (k1, k2) = rodIntrusion(colorLuminance)
        val rods = light[LIGHT_RODS]
        val intrusion = Spectrum.linearSrgbOfCones(doubleArrayOf(k1 * rods, k1 * rods, k2 * rods))
        val seen = DoubleArray(3) { light[it] + intrusion[it] }
        val seenLuminance = luminance(seen).coerceAtLeast(1e-300)
        val saturation = saturationFactor(colorLuminance) / saturationFactor(DISPLAY_ADAPTATION) * hunt

        val world = stevensGamma(adaptation)
        val display = stevensGamma(DISPLAY_ADAPTATION)
        val m = DISPLAY_CONTRAST.pow((world / display - 1.0) / 2.0)
        val brightness = DISPLAY_ADAPTATION * m * (light[LIGHT_LUMINANCE] / adaptation).pow(world / display)

        val linear = DoubleArray(3) { (seen[it].coerceAtLeast(0.0) / seenLuminance).pow(saturation) * brightness / DISPLAY_MAX_LUMINANCE }
        val brightest = linear.max().coerceAtLeast(1e-300)
        val toned = DoubleArray(3) { linear[it] * aces(brightest) / brightest }
        fun channel(i: Int): Int = (toned[i].pow(1.0 / GAMMA) * 255.0).roundToInt().coerceIn(0, 255)
        return (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
    }

    /**
     * Exponent of Stevens' brightness law for an eye adapted to [adaptation] cd/m², as the revised
     * operator of Tumblin and Rushmeier uses it (Tumblin, Hodgins and Guenter 1999).
     */
    private fun stevensGamma(adaptation: Double): Double =
        if (adaptation > 100.0) 2.655 else 1.855 + 0.4 * log10(adaptation + 2.3e-5)

    /**
     * Weights (k₁ for the L and M cones' channels, k₂ for the S cones') with which the rods'
     * signal enters the cones' channels at a luminance of [luminance] cd/m², as measured by Cao,
     * Pokorny, Smith and Zele (2008) and tabulated by Wanat and Mantiuk (2014): none at 10 cd/m²,
     * (0.0173, 0.0101) at 0.62 and (0.173, 0.357) at 0.1; linear in between, held below 0.1.
     */
    private fun rodIntrusion(luminance: Double): Pair<Double, Double> {
        val l = luminance.coerceIn(0.1, 10.0)
        return if (l >= 0.62) {
            val t = (10.0 - l) / (10.0 - 0.62)
            0.0173 * t to 0.0101 * t
        } else {
            val t = (0.62 - l) / (0.62 - 0.1)
            0.0173 + (0.173 - 0.0173) * t to 0.0101 + (0.357 - 0.0101) * t
        }
    }

    /**
     * Saturation matching factor s(Y) = Y / (Y + 0.108) of Wanat and Mantiuk (2014, eq. 26), held
     * below 0.1 cd/m² like [rodIntrusion].
     */
    private fun saturationFactor(luminance: Double): Double {
        val l = luminance.coerceAtLeast(0.1)
        return l / (l + 0.108)
    }

    /**
     * How much stronger colors must be on the display to look as colorful as under an eye adapted
     * to [adaptation] cd/m², the Hunt effect. Kim, Weyrich and Kautz (2009, eq. 10 and 11) measured
     * colorfulness up to [HUNT_MAX_WHITE] cd/m² as chroma times 0.11 log₁₀ L_w + 0.61, with chroma
     * the 0.62nd power of the opponent signals. The factor is that term for the sky's mean
     * luminance as L_w over the display's white, to the inverse power, and multiplies the exponent
     * of [saturationFactor]. Up to the display's white it is 1: colors fade by [saturationFactor].
     */
    private fun huntFactor(adaptation: Double): Double {
        fun scale(white: Double) = 0.11 * log10(white) + 0.61
        return (scale(adaptation.coerceIn(DISPLAY_MAX_LUMINANCE, HUNT_MAX_WHITE)) / scale(DISPLAY_MAX_LUMINANCE)).pow(1.0 / 0.62)
    }

    /** Highest white (cd/m²) at which Kim, Weyrich and Kautz (2009) measured colorfulness. */
    private const val HUNT_MAX_WHITE = 16860.0

    /** ACES filmic curve (Narkowicz 2016). */
    private fun aces(c: Double) = ((c * (2.51 * c + 0.03)) / (c * (2.43 * c + 0.59) + 0.14)).coerceIn(0.0, 1.0)

    // --- The sun ---------------------------------------------------------------------------

    /** Mean angular radius of the sun's disc. */
    const val SUN_SEMI_DIAMETER_DEG = 0.2666

    /**
     * Color (packed 0xRRGGBB) of the sun's disc with the sun at [altitudeDeg] (at least the
     * horizon), as the eye sees it ([perceive]), like the sky: sunlight above the atmosphere
     * ([Spectrum.solarIrradiance]) after its way through the atmosphere along the line of
     * sight, spread over the disc's solid angle, in front of the sky at the sun's altitude. Far
     * brighter than the display, it shows at full brightness in its own hue: nearly white high up,
     * then golden, orange and at the horizon red, as the long path through the air filters it.
     */
    fun sunDisc(altitudeDeg: Double): Int {
        val altitude = altitudeDeg.coerceIn(0.0, 90.0)
        val transmitted = transmittance(0.0, Math.toRadians(90.0 - altitude), SUN_SAMPLES)
        val solidAngle = Math.PI * Math.toRadians(SUN_SEMI_DIAMETER_DEG).pow(2)
        val sky = skySpectra(altitude, doubleArrayOf(altitude)).first()
        val radiance = DoubleArray(BANDS) { k -> Spectrum.solarIrradiance[k] * transmitted[k] / solidAngle + sky[k] }
        // Colorfulness was measured up to [HUNT_MAX_WHITE]; the disc is many orders of magnitude brighter.
        return perceive(radiance, skyLuminance(altitude), hunt = 1.0)
    }

    /**
     * The aureole around the sun at [sunAltitudeDeg] (at least the horizon): at each of
     * [anglesDeg] from the sun's center (ascending, the last far enough out for the aureole to
     * have faded), the color (packed 0xRRGGBB) that, laid over the ring's color at the sun's
     * altitude in "screen" blending (1 − (1 − a)(1 − b) per channel), gives the sky there as the
     * eye sees it ([perceive]): the ring's azimuth-averaged sky plus the excess of the sky in the
     * sun's direction (along the almucantar, the circle of the sun's altitude) over that at the
     * last angle. Black where there is none, so it ends without an edge.
     */
    fun aureole(sunAltitudeDeg: Double, anglesDeg: DoubleArray): IntArray {
        val altitudeDeg = sunAltitudeDeg.coerceIn(0.0, 89.0)
        val sky = almucantar(altitudeDeg, anglesDeg)
        val adaptation = skyLuminance(altitudeDeg)
        val ring = skySpectra(altitudeDeg, doubleArrayOf(altitudeDeg)).first()
        val background = perceive(ring, adaptation)
        val last = sky.last()
        return risingInward(sky.map { s ->
            screenOver(background, perceive(DoubleArray(s.size) { ring[it] + max(0.0, s[it] - last[it]) }, adaptation))
        }.toIntArray())
    }

    /**
     * The [aureole], from the body outward, with no channel weaker than further out. Next to the
     * body the light lies far above the display, where the curve keeping the hue shows only its
     * color, a little bluer than further out, while to the eye it just grows brighter.
     */
    internal fun risingInward(aureole: IntArray): IntArray {
        val rising = aureole.copyOf()
        for (i in rising.lastIndex - 1 downTo 0) rising[i] = brighterChannels(rising[i], rising[i + 1])
        return rising
    }

    /** Per channel the brighter of the packed colors [a] and [b]. */
    private fun brighterChannels(a: Int, b: Int): Int {
        var color = 0
        for (shift in intArrayOf(16, 8, 0)) color = color or (max(a shr shift and 255, b shr shift and 255) shl shift)
        return color
    }

    /** The color that, laid over [background] in "screen" blending, gives [target], or as near as it can. */
    internal fun screenOver(background: Int, target: Int): Int {
        var color = 0
        for (shift in intArrayOf(16, 8, 0)) {
            val b = (background shr shift and 255) / 255.0
            val t = (target shr shift and 255) / 255.0
            val a = if (b >= 1.0) 0.0 else (1.0 - (1.0 - t) / (1.0 - b)).coerceIn(0.0, 1.0)
            color = color or ((a * 255.0).roundToInt() shl shift)
        }
        return color
    }

    /** [light] of the sky with its glow ([skySpectra]) with a body at [altitudeDeg] at each of [elevationsDeg]. */
    internal fun skyLight(altitudeDeg: Double, elevationsDeg: DoubleArray): List<DoubleArray> =
        skySpectra(altitudeDeg, elevationsDeg).map(::light)

    /** [light] of what the [aureole] adds to the sky around a body at [altitudeDeg], at each of [anglesDeg], as bright as the sun. */
    internal fun aureoleLight(altitudeDeg: Double, anglesDeg: DoubleArray): List<DoubleArray> {
        val sky = almucantar(altitudeDeg.coerceIn(0.0, 89.0), anglesDeg)
        val last = sky.last()
        return sky.map { s -> light(DoubleArray(s.size) { max(0.0, s[it] - last[it]) }) }
    }

    /** Spectral radiance of the sky at [anglesDeg] from the sun at [altitudeDeg], along the almucantar. */
    private fun almucantar(altitudeDeg: Double, anglesDeg: DoubleArray): List<DoubleArray> {
        val altitude = Math.toRadians(altitudeDeg)
        val sun = doubleArrayOf(cos(altitude), sin(altitude), 0.0)
        val camera = doubleArrayOf(0.0, GROUND_RADIUS, 0.0)
        return anglesDeg.map { angleDeg ->
            // cos θ = sin²a + cos²a cos φ
            val cosPhi = ((cos(Math.toRadians(angleDeg)) - sin(altitude).pow(2)) / cos(altitude).pow(2)).coerceIn(-1.0, 1.0)
            val phi = acos(cosPhi)
            val view = doubleArrayOf(cos(altitude) * cos(phi), sin(altitude), cos(altitude) * sin(phi))
            inscattered(camera, view, sun)
        }
    }

    /**
     * Share of the light from within [limitDeg] of the sun's center (2.5° as in solar-energy work)
     * that comes from outside its disc, in luminance (circumsolar ratio), with the sun at
     * [altitudeDeg].
     */
    internal fun circumsolarRatio(altitudeDeg: Double, limitDeg: Double = 2.5): Double {
        val angles = DoubleArray(41) { SUN_SEMI_DIAMETER_DEG + (limitDeg - SUN_SEMI_DIAMETER_DEG) * it / 40 }
        val sky = almucantar(altitudeDeg, angles).map(Spectrum::photopicLuminance)
        var circumsolar = 0.0
        for (i in 1 until angles.size) {
            val a = Math.toRadians(angles[i - 1])
            val b = Math.toRadians(angles[i])
            circumsolar += 0.5 * (sky[i - 1] * sin(a) + sky[i] * sin(b)) * (b - a) * 2 * Math.PI
        }
        val transmitted = transmittance(0.0, Math.toRadians(90.0 - altitudeDeg), SUN_SAMPLES)
        val direct = Spectrum.photopicLuminance(DoubleArray(BANDS) { Spectrum.solarIrradiance[it] * transmitted[it] })
        return circumsolar / (circumsolar + direct)
    }

    // --- The moon --------------------------------------------------------------------------

    /** Mean angular radius of the moon's disc, at the mean distance [moonMagnitude] refers to. */
    private const val MOON_SEMI_DIAMETER_DEG = 0.2590

    /** Apparent V magnitude of the sun, against which [moonMagnitude] sets the moonlight. */
    private const val SUN_MAGNITUDE = -26.74

    /** The thinnest crescent the moon's light is computed for, so the lit part's solid angle stays above zero. */
    private const val MOON_MIN_FRACTION = 0.01

    /**
     * Diameter (degrees) of the spot the eye is taken to adapt to when looking at something (Ward
     * Larson, Rushmeier and Piatko 1997). An assumption: measured pooling is far narrower.
     */
    private const val FOVEA_DEG = 1.0

    /**
     * Share of the [FOVEA_DEG] spot the moon's disc fills, counted for the whole disc whatever its
     * phase. A choice, not physiology: the eye pools more narrowly (Vangorp et al. 2015) and adapts
     * far less to a crescent. It stays because its result fits: at that measured adaptation the
     * curves of Stevens and Stevens (1963) put all phases at about the same brightness, as this does.
     */
    private const val MOON_FOVEA_SHARE = (2.0 * MOON_SEMI_DIAMETER_DEG / FOVEA_DEG) * (2.0 * MOON_SEMI_DIAMETER_DEG / FOVEA_DEG)

    /**
     * Apparent V magnitude of the moon at its mean distance, at [phaseAngleDeg] (0 at full moon),
     * outside the atmosphere: the phase law Krisciunas and Schaefer (1991) use, from the full
     * moon's −12.73 in Allen's Astrophysical Quantities.
     */
    private fun moonMagnitude(phaseAngleDeg: Double): Double =
        -12.73 + 0.026 * phaseAngleDeg + 4e-9 * phaseAngleDeg.pow(4)

    /** Moonlight outside the atmosphere with the share [fraction] of the disc lit, relative to sunlight. */
    internal fun moonlightRatio(fraction: Double): Double {
        val phaseAngle = Math.toDegrees(acos(2.0 * fraction.coerceIn(0.0, 1.0) - 1.0))
        return 10.0.pow(-0.4 * (moonMagnitude(phaseAngle) - SUN_MAGNITUDE))
    }

    /**
     * Color (packed 0xRRGGBB) of the lit part of the moon at [moonAltitudeDeg] (at least the
     * horizon), lit by the share [fraction], with the sun at [sunAltitudeDeg], as the eye sees it
     * ([perceive]): sunlight reflected at the brightness of [moonMagnitude], spread over the lit
     * part's solid angle (so independent of the moon's distance, which changes its light and its
     * size alike), after its way through the atmosphere, in front of the sky. By day it adds
     * little to the bright sky and stays pale; in the dark it is white high up and orange to red
     * near the horizon. The eye is adapted to the sky, moonlit too, and to the moon it looks at
     * ([MOON_FOVEA_SHARE]), so a crescent stays bright in a dark sky.
     */
    fun moonDisc(moonAltitudeDeg: Double, sunAltitudeDeg: Double, fraction: Double): Int =
        moonDiscs(moonAltitudeDeg, doubleArrayOf(sunAltitudeDeg), doubleArrayOf(fraction)).single()

    /** [moonDisc] for each of [sunAltitudesDeg] and, within each, each of [fractions]: the moon's way through the air is the same for all. */
    fun moonDiscs(moonAltitudeDeg: Double, sunAltitudesDeg: DoubleArray, fractions: DoubleArray): IntArray {
        val altitude = moonAltitudeDeg.coerceIn(0.0, 90.0)
        val transmitted = transmittance(0.0, Math.toRadians(90.0 - altitude), SUN_SAMPLES)
        val moonlitSky = skyLuminance(altitude)
        return sunAltitudesDeg.flatMap { sunAltitude ->
            val sky = skySpectra(sunAltitude, doubleArrayOf(altitude)).first()
            val moonlessSky = skyLuminance(sunAltitude)
            fractions.map { fraction ->
                val lit = fraction.coerceIn(MOON_MIN_FRACTION, 1.0)
                val ratio = moonlightRatio(lit)
                val solidAngle = lit * Math.PI * Math.toRadians(MOON_SEMI_DIAMETER_DEG).pow(2)
                val moon = DoubleArray(BANDS) { k -> Spectrum.solarIrradiance[k] * ratio * transmitted[k] / solidAngle }
                val skyAdaptation = moonlessSky + ratio * moonlitSky
                val adapted = skyAdaptation + MOON_FOVEA_SHARE * Spectrum.photopicLuminance(moon)
                moonColor(moon, sky, adapted, skyAdaptation)
            }
        }.toIntArray()
    }

    /**
     * The [moon]'s radiance in front of the [sky], seen with [adaptation]; see [moonDisc]. The
     * [huntFactor] goes by the sky, [skyAdaptation], the scene's white, not by the spot on the moon
     * the eye rests on. A moon fainter than the sky only adds to it: where looking at it would dim
     * a channel below the sky behind it, the sky's stays.
     */
    private fun moonColor(moon: DoubleArray, sky: DoubleArray, adaptation: Double, skyAdaptation: Double): Int {
        val radiance = DoubleArray(BANDS) { k -> moon[k] + sky[k] }
        val seen = perceive(radiance, adaptation, Spectrum.photopicLuminance(radiance), huntFactor(skyAdaptation))
        if (Spectrum.photopicLuminance(moon) >= Spectrum.photopicLuminance(sky)) return seen
        return brighterChannels(seen, perceive(sky, skyAdaptation))
    }

    // --- Scattering -------------------------------------------------------------------------

    /** Mean spectral radiance over [AVERAGE_AZIMUTHS] view azimuths at each of [elevationsDeg]. */
    private fun averagedSpectra(sunAltitudeDeg: Double, elevationsDeg: DoubleArray): List<DoubleArray> {
        val sum = List(elevationsDeg.size) { DoubleArray(BANDS) }
        val half = AVERAGE_AZIMUTHS / 2
        for (i in 0 until half) {
            val azimuth = (i + 0.5) * 360.0 / AVERAGE_AZIMUTHS
            spectralGradient(sunAltitudeDeg, elevationsDeg, azimuth).forEachIndexed { k, c ->
                for (j in c.indices) sum[k][j] += c[j] / half
            }
        }
        return sum
    }

    /** Spectral radiance of the inscattered light at each of [elevationsDeg], viewing [azimuthDeg] away from the sun's direction. */
    private fun spectralGradient(sunAltitudeDeg: Double, elevationsDeg: DoubleArray, azimuthDeg: Double): List<DoubleArray> {
        val altitude = Math.toRadians(sunAltitudeDeg)
        val azimuth = Math.toRadians(azimuthDeg)
        val sunDirection = doubleArrayOf(cos(altitude), sin(altitude), 0.0)
        val camera = doubleArrayOf(0.0, GROUND_RADIUS, 0.0)

        return elevationsDeg.map { elevationDeg ->
            val elevation = Math.toRadians(elevationDeg)
            val view = doubleArrayOf(cos(elevation) * cos(azimuth), sin(elevation), cos(elevation) * sin(azimuth))
            inscattered(camera, view, sunDirection)
        }
    }

    private fun luminance(c: DoubleArray) = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]

    /**
     * Spectral radiance scattered towards the camera along [view]. The steps grow with the square
     * of the distance, the first ones a few hundred meters long even for a view along the horizon,
     * where a flat view runs through the dense air and haze near the ground, and the optical depth
     * is summed along the way.
     */
    private fun inscattered(camera: DoubleArray, view: DoubleArray, sunDirection: DoubleArray): DoubleArray {
        val result = DoubleArray(BANDS)
        val tExitTop = intersectSphere(camera, view, TOP_RADIUS) ?: return result
        if (tExitTop <= 0.0) return result

        val sunViewAngle = acos(dot(sunDirection, view).coerceIn(-1.0, 1.0))
        val phaseR = rayleighPhase(sunViewAngle)
        val phaseM = DoubleArray(BANDS) { k -> aerosol.phaseAt(k, sunViewAngle) }
        val opticalDepth = DoubleArray(BANDS)

        for (i in 0 until SAMPLES) {
            val start = tExitTop * (i.toDouble() / SAMPLES).pow(2)
            val end = tExitTop * ((i + 1.0) / SAMPLES).pow(2)
            val tRay = (start + end) / 2
            val segmentLength = end - start
            val pos = DoubleArray(3) { k -> camera[k] + view[k] * tRay }
            val radius = length(pos)
            val up = DoubleArray(3) { k -> pos[k] / radius }
            val height = radius - GROUND_RADIUS

            val sunAngle = acos(dot(up, sunDirection).coerceIn(-1.0, 1.0))
            val light = transmittance(height, sunAngle)
            val higher = multipleScattering(height, 90.0 - Math.toDegrees(sunAngle))
            val densityR = exp(-height / RAYLEIGH_SCALE_HEIGHT)
            val densityM = exp(-height / MIE_SCALE_HEIGHT)
            val densityO = ozoneDensity(height)

            for (k in 0 until BANDS) {
                val scatteringR = Spectrum.rayleighScattering[k] * densityR
                val scatteringM = mieScattering[k] * densityM
                val extinction = Spectrum.rayleighScattering[k] * densityR + mieExtinction[k] * densityM + Spectrum.ozoneAbsorption[k] * densityO
                val cameraToSample = exp(-(opticalDepth[k] + extinction * segmentLength / 2))
                opticalDepth[k] += extinction * segmentLength
                val single = light[k] * (scatteringR * phaseR + scatteringM * phaseM[k])
                val higherOrders = higher[k] * (scatteringR + scatteringM)
                result[k] += cameraToSample * (single + higherOrders) * segmentLength
            }
        }
        for (k in 0 until BANDS) result[k] *= Spectrum.solarIrradiance[k]
        return result
    }

    /**
     * Light of second and higher scattering orders arriving at [height] (per unit sun irradiance,
     * folded with an isotropic phase function), for the sun at [sunAltitudeDeg] there; bilinear in
     * the [multipleScatteringTable].
     */
    private fun multipleScattering(height: Double, sunAltitudeDeg: Double): DoubleArray {
        val table = multipleScatteringTable
        val altitudes = table.size / MS_HEIGHTS
        val a = ((sunAltitudeDeg - MS_MIN_SUN_ALTITUDE) / MS_SUN_STEP).coerceIn(0.0, altitudes - 1.0)
        val h = sqrt((height / (TOP_RADIUS - GROUND_RADIUS)).coerceIn(0.0, 1.0)) * (MS_HEIGHTS - 1)
        val a0 = floor(a).toInt().coerceAtMost(altitudes - 2)
        val h0 = floor(h).toInt().coerceAtMost(MS_HEIGHTS - 2)
        val ta = a - a0
        val th = h - h0
        fun at(i: Int, j: Int) = table[i * MS_HEIGHTS + j]
        return DoubleArray(BANDS) { k ->
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
    private fun multipleScatteringAt(height: Double, sunAltitudeDeg: Double): DoubleArray {
        val origin = doubleArrayOf(0.0, GROUND_RADIUS + height, 0.0)
        val a = Math.toRadians(sunAltitudeDeg)
        val sun = doubleArrayOf(cos(a), sin(a), 0.0)
        val second = DoubleArray(BANDS)
        val transfer = DoubleArray(BANDS)
        for (direction in sphereDirections) {
            val ground = intersectGround(origin, direction)
            val distance = ground ?: intersectSphere(origin, direction, TOP_RADIUS) ?: continue
            val step = distance / MS_SAMPLES
            val opticalDepth = DoubleArray(BANDS)
            for (i in 0 until MS_SAMPLES) {
                val pos = DoubleArray(3) { k -> origin[k] + direction[k] * (i + 0.5) * step }
                val h = length(pos) - GROUND_RADIUS
                val densityR = exp(-h / RAYLEIGH_SCALE_HEIGHT)
                val densityM = exp(-h / MIE_SCALE_HEIGHT)
                val densityO = ozoneDensity(h)
                val toSun = sunTransmittance(pos, sun)
                for (k in 0 until BANDS) {
                    val extinction = Spectrum.rayleighScattering[k] * densityR + mieExtinction[k] * densityM + Spectrum.ozoneAbsorption[k] * densityO
                    val scattering = Spectrum.rayleighScattering[k] * densityR + mieScattering[k] * densityM
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
                    val toSun = sunTransmittance(pos, sun)
                    for (k in 0 until BANDS) second[k] += exp(-opticalDepth[k]) * toSun[k] * incidence * GROUND_ALBEDO / Math.PI
                }
            }
        }
        return DoubleArray(BANDS) { k ->
            val l2 = second[k] / sphereDirections.size
            val f = transfer[k] / sphereDirections.size
            l2 / (1.0 - f)
        }
    }

    /** Transmittance from [pos] towards the sun along [sun]; zero where the ground is in the way. */
    private fun sunTransmittance(pos: DoubleArray, sun: DoubleArray): DoubleArray {
        if (intersectGround(pos, sun) != null) return DoubleArray(BANDS)
        val radius = length(pos)
        return transmittance(radius - GROUND_RADIUS, acos((dot(pos, sun) / radius).coerceIn(-1.0, 1.0)))
    }

    /** Transmittance from [height] to the top of the atmosphere along a ray at [angle] from the vertical, per band. */
    private fun transmittance(height: Double, angle: Double, samples: Int = SAMPLES): DoubleArray {
        val origin = doubleArrayOf(0.0, GROUND_RADIUS + height, 0.0)
        val direction = doubleArrayOf(sin(angle), cos(angle), 0.0)
        val distance = intersectSphere(origin, direction, TOP_RADIUS)
        if (distance == null || distance == 0.0) return DoubleArray(BANDS) { 1.0 }

        val segmentLength = distance / samples
        var t = 0.5 * segmentLength
        var odRayleigh = 0.0
        var odMie = 0.0
        var odOzone = 0.0
        repeat(samples) {
            val x = origin[0] + direction[0] * t
            val y = origin[1] + direction[1] * t
            val h = sqrt(x * x + y * y) - GROUND_RADIUS
            odRayleigh += exp(-h / RAYLEIGH_SCALE_HEIGHT) * segmentLength
            odMie += exp(-h / MIE_SCALE_HEIGHT) * segmentLength
            odOzone += ozoneDensity(h) * segmentLength
            t += segmentLength
        }
        return DoubleArray(BANDS) { k ->
            exp(-(Spectrum.rayleighScattering[k] * odRayleigh + mieExtinction[k] * odMie + Spectrum.ozoneAbsorption[k] * odOzone))
        }
    }

    /** Ozone: triangular profile centered at 25 km, 30 km wide, relative to its peak. */
    private fun ozoneDensity(height: Double) = 1.0 - min(abs(height - 25e3) / 15e3, 1.0)

    private fun rayleighPhase(angle: Double): Double = 3.0 * (1.0 + cos(angle).pow(2)) / (16.0 * Math.PI)

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
}
