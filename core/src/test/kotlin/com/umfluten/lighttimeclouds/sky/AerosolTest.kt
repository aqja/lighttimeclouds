package com.umfluten.lighttimeclouds.sky

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The ring's aerosol against the results stated in the physics documentation. */
class AerosolTest {

    private val aerosol = Aerosol(tau440 = 0.2)

    private fun band(wavelengthNm: Int) = Spectrum.wavelengthsNm.indexOfFirst { it == wavelengthNm.toDouble() }

    /** ∫ f(θ) P(θ) dΩ at [wavelengthNm], trapezoidal over the angles of the table. */
    private fun integrated(wavelengthNm: Int, f: (Double) -> Double): Double {
        val angles = aerosol.phaseAngles
        val phase = aerosol.phase[band(wavelengthNm)]
        fun integrand(j: Int) = f(angles[j]) * phase[j] * sin(angles[j])
        return 2 * PI * (1..angles.lastIndex).sumOf { j -> (integrand(j - 1) + integrand(j)) / 2 * (angles[j] - angles[j - 1]) }
    }

    @Test
    fun `the phase function is normalized`() {
        for (wavelength in listOf(440, 550, 670)) assertEquals(1.0, integrated(wavelength) { 1.0 }, 1e-3)
    }

    @Test
    fun `optical thickness, albedo and asymmetry come out as documented`() {
        fun depth(wavelength: Int) = aerosol.extinctionDepth[band(wavelength)]
        fun albedo(wavelength: Int) = aerosol.scatteringDepth[band(wavelength)] / depth(wavelength)
        fun asymmetry(wavelength: Int) = integrated(wavelength) { cos(it) }
        assertEquals(0.208, depth(440), 0.0005)
        assertEquals(0.136, depth(550), 0.0005)
        assertEquals(0.092, depth(670), 0.0005)
        assertEquals(0.92, albedo(440), 0.005)
        assertEquals(0.89, albedo(670), 0.005)
        assertEquals(0.67, asymmetry(440), 0.005)
        assertEquals(0.57, asymmetry(670), 0.005)
    }
}
