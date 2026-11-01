package com.umfluten.lighttimeclouds.sky

import kotlin.math.exp
import kotlin.math.pow

/**
 * Spectral data for [SkyModel]: wavelengths from 360 to 830 nm in 10 nm steps, with the
 * extraterrestrial solar irradiance and the ozone absorption cross-sections as in the demo of
 * "Precomputed Atmospheric Scattering" by Eric Bruneton (2017 version, BSD-3-Clause license; the
 * irradiance from ASTM G-173, the cross-sections from the IUP Bremen ozone reference spectra), and
 * the conversion of a spectrum to linear sRGB.
 */
internal object Spectrum {

    val wavelengthsNm = DoubleArray(48) { 360.0 + 10.0 * it }
    private const val STEP_NM = 10.0

    /** Solar irradiance at the top of the atmosphere, W/(m²·nm), per [wavelengthsNm]. */
    val solarIrradiance = doubleArrayOf(
        1.11776, 1.14259, 1.01249, 1.14716, 1.72765, 1.73054, 1.6887, 1.61253,
        1.91198, 2.03474, 2.02042, 2.02212, 1.93377, 1.95809, 1.91686, 1.8298,
        1.8685, 1.8931, 1.85149, 1.8504, 1.8341, 1.8345, 1.8147, 1.78158, 1.7533,
        1.6965, 1.68194, 1.64654, 1.6048, 1.52143, 1.55622, 1.5113, 1.474, 1.4482,
        1.41018, 1.36775, 1.34188, 1.31429, 1.28303, 1.26758, 1.2367, 1.2082,
        1.18737, 1.14683, 1.12362, 1.1058, 1.07124, 1.04992,
    )

    /** Ozone absorption cross-section, m² per molecule, per [wavelengthsNm]. */
    private val ozoneCrossSection = doubleArrayOf(
        1.18e-27, 2.182e-28, 2.818e-28, 6.636e-28, 1.527e-27, 2.763e-27, 5.52e-27,
        8.451e-27, 1.582e-26, 2.316e-26, 3.669e-26, 4.924e-26, 7.752e-26, 9.016e-26,
        1.48e-25, 1.602e-25, 2.139e-25, 2.755e-25, 3.091e-25, 3.5e-25, 4.266e-25,
        4.672e-25, 4.398e-25, 4.701e-25, 5.019e-25, 4.305e-25, 3.74e-25, 3.215e-25,
        2.662e-25, 2.238e-25, 1.852e-25, 1.473e-25, 1.209e-25, 9.423e-26, 7.455e-26,
        6.566e-26, 5.105e-26, 4.15e-26, 4.228e-26, 3.237e-26, 2.451e-26, 2.801e-26,
        2.534e-26, 1.624e-26, 1.465e-26, 2.078e-26, 1.383e-26, 7.105e-27,
    )

    /** Peak ozone number density (1/m³): 300 Dobson units spread over the triangular profile 30 km wide. */
    private const val OZONE_PEAK_DENSITY = 300.0 * 2.687e20 / 15e3

    /** Rayleigh scattering coefficient (1/m) per [wavelengthsNm]; 5.802e-6 at 680 nm as in the RGB model. */
    val rayleighScattering = DoubleArray(wavelengthsNm.size) { 1.24062e-6 / (wavelengthsNm[it] / 1000.0).pow(4) }

    /** Ozone absorption coefficient (1/m) at the profile's peak, per [wavelengthsNm]. */
    val ozoneAbsorption = DoubleArray(wavelengthsNm.size) { ozoneCrossSection[it] * OZONE_PEAK_DENSITY }

    /**
     * Linear sRGB of [radiance] (per [wavelengthsNm]), via CIE 1931 XYZ, white-balanced so that
     * the sunlight above the atmosphere ([solarIrradiance]) is (1, 1, 1).
     */
    fun toLinearSrgb(radiance: DoubleArray): DoubleArray {
        val rgb = unbalancedSrgb(radiance)
        return DoubleArray(3) { rgb[it] / sunSrgb[it] }
    }

    private val sunSrgb: DoubleArray by lazy { unbalancedSrgb(solarIrradiance) }

    private fun unbalancedSrgb(spectrum: DoubleArray): DoubleArray {
        var x = 0.0
        var y = 0.0
        var z = 0.0
        for (i in spectrum.indices) {
            val l = wavelengthsNm[i]
            x += spectrum[i] * cieX(l) * STEP_NM
            y += spectrum[i] * cieY(l) * STEP_NM
            z += spectrum[i] * cieZ(l) * STEP_NM
        }
        return doubleArrayOf(
            3.2404542 * x - 1.5371385 * y - 0.4985314 * z,
            -0.9692660 * x + 1.8760108 * y + 0.0415560 * z,
            0.0556434 * x - 0.2040259 * y + 1.0572252 * z,
        )
    }

    // CIE 1931 color matching functions, multi-lobe fit from "Simple Analytic Approximations to the
    // CIE XYZ Color Matching Functions" (Wyman, Sloan, Shirley, 2013).
    private fun cieX(l: Double) = 1.056 * lobe(l, 599.8, 37.9, 31.0) + 0.362 * lobe(l, 442.0, 16.0, 26.7) -
        0.065 * lobe(l, 501.1, 20.4, 26.2)
    private fun cieY(l: Double) = 0.821 * lobe(l, 568.8, 46.9, 40.5) + 0.286 * lobe(l, 530.9, 16.3, 31.1)
    private fun cieZ(l: Double) = 1.217 * lobe(l, 437.0, 11.8, 36.0) + 0.681 * lobe(l, 459.0, 26.0, 13.8)

    private fun lobe(l: Double, mean: Double, sigmaBelow: Double, sigmaAbove: Double): Double {
        val t = (l - mean) / if (l < mean) sigmaBelow else sigmaAbove
        return exp(-0.5 * t * t)
    }
}
