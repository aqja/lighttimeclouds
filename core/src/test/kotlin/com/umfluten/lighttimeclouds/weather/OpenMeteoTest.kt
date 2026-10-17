package com.umfluten.lighttimeclouds.weather

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OpenMeteoTest {

    private val fetchedAt: Instant = Instant.parse("2026-10-17T10:15:00Z")

    // Shape of a real response (2026-10-17), shortened to four hours, one with a missing value.
    private val body = """
        {"latitude":52.52,"longitude":13.419998,"generationtime_ms":0.33,"utc_offset_seconds":0,
         "timezone":"GMT","timezone_abbreviation":"GMT","elevation":38.0,
         "hourly_units":{"time":"unixtime","cloud_cover":"%","precipitation":"mm","snowfall":"cm",
                         "precipitation_probability":"%","temperature_2m":"°C","apparent_temperature":"°C"},
         "hourly":{"time":[1792116000,1792119600,1792123200,1792126800],
                   "cloud_cover":[100,85,null,12],
                   "precipitation":[0.4,2.2,0.0,0.0],
                   "snowfall":[0.0,0.0,0.0,0.0],
                   "precipitation_probability":[95,98,100,null],
                   "temperature_2m":[11.5,11.0,10.6,10.6],
                   "apparent_temperature":[9.4,8.8,9.0,8.4]}}
    """.trimIndent()

    @Test
    fun `parses hourly columns, drops incomplete hours and keeps the requested coordinates`() {
        val f = OpenMeteo.parse(body, 52.5208, 13.4094, fetchedAt)

        assertEquals(52.5208, f.latitude, 0.0)
        assertEquals(fetchedAt, f.fetchedAt)
        assertEquals(listOf(1792116000L, 1792119600L, 1792126800L), f.hours.map { it.time.epochSecond })
        assertEquals(85.0, f.hours[1].cloudCoverPercent, 1e-9)
        assertEquals(2.2, f.hours[1].precipitationMm, 1e-9)
        assertEquals(98.0, f.hours[1].precipitationProbabilityPercent!!, 1e-9)
        assertEquals(8.8, f.hours[1].apparentTemperatureC!!, 1e-9)
        assertNull(f.hours[2].precipitationProbabilityPercent)
    }

    @Test
    fun `optional columns may be missing entirely, also in caches written before they existed`() {
        val minimal = """{"hourly":{"time":[1792116000],"cloud_cover":[50],"precipitation":[0.0],"snowfall":[0.0]}}"""
        val f = OpenMeteo.parse(minimal, 0.0, 0.0, fetchedAt)
        assertNull(f.hours.single().temperatureC)

        val oldCache = """{"latitude":0.0,"longitude":0.0,"fetched_at":1792116000,
            "hours":[{"time":1792116000,"cloud_cover":50.0,"precipitation":0.0,"snowfall":0.0}]}"""
        assertNull(OpenMeteo.decodeCache(oldCache)!!.hours.single().precipitationProbabilityPercent)
    }

    @Test
    fun `error responses and malformed bodies are rejected`() {
        val error = """{"error":true,"reason":"Latitude must be in range of -90 to 90°. Given: 999.0."}"""
        val e = assertThrows(IllegalArgumentException::class.java) { OpenMeteo.parse(error, 999.0, 0.0, fetchedAt) }
        assertTrue(e.message!!.contains("Latitude"))
        assertThrows(IllegalArgumentException::class.java) { OpenMeteo.parse("<html>", 0.0, 0.0, fetchedAt) }
        assertThrows(IllegalArgumentException::class.java) { OpenMeteo.parse("""{"hourly":{}}""", 0.0, 0.0, fetchedAt) }
    }

    @Test
    fun `cache round trip preserves the forecast`() {
        val f = OpenMeteo.parse(body, 52.5208, 13.4094, fetchedAt)
        assertEquals(f, OpenMeteo.decodeCache(OpenMeteo.encodeCache(f)))
        assertNull(OpenMeteo.decodeCache("{\"latitude\":1"))
        assertNull(OpenMeteo.decodeCache("{}"))
    }

    @Test
    fun `url requests the needed variables with a locale-independent decimal point`() {
        val url = OpenMeteo.forecastUrl(52.5208, -13.4094)
        assertTrue(url, url.contains("latitude=52.5208&longitude=-13.4094"))
        assertTrue(url, url.contains("hourly=cloud_cover,precipitation,snowfall,precipitation_probability,temperature_2m,apparent_temperature"))
        assertTrue(url, url.contains("timeformat=unixtime"))
    }
}
