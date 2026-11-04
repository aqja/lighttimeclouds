package com.umfluten.lighttimeclouds.sky

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class MieTest {

    /** Cosines of [n] angles from 0 to 180° and the trapezoid weights for ∫ f dΩ over them. */
    private fun grid(n: Int): Pair<DoubleArray, DoubleArray> {
        val theta = DoubleArray(n) { PI * it / (n - 1) }
        val weights = DoubleArray(n) { i ->
            val h = PI / (n - 1)
            2 * PI * sin(theta[i]) * if (i == 0 || i == n - 1) h / 2 else h
        }
        return DoubleArray(n) { cos(theta[it]) } to weights
    }

    @Test
    fun `matches the example run of Bohren and Huffman`() {
        // BHMIE's example: sphere of radius 0.525 µm, refractive index 1.55, at 0.6328 µm.
        val x = 2 * PI * 0.525 / 0.6328
        val result = Mie.sphere(x, 1.55, 0.0, doubleArrayOf(-1.0))
        assertEquals(3.10543, result.qSca, 1e-4)
        assertEquals(3.10543, result.qExt, 1e-4)
        // Backscatter efficiency 4|S1(180°)|²/x² = 2.92534.
        assertEquals(2.92534, 4 * result.s11[0] / (x * x), 1e-4)
    }

    @Test
    fun `scattered light integrates to the scattering efficiency`() {
        val (mu, weights) = grid(4001)
        for ((x, k) in listOf(0.5 to 0.0, 3.0 to 0.01, 12.0 to 0.003)) {
            val result = Mie.sphere(x, 1.4, k, mu)
            val integral = result.s11.indices.sumOf { result.s11[it] * weights[it] }
            assertEquals("x = $x", result.qSca, integral / (PI * x * x), result.qSca * 1e-3)
            if (k == 0.0) assertEquals(result.qExt, result.qSca, 1e-9)
        }
    }

    @Test
    fun `small spheres scatter as Rayleigh predicts`() {
        val x = 0.01
        val m2 = 1.5 * 1.5
        val expected = 8.0 / 3.0 * x.pow(4) * ((m2 - 1) / (m2 + 2)).pow(2)
        assertEquals(expected, Mie.sphere(x, 1.5, 0.0, doubleArrayOf(1.0)).qSca, expected * 1e-3)
    }
}
