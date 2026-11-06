package com.umfluten.lighttimeclouds.sky

import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class PerceivedSkyTest {

    private val elevations = DoubleArray(24) { SkyProjection.elevation(it / 23.0, 45.0) }

    /** Mean displayed luminance (linear, 0..1) of the ring's colors. */
    private fun meanLuminance(colors: IntArray): Double = colors.map { c ->
        fun linear(v: Int) = (v / 255.0).pow(2.2)
        0.2126 * linear(c shr 16 and 255) + 0.7152 * linear(c shr 8 and 255) + 0.0722 * linear(c and 255)
    }.average()

    /**
     * From 2° below the horizon on, the ring only darkens into the night. Right after sunset the
     * eye's adaptation to the dimming sky may lift it a little (by at most 3 %).
     */
    @Test
    fun `the ring darkens through twilight into the night`() {
        val means = (0 downTo -20).associateWith { meanLuminance(SkyModel.ringGradient(it.toDouble(), elevations)) }
        for (alt in -3 downTo -20) {
            assertTrue("sun at $alt°: ${means[alt]} after ${means[alt + 1]}", means.getValue(alt) < means.getValue(alt + 1))
        }
        for (alt in -1 downTo -2) assertTrue("sun at $alt°", means.getValue(alt) <= means.getValue(0) * 1.03)
    }

    /** The whole sky's mean luminance, which the eye adapts to, against typical measured values. */
    @Test
    fun `sky luminance spans day to night as measured`() {
        val day = SkyModel.skyLuminance(40.0)
        val civil = SkyModel.skyLuminance(-6.0)
        val night = SkyModel.skyLuminance(-25.0)
        assertTrue("day $day", day in 2_000.0..15_000.0)
        assertTrue("end of civil twilight $civil", civil in 0.5..20.0)
        // Only the night sky's own glow (174 µcd/m²).
        assertTrue("night $night", night in 1.5e-4..2.5e-4)
    }
}
