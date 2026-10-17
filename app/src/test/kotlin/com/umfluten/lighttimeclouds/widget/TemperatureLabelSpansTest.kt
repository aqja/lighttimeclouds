package com.umfluten.lighttimeclouds.widget

import com.umfluten.lighttimeclouds.data.DisplayMode
import com.umfluten.lighttimeclouds.data.Place
import com.umfluten.lighttimeclouds.data.SwitchStrategyType
import com.umfluten.lighttimeclouds.data.WidgetSettings
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZonedDateTime

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class TemperatureLabelSpansTest {

    private val zone = Place.DEFAULT.zone
    private fun at(day: Int, hour: Int, minute: Int) = ZonedDateTime.of(2026, 10, day, hour, minute, 0, 0, zone)

    private fun span(settings: WidgetSettings, now: ZonedDateTime, angle: Double) =
        ClockFaceRenderer.temperatureLabelSpans(settings, now).single { it.angle == angle }

    private fun assertSpan(span: ClockFaceRenderer.LabelSpan, mid: ZonedDateTime, from: ZonedDateTime, to: ZonedDateTime) {
        assertEquals("mid", mid.toInstant(), span.mid)
        assertEquals("from", from.toInstant(), span.from)
        assertEquals("to", to.toInstant(), span.to)
    }

    private val trailing = WidgetSettings(
        widgetId = 1,
        switchStrategyType = SwitchStrategyType.TRAILING_OFFSET,
        trailingOffsetMinutes = 30,
    )

    @Test
    fun `trailing window - the label behind the seam shows the current hour, not the night 12 h later`() {
        // 14:40 with 30 min offset: the window runs 14:10 - 02:10, its seam sits at the "2:10" position.
        val now = at(17, 14, 40)
        // "2:30" position, just past the seam: 14:30 today, averaged from the window start on.
        assertSpan(span(trailing, now, 75.0), mid = at(17, 14, 30), from = at(17, 14, 10), to = at(17, 15, 0))
        // "1:30" position, before the seam: the end of the window, 01:30 tomorrow.
        assertSpan(span(trailing, now, 45.0), mid = at(18, 1, 30), from = at(18, 1, 0), to = at(18, 2, 0))
    }

    @Test
    fun `fixed switch times off the full hour cut the label at the window start`() {
        val settings = WidgetSettings(widgetId = 1, switchTime1Minutes = 6 * 60 + 30, switchTime2Minutes = 18 * 60 + 30)
        // Window 06:30 - 18:30; the label at "6:30" starts exactly at the seam.
        assertSpan(span(settings, at(17, 9, 0), 195.0), mid = at(17, 6, 30), from = at(17, 6, 30), to = at(17, 7, 0))
    }

    @Test
    fun `without a seam inside a segment the label covers the whole span between the numerals`() {
        val fixed = WidgetSettings(widgetId = 1)
        assertSpan(span(fixed, at(17, 14, 20), 45.0), mid = at(17, 13, 30), from = at(17, 13, 0), to = at(17, 14, 0))
        val fullDay = WidgetSettings(widgetId = 1, displayMode = DisplayMode.TWENTY_FOUR_HOUR)
        assertSpan(span(fullDay, at(17, 14, 20), 15.0), mid = at(17, 1, 0), from = at(17, 0, 0), to = at(17, 2, 0))
    }
}
