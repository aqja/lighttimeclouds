package com.umfluten.lighttimeclouds.sky

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkyModelTest {

    private fun rgb(r: Int, g: Int, b: Int) = (r shl 16) or (g shl 8) or b

    @Test
    fun `matches the original Horizon implementation`() {
        // Horizon's renderGradient (32 stops) for these sun altitudes: horizon and top stop.
        val reference = mapOf(
            0.0 to (rgb(143, 35, 2) to rgb(59, 48, 62)),
            3.0 to (rgb(202, 144, 40) to rgb(81, 88, 114)),
            10.0 to (rgb(221, 210, 176) to rgb(97, 128, 171)),
            60.0 to (rgb(228, 231, 230) to rgb(123, 169, 213)),
            -12.0 to (0 to 0),
        )
        for ((altitude, expected) in reference) {
            val gradient = SkyModel.gradient(altitude, 32)
            assertEquals("horizon at $altitude°", expected.first, gradient.first())
            assertEquals("top at $altitude°", expected.second, gradient.last())
        }
    }

    /** Checks every 12th row of the table generated at build time (computing all of it takes seconds). */
    @Test
    fun `bundled table matches the model`() {
        val rows = (0 until SkyGradientTable.ROWS step 12)
        val computed = SkyGradientTable.compute(rows)
        val bundled = SkyGradientTable::class.java.getResourceAsStream(SkyGradientTable.RESOURCE)!!.use { it.readBytes() }
        val rowBytes = SkyGradientTable.STOPS * 3
        for (row in rows) {
            val range = row * rowBytes until (row + 1) * rowBytes
            assertArrayEquals("row $row", computed.sliceArray(range), bundled.sliceArray(range))
        }
    }

    @Test
    fun `averaged sky keeps the red dawn glow late and faint`() {
        val red = { c: Int -> c shr 16 and 255 }
        // Deep twilight: hardly any glow left even at the horizon.
        assertTrue(red(SkyModel.averagedGradient(-10.0, 16).first()) < 40)
        // Shortly before sunrise: a clear red-orange horizon under a darker sky.
        val dawn = SkyModel.averagedGradient(-2.0, 16)
        assertTrue(red(dawn.first()) > 150 && red(dawn.first()) > red(dawn.last()))
    }

    @Test
    fun `table is dark at night and interpolates between rows`() {
        assertTrue(SkyGradientTable.colors(-30.0).all { it == 0 })
        assertArrayEquals(SkyGradientTable.colors(-18.0), SkyGradientTable.colors(-40.0))

        val a = SkyGradientTable.colors(2.0)
        val b = SkyGradientTable.colors(2.5)
        val mid = SkyGradientTable.colors(2.25)
        val red = { c: Int -> c shr 16 and 255 }
        for (i in a.indices) assertEquals((red(a[i]) + red(b[i])) / 2.0, red(mid[i]).toDouble(), 1.0)
    }
}
