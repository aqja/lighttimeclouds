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

    private fun luminance(c: Int) = 0.2126 * Color.red(c) + 0.7152 * Color.green(c) + 0.0722 * Color.blue(c)

    @Test
    fun `blue hour is blue and the red glow comes later`() {
        val red = { c: Int -> Color.red(c) }
        val blue = { c: Int -> Color.blue(c) }
        // Sun at -8°: the sky is blue-violet, the horizon not yet red.
        val blueHour = ClockFaceRenderer.skyColors(-8.0)
        assertTrue(blueHour.all { blue(it) > red(it) })
        // Sun at -1°: a warm horizon, still a cooler sky above.
        val dawn = ClockFaceRenderer.skyColors(-1.0)
        assertTrue(red(dawn.first()) > blue(dawn.first()))
        assertTrue(luminance(dawn.first()) > luminance(blueHour.first()))
    }

    @Test
    fun `daylight is the physical model alone`() {
        val day = ClockFaceRenderer.skyColors(30.0)
        val model = SkyGradientTable.colors(30.0)
        for (i in day.indices) assertTrue(day[i] and 0xFFFFFF == model[i])
    }

    @Test
    fun `night is the night color of the other ring styles`() {
        val night = ClockFaceRenderer.ringColor(-30.0, WidgetSettings(1, ringStyle = RingStyle.BANDS))
        assertTrue(ClockFaceRenderer.skyColors(-25.0).all { it == night })
        assertTrue(SunMoonCalculator.classify(-25.0) == SunMoonCalculator.SkyState.NIGHT)
    }
}
