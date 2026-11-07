package com.umfluten.lighttimeclouds.widget

import android.view.View
import android.widget.FrameLayout
import android.widget.RemoteViews
import com.umfluten.lighttimeclouds.R
import com.umfluten.lighttimeclouds.data.TapAction
import com.umfluten.lighttimeclouds.data.WeatherRingPosition
import com.umfluten.lighttimeclouds.data.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TapAreasTest {

    private val radius = 100f
    private val sizes = TapActionResolver.tapAreaSizes(radius)

    private fun isInsideAnArea(x: Float, y: Float) =
        sizes.any { x <= it.width / 2 && y <= it.height / 2 }

    @Test
    fun `the areas nearly reach the circle along both axes`() {
        assertTrue(isInsideAnArea(radius * 0.95f, 0f))
        assertTrue(isInsideAnArea(0f, radius * 0.95f))
    }

    @Test
    fun `the corners lie on the circle, so nothing reaches onto the ring`() {
        sizes.forEach { assertEquals(radius, hypot(it.width / 2, it.height / 2), 0.01f) }
    }

    @Test
    fun `no direction falls short of the circle by more than a quarter`() {
        for (degrees in 0..90) {
            val angle = Math.toRadians(degrees.toDouble())
            val distance = radius * 3 / 4
            assertTrue(
                "at $degrees°",
                isInsideAnArea((distance * cos(angle)).toFloat(), (distance * sin(angle)).toFloat()),
            )
        }
    }

    private fun remoteViews(tapAction: TapAction): RemoteViews {
        val context = RuntimeEnvironment.getApplication()
        return RemoteViews(context.packageName, R.layout.widget_clock).also {
            TapActionResolver.bind(context, it, 1, WidgetSettings(widgetId = 1, tapAction = tapAction), clockSidePx = 400)
        }
    }

    @Test
    fun `the areas appear with the split and go again on the same views, as a launcher updates them`() {
        val context = RuntimeEnvironment.getApplication()
        val shown = remoteViews(TapAction.SPLIT).apply(context, FrameLayout(context))
        val area = shown.findViewById<View>(R.id.widget_tap_area_square)
        assertEquals(View.VISIBLE, area.visibility)
        assertTrue(area.hasOnClickListeners())

        remoteViews(TapAction.OWN_APP).reapply(context, shown)
        assertEquals(View.GONE, area.visibility)
    }

    @Test
    fun `the rings begin further in with the weather ring on either side`() {
        assertEquals(0.82f, innerRingEdge(WidgetSettings(widgetId = 1, showWeather = false)), 0f)
        for (position in WeatherRingPosition.entries) {
            assertEquals(0.75f, innerRingEdge(WidgetSettings(widgetId = 1, showWeather = true, weatherRingPosition = position)), 0f)
        }
    }
}
