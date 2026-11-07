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
        temperatureLabelSpans(settings, now).single { it.angle == angle }

    private fun assertSpan(span: LabelSpan, mid: ZonedDateTime, from: ZonedDateTime, to: ZonedDateTime) {
        assertEquals("mid", mid.toInstant(), span.mid)
        assertEquals("from", from.toInstant(), span.from)
        assertEquals("to", to.toInstant(), span.to)
    }

    private val trailing = WidgetSettings(widgetId = 1, switchStrategyType = SwitchStrategyType.TRAILING)

    @Test
    fun `trailing window - the seam at the hand cuts the labels on either side of it`() {
        // 14:40: the window runs 14:40 - 02:40, its seam sits at the hour hand, at the "2:40" position.
        val now = at(17, 14, 40)
        // "2:30" position, just behind the hand: the end of the window, 02:30 tomorrow, up to the seam.
        assertSpan(span(trailing, now, 75.0), mid = at(18, 2, 30), from = at(18, 2, 0), to = at(18, 2, 40))
        // "3:30" position, ahead of the hand: 15:30 today, a whole span inside the window.
        assertSpan(span(trailing, now, 105.0), mid = at(17, 15, 30), from = at(17, 15, 0), to = at(17, 16, 0))
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
