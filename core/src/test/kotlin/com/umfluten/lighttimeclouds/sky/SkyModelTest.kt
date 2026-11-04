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
    fun `haze darkens the horizon at sunset under a brighter band`() {
        // Looking flat through the haze near the ground, which the low sun hardly reaches any more.
        val luminance = { c: Int -> 0.2126 * (c shr 16 and 255) + 0.7152 * (c shr 8 and 255) + 0.0722 * (c and 255) }
        val sunset = SkyModel.ringGradient(0.0, doubleArrayOf(0.0, 15.0))
        assertTrue(luminance(sunset[0]) < 0.7 * luminance(sunset[1]))
        // With the sun high, the hazy horizon is the brightest part of the sky.
        val day = SkyModel.ringGradient(40.0, doubleArrayOf(0.0, 15.0, 30.0))
        assertTrue(luminance(day[0]) > luminance(day[1]) && luminance(day[1]) > luminance(day[2]))
    }

    @Test
    fun `averaged sky is blue in the blue hour, not purple`() {
        // Three wavelengths and single scattering leave the upper sky black-purple here; the
        // spectrum (ozone absorbing broadly) and multiple scattering make it a dark blue.
        val red = { c: Int -> c shr 16 and 255 }
        val green = { c: Int -> c shr 8 and 255 }
        val blue = { c: Int -> c and 255 }
        for (sun in listOf(-4.0, -6.0)) {
            val top = SkyModel.ringGradient(sun, doubleArrayOf(0.0, 30.0)).last()
            val blueSky = blue(top) > 1.3 * red(top) && blue(top) > green(top) && blue(top) > 10
            assertTrue("sun at $sun°: ${Integer.toHexString(top)}", blueSky)
        }
    }

    @Test
    fun `daytime zenith is a deeper blue than the horizon`() {
        val sky = SkyGradientTable.colors(40.0, topElevationDeg = 80.0, stops = 2)
        val red = { c: Int -> c shr 16 and 255 }
        val blue = { c: Int -> c and 255 }
        assertTrue(blue(sky.last()) - red(sky.last()) > blue(sky.first()) - red(sky.first()))
    }

    @Test
    fun `sun disc is near white high up, golden, orange and red towards the horizon`() {
        val red = { c: Int -> c shr 16 and 255 }
        val green = { c: Int -> c shr 8 and 255 }
        val blue = { c: Int -> c and 255 }
        val high = SkyModel.sunDisc(60.0)
        assertTrue("high sun near white: %06x".format(high), green(high) > 235 && blue(high) > 200)
        val low = SkyModel.sunDisc(3.0)
        assertTrue("sun 3° high orange: %06x".format(low), green(low) in 100..180 && blue(low) < 60)
        val horizon = SkyModel.sunDisc(0.0)
        assertTrue("sun at the horizon red through the haze: %06x".format(horizon), green(horizon) < 60 && blue(horizon) < 40)
        // Far brighter than the display: shown at full brightness, its hue kept, getting warmer towards the horizon.
        val altitudes = listOf(0.0, 1.0, 3.0, 10.0, 30.0, 90.0)
        for (altitude in altitudes) assertEquals(255, red(SkyModel.sunDisc(altitude)))
        val greens = altitudes.map { green(SkyModel.sunDisc(it)) }
        assertEquals(greens.sorted(), greens)
    }

    @Test
    fun `table is dark at night and interpolates between rows and elevations`() {
        // Night: only the night sky's own faint glow, dark and a little blue as the rods see it.
        assertTrue(SkyGradientTable.colors(-30.0, 45.0, 16).all { c -> listOf(16, 8, 0).all { c shr it and 255 <= 64 } && (c and 255) >= (c shr 16 and 255) })
        assertArrayEquals(SkyGradientTable.colors(-18.0, 45.0, 16), SkyGradientTable.colors(-40.0, 45.0, 16))

        val red = { c: Int -> c shr 16 and 255 }
        val a = SkyGradientTable.colors(2.0, 45.0, 16)
        val b = SkyGradientTable.colors(2.5, 45.0, 16)
        val mid = SkyGradientTable.colors(2.25, 45.0, 16)
        for (i in a.indices) assertEquals((red(a[i]) + red(b[i])) / 2.0, red(mid[i]).toDouble(), 1.0)

        // Three and five stops up to 2° (nearly evenly spaced this low): every other one matches, the rest lie between.
        val coarse = SkyGradientTable.colors(2.0, 2.0, 3)
        val fine = SkyGradientTable.colors(2.0, 2.0, 5)
        for (i in coarse.indices) assertEquals(coarse[i], fine[2 * i])
        assertEquals((red(coarse[0]) + red(coarse[1])) / 2.0, red(fine[1]).toDouble(), 1.0)
    }

    /** Checks every 20th row of the sun table generated at build time. */
    @Test
    fun `bundled sun table matches the model`() {
        val rows = (0 until SunTable.ROWS step 20)
        val computed = SunTable.compute(rows)
        val bundled = SunTable::class.java.getResourceAsStream(SunTable.RESOURCE)!!.use { it.readBytes() }
        val rowBytes = bundled.size / SunTable.ROWS
        for (row in rows) {
            val range = row * rowBytes until (row + 1) * rowBytes
            assertArrayEquals("row $row", computed.sliceArray(range), bundled.sliceArray(range))
        }
    }

    @Test
    fun `aureole is brightest next to the sun and fades out completely`() {
        val brightness = { c: Int -> (c shr 16 and 255) + (c shr 8 and 255) + (c and 255) }
        for (altitude in listOf(2.0, 10.0, 40.0)) {
            val aureole = SunTable.aureole(altitude)
            assertTrue("sun at $altitude°", (1 until aureole.size).all { brightness(aureole[it]) <= brightness(aureole[it - 1]) })
            assertTrue(brightness(aureole.first()) > 300)
            assertEquals(0, aureole.last())
        }
    }

    @Test
    fun `circumsolar ratio is a few percent at most, more for a low sun`() {
        val high = SkyModel.circumsolarRatio(40.0)
        val low = SkyModel.circumsolarRatio(10.0)
        assertTrue("$high, $low", high in 0.001..0.02 && low in high..0.05)
    }

    @Test
    fun `ring projection is the original's perspective`() {
        // The original's view up to 37.5°: half way up the image is atan(tan(37.5°) / 2) ≈ 21°.
        assertEquals(20.98, SkyProjection.elevation(0.5, SkyModel.TOP_ELEVATION_DEG), 0.01)
        for (f in listOf(-0.3, 0.0, 0.25, 1.0, 1.4)) {
            assertEquals(f, SkyProjection.fraction(SkyProjection.elevation(f, 60.0), 60.0), 1e-9)
        }
        // The top elevation is capped, the zenith stays finite.
        assertEquals(SkyProjection.elevation(1.0, 90.0), SkyProjection.MAX_TOP_ELEVATION_DEG, 1e-9)
        assertTrue(SkyProjection.fraction(90.0, 45.0).isFinite())
    }
}
