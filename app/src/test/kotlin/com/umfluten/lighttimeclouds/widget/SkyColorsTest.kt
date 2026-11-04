package com.umfluten.lighttimeclouds.widget

import android.graphics.Color
import com.umfluten.lighttimeclouds.sky.SkyGradientTable
import org.junit.Assert.assertArrayEquals
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
    fun `blue hour is blue`() {
        // Sun at -8°: the sky above the faint horizon glow is blue.
        val blueHour = ClockFaceRenderer.skyColors(-8.0, TOP)
        assertTrue(blueHour.takeLast(blueHour.size / 2).all { Color.blue(it) > Color.red(it) })
    }

    @Test
    fun `sky colors are the model's, opaque`() {
        val day = ClockFaceRenderer.skyColors(30.0, TOP)
        val model = SkyGradientTable.colors(30.0, TOP, day.size)
        assertArrayEquals(IntArray(model.size) { model[it] or (0xFF shl 24) }, day)
    }

    @Test
    fun `night is the night sky's own faint glow, dark and bluish`() {
        val night = ClockFaceRenderer.skyColors(-25.0, 80.0)
        assertArrayEquals(ClockFaceRenderer.skyColors(-18.0, 80.0), night)
        assertTrue(night.all { luminance(it) < 60 && Color.blue(it) >= Color.red(it) })
    }
}
