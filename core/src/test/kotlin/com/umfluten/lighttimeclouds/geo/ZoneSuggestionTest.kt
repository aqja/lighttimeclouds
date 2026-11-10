package com.umfluten.lighttimeclouds.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.ZoneId

class ZoneSuggestionTest {

    private fun suggest(lat: Double, lon: Double) = ZoneSuggestion.nearest(lat, lon)?.id

    @Test
    fun `capitals get their own zone`() {
        assertEquals("Europe/Berlin", suggest(52.521, 13.409))
        assertEquals("Europe/Paris", suggest(48.857, 2.352))
        assertEquals("Europe/London", suggest(51.507, -0.128))
        assertEquals("Asia/Tokyo", suggest(35.690, 139.692))
        assertEquals("America/Argentina/Buenos_Aires", suggest(-34.604, -58.382))
        assertEquals("Europe/Amsterdam", suggest(52.370, 4.895))
    }

    @Test
    fun `a city away from the principal one still gets its zone`() {
        assertEquals("America/Los_Angeles", suggest(37.775, -122.418))
        assertEquals("Australia/Sydney", suggest(-35.281, 149.130))
    }

    @Test
    fun `only zones the device knows`() {
        assertEquals("Europe/Paris", ZoneSuggestion.nearest(52.521, 13.409, available = setOf("Europe/Paris", "Asia/Tokyo"))?.id)
        assertNull(ZoneSuggestion.nearest(52.521, 13.409, available = emptySet()))
    }

    @Test
    fun `the table is complete and its zones are known to the JVM`() {
        val entries = ZoneSuggestion.entries
        assertTrue(entries.size > 400)
        val known = ZoneId.getAvailableZoneIds()
        val unknown = entries.map { it.zone }.filterNot { it in known }
        assertTrue("Unknown zones: $unknown", unknown.size <= 2)
    }

    @Test
    fun `ISO 6709 with and without seconds`() {
        val (lat, lon) = ZoneSuggestion.parseIso6709("+5230+01322")!!
        assertEquals(52.5, lat, 1e-9)
        assertEquals(13.0 + 22 / 60.0, lon, 1e-9)
        val (lat2, lon2) = ZoneSuggestion.parseIso6709("-334940-0582234")!!
        assertEquals(-(33 + 49 / 60.0 + 40 / 3600.0), lat2, 1e-9)
        assertEquals(-(58 + 22 / 60.0 + 34 / 3600.0), lon2, 1e-9)
        assertNull(ZoneSuggestion.parseIso6709("5230+01322"))
    }
}
