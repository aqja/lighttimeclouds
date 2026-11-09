package com.umfluten.lighttimeclouds.widget

import android.graphics.Bitmap
import android.os.Parcel
import android.util.SizeF
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

    /** A full-width widget on a phone, in portrait and in landscape. */
    private val portrait = SizeF(379f, 360f)
    private val landscape = SizeF(670f, 207f)

    private fun viewsFor(bitmap: Bitmap, sizeDp: SizeF): RemoteViews {
        val context = RuntimeEnvironment.getApplication()
        val settings = WidgetSettings(widgetId = 1, tapAction = TapAction.SPLIT)
        return ClockWidgetProvider.viewsFor(context, 1, settings, bitmap, null, WidgetSizing.sizePx(context, sizeDp))
    }

    @Test
    fun `each orientation gets areas for the clock as large as it is shown there`() {
        val context = RuntimeEnvironment.getApplication()
        val bitmap = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888)
        fun squareWidth(sizeDp: SizeF) = viewsFor(bitmap, sizeDp).apply(context, FrameLayout(context))
            .findViewById<View>(R.id.widget_tap_area_square).layoutParams.width
        val ratio = squareWidth(landscape).toFloat() / squareWidth(portrait)
        assertEquals(207f / 360f, ratio, 0.01f)
    }

    @Test
    fun `the views for both orientations send the clock bitmap only once`() {
        val bitmap = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        fun parcelSize(views: RemoteViews) = Parcel.obtain().run {
            views.writeToParcel(this, 0)
            dataSize().also { recycle() }
        }
        val one = parcelSize(viewsFor(bitmap, portrait))
        val both = parcelSize(ClockWidgetProvider.viewsPerLayout(listOf(portrait, landscape)) { viewsFor(bitmap, it) })
        val pixels = 200 * 200 * 4
        assertTrue("the bitmap's pixels are in the parcel: $one bytes", one > pixels)
        assertTrue("one layout $one bytes, both $both bytes", both < one + pixels / 2)
    }

    @Test
    fun `the split stays the split where it is available`() {
        assertEquals(TapAction.SPLIT, TapActionResolver.effective(TapAction.SPLIT))
    }

    @Test
    @Config(sdk = [30])
    fun `before Android 12 the split acts like the clock app, without tap areas`() {
        assertEquals(TapAction.SYSTEM_CLOCK, TapActionResolver.effective(TapAction.SPLIT))
        assertEquals(TapAction.OWN_APP, TapActionResolver.effective(TapAction.OWN_APP))

        val context = RuntimeEnvironment.getApplication()
        val shown = remoteViews(TapAction.SPLIT).apply(context, FrameLayout(context))
        assertEquals(View.GONE, shown.findViewById<View>(R.id.widget_tap_area_square).visibility)
        assertTrue(shown.findViewById<View>(R.id.widget_root).hasOnClickListeners())
    }

    @Test
    fun `the rings begin further in with the weather ring on either side`() {
        assertEquals(0.82f, innerRingEdge(WidgetSettings(widgetId = 1, showWeather = false)), 0f)
        for (position in WeatherRingPosition.entries) {
            assertEquals(0.75f, innerRingEdge(WidgetSettings(widgetId = 1, showWeather = true, weatherRingPosition = position)), 0f)
        }
    }
}
