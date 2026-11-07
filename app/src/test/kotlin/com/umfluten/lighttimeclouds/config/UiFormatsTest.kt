package com.umfluten.lighttimeclouds.config

import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class UiFormatsTest {

    private val evening = ZonedDateTime.of(2026, 10, 17, 19, 5, 0, 0, ZoneId.of("Europe/Berlin"))

    @Test
    fun `german uses 24 hours, decimal commas and day before month`() {
        val formats = UiFormats(Locale.GERMAN, is24Hour = true)
        assertEquals("19:05", formats.timeOfDay(19 * 60 + 5))
        assertEquals("Sa., 17. Okt.", formats.date(evening))
        assertEquals("52,521°, 13,409°", formats.coordinates(52.521, 13.409))
    }

    @Test
    fun `english with 12 hours uses AM-PM, decimal points and month before day`() {
        val formats = UiFormats(Locale.US, is24Hour = false)
        assertEquals("7:05 PM", formats.timeOfDay(19 * 60 + 5))
        assertEquals("Sat, Oct 17", formats.date(evening))
        assertEquals("52.521°, 13.409°", formats.coordinates(52.521, 13.409))
    }

    @Test
    fun `time is shown in the zone of the given date-time`() {
        val formats = UiFormats(Locale.GERMAN, is24Hour = true)
        assertEquals("19:05 MESZ", formats.time(evening))
        // Zone abbreviations only where the language commonly uses them, otherwise the offset.
        assertEquals("02:05 GMT+9",formats.time(evening.withZoneSameInstant(ZoneId.of("Asia/Tokyo"))))
    }
}
