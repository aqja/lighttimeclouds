package com.umfluten.lighttimeclouds.data

import java.time.ZoneId
import java.util.UUID
import kotlin.math.roundToLong

/**
 * A saved location that widgets refer to by [id]. [zone] is the time zone the clock shows for this
 * place, so widgets for different places can act as a world clock.
 */
data class Place(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val zone: ZoneId,
) {
    companion object {
        /** Created when no place exists yet, so every widget always has a valid place. */
        val DEFAULT = Place(
            id = "default",
            name = "Berlin",
            latitude = 52.521,
            longitude = 13.409,
            zone = ZoneId.of("Europe/Berlin"),
        )

        /** A new, unsaved place, by default in the device's time zone. */
        fun new(
            name: String = "",
            latitude: Double = DEFAULT.latitude,
            longitude: Double = DEFAULT.longitude,
            zone: ZoneId = ZoneId.systemDefault(),
        ) = Place(UUID.randomUUID().toString(), name, latitude, longitude, zone)
    }
}

/**
 * [degrees] with three decimals, about 100 m, the most any place keeps: neither the clock nor the
 * forecast models get better from more, it would only say more about where someone is.
 */
fun roundCoordinate(degrees: Double): Double = (degrees * 1000).roundToLong() / 1000.0
