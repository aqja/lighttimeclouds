package com.umfluten.lighttimeclouds.widget

import android.graphics.Color
import com.umfluten.lighttimeclouds.astro.SunMoonCalculator
import com.umfluten.lighttimeclouds.data.RingStyle
import com.umfluten.lighttimeclouds.data.WidgetSettings
import com.umfluten.lighttimeclouds.sky.SkyGradientTable
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SkyColorsTest {

    private companion object {
        const val TOP = 45.0
    }

    private fun luminance(c: Int) = 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)

    @Test
    fun `blue hour is blue and the red glow comes later`() {
        val red = { c: Int -> Color.red(c) }
        val blue = { c: Int -> Color.blue(c) }
        // Sun at -8°: the sky above the faint horizon glow is blue.
        val blueHour = ClockFaceRenderer.skyColors(-8.0, TOP)
        assertTrue(blueHour.takeLast(blueHour.size / 2).all { blue(it) > red(it) })
        // Sun at -1°: a warm horizon, still a cooler sky above.
        val dawn = ClockFaceRenderer.skyColors(-1.0, TOP)
        assertTrue(red(dawn.first()) > blue(dawn.first()))
        assertTrue(luminance(dawn.first()) > luminance(blueHour.first()))
    }

    @Test
    fun `daylight is the physical model, hardly lifted by the night color`() {
        val day = ClockFaceRenderer.skyColors(30.0, TOP)
        val model = SkyGradientTable.colors(30.0, TOP, day.size)
        for (i in day.indices) {
            for (shift in listOf(16, 8, 0)) {
                val lift = (day[i] shr shift and 255) - (model[i] shr shift and 255)
                assertTrue(lift in 0..10)
            }
        }
    }

    @Test
    fun `night is the night color of the other ring styles`() {
        val night = ClockFaceRenderer.ringColor(-30.0, WidgetSettings(1, ringStyle = RingStyle.BANDS))
        assertTrue(ClockFaceRenderer.skyColors(-25.0, 80.0).all { it == night })
        assertTrue(SunMoonCalculator.classify(-25.0) == SunMoonCalculator.SkyState.NIGHT)
    }
}
