package com.umfluten.lighttimeclouds.widget

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WidgetSizingTest {

    /** A full-width widget on a phone, as a launcher reports it: portrait 379 × 360 dp, landscape 670 × 207 dp. */
    private fun fullWidthBounds() = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 379)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 670)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 207)
        putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 360)
    }

    @Test
    fun `clock fills the portrait height, not the landscape height`() {
        val options = fullWidthBounds().apply {
            putParcelableArrayList(
                AppWidgetManager.OPTION_APPWIDGET_SIZES,
                arrayListOf(SizeF(378.7f, 360f), SizeF(669.7f, 206.9f)),
            )
        }
        assertEquals(360f, WidgetSizing.clockSideDp(options), 0.01f)
    }

    @Test
    fun `without the list of sizes the bounds give portrait and landscape`() {
        assertEquals(360f, WidgetSizing.clockSideDp(fullWidthBounds()), 0.01f)
    }

    @Test
    fun `nothing reported falls back to the declared default size`() {
        assertEquals(110f, WidgetSizing.clockSideDp(Bundle()), 0.01f)
    }
}
