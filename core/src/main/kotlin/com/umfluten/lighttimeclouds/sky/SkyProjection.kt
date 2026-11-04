package com.umfluten.lighttimeclouds.sky


/**
 * How elevations above the horizon spread across a band from the horizon (0) to a far edge (1)
 * that stands for a top elevation: as in a perspective view like the original's, evenly in
 * tan(elevation), so the low sky gets no more room than in a photo of it. Because tan grows without
 * bound towards the zenith, the top elevation is at most [MAX_TOP_ELEVATION_DEG]; the sky above it
 * hardly changes anyway.
 */
object SkyProjection {
    const val MAX_TOP_ELEVATION_DEG = 80.0

    /** Elevation far below the zenith and the nadir, to keep tan finite. */
    private const val ELEVATION_LIMIT_DEG = 89.0

    /**
     * Position of [elevationDeg] in the band for a far edge at [topElevationDeg]: 0 at the horizon,
     * 1 at the far edge, beyond them for elevations below the horizon or above the top.
     */
    fun fraction(elevationDeg: Double, topElevationDeg: Double): Double {
        val elevation = elevationDeg.coerceIn(-ELEVATION_LIMIT_DEG, ELEVATION_LIMIT_DEG)
        return tan(Math.toRadians(elevation)) / tan(Math.toRadians(clampTop(topElevationDeg)))
    }

    /** The elevation (degrees) at [fraction] of the band; inverse of [fraction]. */
    fun elevation(fraction: Double, topElevationDeg: Double): Double =
        Math.toDegrees(atan(fraction * tan(Math.toRadians(clampTop(topElevationDeg)))))

    private fun clampTop(topElevationDeg: Double) = topElevationDeg.coerceIn(0.1, MAX_TOP_ELEVATION_DEG)
}
