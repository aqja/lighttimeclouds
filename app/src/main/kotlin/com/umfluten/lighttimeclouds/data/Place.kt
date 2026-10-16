package com.umfluten.lighttimeclouds.data

import java.time.ZoneId
import java.util.UUID

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
            latitude = 52.5208,
            longitude = 13.4094,
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
