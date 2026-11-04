package com.umfluten.lighttimeclouds.sky

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * An urban aerosol as measured by AERONET at Créteil near Paris (Dubovik et al. 2002, table 1):
 * a bimodal lognormal volume size distribution
 * dV/d ln r = Σ C_i / (√(2π) σ_i) · exp(−(ln r − ln r_i)² / (2σ_i²)), whose parameters grow with
 * the optical thickness [tau440] at 440 nm, and a refractive index of 1.40 + 0.009i, taken as
 * independent of the wavelength. From these, by [Mie] for each wavelength of [Spectrum]: the
 * column's optical thickness for extinction and scattering, and the phase function.
 */
internal class Aerosol(val tau440: Double) {

    private class Mode(val volume: Double, val radius: Double, val sigma: Double) {
        /** dV/d ln r (µm³/µm²) at [r] (µm). */
        fun dvdlnr(r: Double): Double {
            val z = (ln(r) - ln(radius)) / sigma
            return volume / (sqrt(2 * PI) * sigma) * exp(-0.5 * z * z)
        }
    }

    // Créteil–Paris (1999): volume (µm³/µm²), volume median radius (µm), σ of ln r.
    private val modes = listOf(
        Mode(volume = 0.01 + 0.12 * tau440, radius = 0.11 + 0.13 * tau440, sigma = 0.43),
        Mode(volume = 0.01 + 0.05 * tau440, radius = 2.76 + 0.48 * tau440, sigma = 0.79),
    )

    /** Scattering angles (radians) of the phase function table, dense near 0° for the aureole. */
    internal val phaseAngles = DoubleArray(PHASE_ANGLES) { j ->
        val s = j.toDouble() / (PHASE_ANGLES - 1)
        PI * s * s
    }

    /** Optical thickness of the column for extinction, per [Spectrum.wavelengthsNm]. */
    val extinctionDepth: DoubleArray

    /** Optical thickness of the column for scattering, per [Spectrum.wavelengthsNm]. */
    val scatteringDepth: DoubleArray

    /** Phase function (1/sr, ∫ P dΩ = 1) per wavelength at [phaseAngles]. */
    internal val phase: Array<DoubleArray>

    /** Asymmetry parameter ⟨cos θ⟩ per wavelength. */
    val asymmetry: DoubleArray

    init {
        val wavelengths = Spectrum.wavelengthsNm
        val mu = DoubleArray(PHASE_ANGLES) { cos(phaseAngles[it]) }
        val step = (ln(MAX_RADIUS) - ln(MIN_RADIUS)) / (RADII - 1)
        val radii = DoubleArray(RADII) { exp(ln(MIN_RADIUS) + it * step) }
        extinctionDepth = DoubleArray(wavelengths.size)
        scatteringDepth = DoubleArray(wavelengths.size)
        asymmetry = DoubleArray(wavelengths.size)
        phase = Array(wavelengths.size) { k ->
            val lambda = wavelengths[k] / 1000.0
            val waveNumber = 2 * PI / lambda
            val s11 = DoubleArray(PHASE_ANGLES)
            var extinction = 0.0
            var scattering = 0.0
            for (r in radii) {
                // Particles per µm² of the column per d ln r: volume over the volume of one sphere.
                val number = modes.sumOf { it.dvdlnr(r) } / (4.0 / 3.0 * PI * r * r * r) * step
                val result = Mie.sphere(waveNumber * r, REFRACTIVE_REAL, REFRACTIVE_IMAGINARY, mu)
                extinction += number * PI * r * r * result.qExt
                scattering += number * PI * r * r * result.qSca
                for (j in s11.indices) s11[j] += number * result.s11[j]
            }
            extinctionDepth[k] = extinction
            scatteringDepth[k] = scattering
            // Σ N·s11 integrates to k²·Σ N·C_sca over the sphere: normalize to ∫ P dΩ = 1.
            val p = DoubleArray(PHASE_ANGLES) { s11[it] / (waveNumber * waveNumber * scattering) }
            asymmetry[k] = integrate { j -> p[j] * mu[j] }
            p
        }
    }

    /** ∫ f dΩ over the sphere, trapezoidal in the scattering angle of the table. */
    internal fun integrate(f: (Int) -> Double): Double {
        var sum = 0.0
        for (j in 1 until PHASE_ANGLES) {
            val a = phaseAngles[j - 1]
            val b = phaseAngles[j]
            sum += 0.5 * (f(j - 1) * sin(a) + f(j) * sin(b)) * (b - a)
        }
        return 2 * PI * sum
    }

    /** Phase function at wavelength index [band] for the scattering angle [angle] (radians), interpolated linearly. */
    internal fun phaseAt(band: Int, angle: Double): Double {
        val s = sqrt((angle / PI).coerceIn(0.0, 1.0)) * (PHASE_ANGLES - 1)
        val j = s.toInt().coerceAtMost(PHASE_ANGLES - 2)
        val a = phaseAngles[j]
        val t = ((angle - a) / (phaseAngles[j + 1] - a)).coerceIn(0.0, 1.0)
        val p = phase[band]
        return p[j] + (p[j + 1] - p[j]) * t
    }

    private companion object {
        const val REFRACTIVE_REAL = 1.40
        const val REFRACTIVE_IMAGINARY = 0.009
        const val MIN_RADIUS = 0.01
        const val MAX_RADIUS = 20.0
        const val RADII = 160
        const val PHASE_ANGLES = 721
    }
}
