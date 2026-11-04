package com.umfluten.lighttimeclouds.sky

import kotlin.math.max

/**
 * Scattering of light by a homogeneous sphere (Lorenz-Mie theory), after the BHMIE algorithm of
 * Bohren and Huffman, "Absorption and Scattering of Light by Small Particles" (1983), appendix A:
 * the logarithmic derivative D_n(mx) by downward recurrence, the Riccati-Bessel functions by
 * upward recurrence, and the angular functions π_n and τ_n.
 */
internal object Mie {

    /**
     * Efficiencies for extinction and scattering, and [s11] = (|S₁|² + |S₂|²)/2 at each of the
     * scattering angles' cosines passed in; ∫ s11 dΩ = π x² [qSca].
     */
    class Result(val qExt: Double, val qSca: Double, val s11: DoubleArray)

    /**
     * The sphere of size parameter [x] = 2πr/λ and relative refractive index [mRe] + i·[mIm]
     * (absorbing for [mIm] > 0), at scattering angles with cosines [mu].
     */
    fun sphere(x: Double, mRe: Double, mIm: Double, mu: DoubleArray): Result {
        require(x > 0.0) { "size parameter must be positive" }
        val nStop = (x + 4.0 * cbrt(x) + 2.0).toInt()
        val y = Complex(mRe * x, mIm * x)
        val nMx = max(nStop, hypot(y.re, y.im).toInt()) + 15
        val m = Complex(mRe, mIm)

        // D_{n-1}(y) = n/y - 1/(D_n(y) + n/y), from D_nMx = 0.
        val d = arrayOfNulls<Complex>(nMx + 1)
        d[nMx] = Complex(0.0, 0.0)
        for (n in nMx downTo 1) {
            val ny = Complex(n.toDouble(), 0.0) / y
            d[n - 1] = ny - Complex(1.0, 0.0) / (d[n]!! + ny)
        }

        val s1Re = DoubleArray(mu.size)
        val s1Im = DoubleArray(mu.size)
        val s2Re = DoubleArray(mu.size)
        val s2Im = DoubleArray(mu.size)
        val piPrev = DoubleArray(mu.size)
        val piCurr = DoubleArray(mu.size) { 1.0 }

        var psi0 = cos(x)
        var psi1 = sin(x)
        var chi0 = -sin(x)
        var chi1 = cos(x)
        var xi1 = Complex(psi1, -chi1)
        var qSca = 0.0
        var qExt = 0.0
        for (n in 1..nStop) {
            val en = n.toDouble()
            val fn = (2.0 * en + 1.0) / (en * (en + 1.0))
            val psi = (2.0 * en - 1.0) * psi1 / x - psi0
            val chi = (2.0 * en - 1.0) * chi1 / x - chi0
            val xi = Complex(psi, -chi)
            val nx = Complex(en / x, 0.0)
            val da = d[n]!! / m + nx
            val db = m * d[n]!! + nx
            val a = (da * psi - Complex(psi1, 0.0)) / (da * xi - xi1)
            val b = (db * psi - Complex(psi1, 0.0)) / (db * xi - xi1)
            qSca += (2.0 * en + 1.0) * (a.abs2() + b.abs2())
            qExt += (2.0 * en + 1.0) * (a.re + b.re)

            for (j in mu.indices) {
                val p = piCurr[j]
                val t = en * mu[j] * p - (en + 1.0) * piPrev[j]
                s1Re[j] += fn * (a.re * p + b.re * t)
                s1Im[j] += fn * (a.im * p + b.im * t)
                s2Re[j] += fn * (a.re * t + b.re * p)
                s2Im[j] += fn * (a.im * t + b.im * p)
                // π_{n+1} = ((2n+1) μ π_n - (n+1) π_{n-1}) / n
                piCurr[j] = ((2.0 * en + 1.0) * mu[j] * p - (en + 1.0) * piPrev[j]) / en
                piPrev[j] = p
            }
            psi0 = psi1
            psi1 = psi
            chi0 = chi1
            chi1 = chi
            xi1 = Complex(psi1, -chi1)
        }
        val scale = 2.0 / (x * x)
        val s11 = DoubleArray(mu.size) { j ->
            0.5 * (s1Re[j] * s1Re[j] + s1Im[j] * s1Im[j] + s2Re[j] * s2Re[j] + s2Im[j] * s2Im[j])
        }
        return Result(qExt * scale, qSca * scale, s11)
    }

    private class Complex(val re: Double, val im: Double) {
        operator fun plus(o: Complex) = Complex(re + o.re, im + o.im)
        operator fun minus(o: Complex) = Complex(re - o.re, im - o.im)
        operator fun times(o: Complex) = Complex(re * o.re - im * o.im, re * o.im + im * o.re)
        operator fun times(s: Double) = Complex(re * s, im * s)
        operator fun div(o: Complex): Complex {
            val den = o.re * o.re + o.im * o.im
            return Complex((re * o.re + im * o.im) / den, (im * o.re - re * o.im) / den)
        }
        fun abs2() = re * re + im * im
    }
}
