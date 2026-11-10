package com.umfluten.lighttimeclouds.geo

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLocationTest {

    private fun assertPoint(expectedLat: Double, expectedLon: Double, actual: SharedLocation?, tolerance: Double = 1e-9) {
        assertNotNull(actual)
        assertEquals(expectedLat, actual!!.latitude, tolerance)
        assertEquals(expectedLon, actual.longitude, tolerance)
    }

    @Test
    fun `plain geo URI`() {
        val location = SharedLocation.parse("geo:52.521,13.409")
        assertPoint(52.521, 13.409, location)
        assertNull(location!!.name)
    }

    @Test
    fun `geo URI with altitude, parameters and zoom`() {
        assertPoint(48.857, 2.352, SharedLocation.parse("geo:48.857,2.352,35;crs=wgs84;u=10?z=16"))
    }

    @Test
    fun `negative coordinates on the southern and western hemispheres`() {
        assertPoint(-34.604, -58.382, SharedLocation.parse("geo:-34.604,-58.382"))
    }

    @Test
    fun `Android query with label wins over the zero path`() {
        val location = SharedLocation.parse("geo:0,0?q=51.501,-0.142(Buckingham%20Palace)")
        assertPoint(51.501, -0.142, location)
        assertEquals("Buckingham Palace", location!!.name)
    }

    @Test
    fun `unencoded label with spaces in a whole geo URI`() {
        val location = SharedLocation.parse("geo:0,0?q=52.516,13.378(Brandenburger Tor)")
        assertPoint(52.516, 13.378, location)
        assertEquals("Brandenburger Tor", location!!.name)
    }

    @Test
    fun `query with a label only keeps the path coordinates`() {
        assertPoint(35.681, 139.767, SharedLocation.parse("geo:35.681,139.767?q=Tokyo+Station"))
    }

    @Test
    fun `address search without coordinates gives nothing`() {
        assertNull(SharedLocation.parse("geo:0,0?q=Pariser+Platz+1,+Berlin"))
        assertNull(SharedLocation.parse("geo:0,0"))
    }

    @Test
    fun `out of range coordinates give nothing`() {
        assertNull(SharedLocation.parse("geo:91.0,13.4"))
        assertNull(SharedLocation.parse("geo:52.5,181.0"))
    }

    @Test
    fun `geo URI inside a shared text`() {
        assertPoint(52.521, 13.409, SharedLocation.parse("Treffpunkt:\ngeo:52.521,13.409?z=17\nbis gleich"))
    }

    @Test
    fun `Organic Maps short link with name`() {
        val code = Ge0Encoder.encode(52.5163, 13.3777)
        val location = SharedLocation.parse("Brandenburger Tor\nhttps://omaps.app/$code/Brandenburger_Tor")
        assertPoint(52.5163, 13.3777, location, tolerance = 1e-5)
        assertEquals("Brandenburger Tor", location!!.name)
    }

    @Test
    fun `ge0 reference codes decode to their known points`() {
        // Zoom character first; then latitude and longitude bits interleaved, latitude first.
        assertPoint(0.0, 0.0, Ge0.decode("AwAAAAAAAA"), tolerance = 1e-5)
        assertPoint(45.0, 0.0, Ge0.decode("Byqqqqqqqq"), tolerance = 1e-5)
    }

    @Test
    fun `ge0 name swaps underscores and spaces`() {
        assertEquals("Café de Flore", Ge0.decode("o4B4pYZsRs", "Caf%C3%A9_de_Flore")!!.name)
        assertEquals("a_b", Ge0.decode("o4B4pYZsRs", "a%20b")!!.name)
    }

    @Test
    fun `ge0 round trip across the globe`() {
        listOf(52.521 to 13.409, -33.857 to 151.215, 40.713 to -74.006, -34.604 to -58.382, 64.147 to -21.943).forEach { (lat, lon) ->
            assertPoint(lat, lon, Ge0.decode(Ge0Encoder.encode(lat, lon)), tolerance = 1e-5)
        }
    }

    @Test
    fun `OsmAnd links`() {
        assertPoint(52.51628, 13.37771, SharedLocation.parse("https://osmand.net/map?pin=52.51628,13.37771#17/52.51628/13.37771"))
        assertPoint(52.51628, 13.37771, SharedLocation.parse("https://osmand.net/go?lat=52.51628&lon=13.37771&z=17"))
    }

    @Test
    fun `OpenStreetMap links`() {
        assertPoint(51.5007, -0.1246, SharedLocation.parse("https://www.openstreetmap.org/?mlat=51.5007&mlon=-0.1246#map=17/51.5007/-0.1246"))
        assertPoint(48.8584, 2.2945, SharedLocation.parse("https://www.openstreetmap.org/#map=16/48.8584/2.2945"))
    }

    @Test
    fun `Google Maps links with coordinates`() {
        assertPoint(52.5162746, 13.3755153, SharedLocation.parse("https://www.google.com/maps/place/Brandenburger+Tor/@52.5162746,13.3755153,17z/data=x"))
        assertPoint(41.8902, 12.4922, SharedLocation.parse("https://maps.google.com/?q=41.8902,12.4922"))
    }

    @Test
    fun `Google short link without coordinates gives nothing`() {
        assertNull(SharedLocation.parse("Brandenburger Tor https://maps.app.goo.gl/AbCdEf123"))
    }

    @Test
    fun `bare coordinate pair in text`() {
        assertPoint(55.7539, 37.6208, SharedLocation.parse("Position: 55.7539, 37.6208"))
    }

    @Test
    fun `ordinary numbers in a text are not a location`() {
        assertNull(SharedLocation.parse("Version 1.5, 2.0 und 3"))
        assertNull(SharedLocation.parse("Kostet 12.50, 3.99 Versand"))
    }
}

/** Encoder after the same scheme as [Ge0], independent of its decoding, for round trips. */
private object Ge0Encoder {
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
    private const val MAX_VALUE = (1L shl 30) - 1

    fun encode(lat: Double, lon: Double, zoomChar: Char = 'o', chars: Int = 9): String {
        val latI = ((lat + 90.0) / 180.0 * MAX_VALUE + 0.5).toLong()
        val lonI = ((lon + 180.0) / 360.0 * (MAX_VALUE + 1) + 0.5).toLong()
        val out = StringBuilder().append(zoomChar)
        var shift = 27
        repeat(chars) {
            val a = (latI shr shift and 7).toInt()
            val o = (lonI shr shift and 7).toInt()
            val bits = (a shr 2 and 1) shl 5 or ((o shr 2 and 1) shl 4) or ((a shr 1 and 1) shl 3) or
                ((o shr 1 and 1) shl 2) or ((a and 1) shl 1) or (o and 1)
            out.append(ALPHABET[bits])
            shift -= 3
        }
        return out.toString()
    }
}
