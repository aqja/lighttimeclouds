package com.umfluten.lighttimeclouds.data

import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.preferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceCoordinatesTest {

    @Test
    fun `rounds to three decimals on both sides of zero`() {
        assertEquals(52.521, roundCoordinate(52.5208), 0.0)
        assertEquals(-13.409, roundCoordinate(-13.4094), 0.0)
        assertEquals(90.0, roundCoordinate(89.9999), 0.0)
    }

    @Test
    fun `a rounded coordinate stays as it is and prints with three decimals at most`() {
        val rounded = roundCoordinate(13.40949999)
        assertEquals(rounded, roundCoordinate(rounded), 0.0)
        assertEquals("13.409", rounded.toString())
    }

    @Test
    fun `the default place has no more decimals than any other`() {
        assertEquals(Place.DEFAULT.latitude, roundCoordinate(Place.DEFAULT.latitude), 0.0)
        assertEquals(Place.DEFAULT.longitude, roundCoordinate(Place.DEFAULT.longitude), 0.0)
    }

    @Test
    fun `a place stored with more decimals is read rounded`() {
        val prefs = preferencesOf(
            stringSetPreferencesKey("place_ids") to setOf("a"),
            stringPreferencesKey("place_a_name") to "Berlin",
            doublePreferencesKey("place_a_lat") to 52.520815,
            doublePreferencesKey("place_a_lon") to 13.409419,
            stringPreferencesKey("place_a_zone") to "Europe/Berlin",
        )
        val place = PlacesRepository.readPlace(prefs, "a")!!
        assertEquals(52.521, place.latitude, 0.0)
        assertEquals(13.409, place.longitude, 0.0)
    }
}
